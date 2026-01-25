import { c as canEncodeAudio, b as QUALITY_MEDIUM, O as Output, S as StreamTarget, M as Mp4OutputFormat, d as CanvasSource, e as MediaStreamAudioTrackSource } from "./base-C1Isxu1w.js";
const toggleRecordingButton = document.querySelector("#toggle-button");
const horizontalRule = document.querySelector("hr");
const mainContainer = document.querySelector("#main-container");
const videoElement = document.querySelector("video");
const downloadButton = document.querySelector("#download-button");
const errorElement = document.querySelector("#error-element");
const warningElement = document.querySelector("#warning-element");
const canvas = document.querySelector("canvas");
const context = canvas.getContext("2d", { alpha: false, desynchronized: true });
const frameRate = 30;
const chunks = [];
let recording = false;
let output;
let videoSource;
let videoCaptureInterval;
let mediaStream;
let startTime;
let readyForMoreFrames = true;
let lastFrameNumber = -1;
const startRecording = async () => {
  try {
    recording = true;
    toggleRecordingButton.textContent = "Starting...";
    toggleRecordingButton.disabled = true;
    mainContainer.style.display = "none";
    videoElement.src = "";
    downloadButton.style.display = "none";
    errorElement.textContent = "";
    warningElement.textContent = "";
    context.fillStyle = "white";
    context.fillRect(0, 0, canvas.width, canvas.height);
    const audioIsEncodable = await canEncodeAudio("opus", {
      bitrate: QUALITY_MEDIUM
    });
    let audioTrack = null;
    if (audioIsEncodable) {
      mediaStream = await navigator.mediaDevices.getUserMedia({ audio: true });
      audioTrack = mediaStream.getAudioTracks()[0] ?? null;
    } else {
      warningElement.textContent = "Audio is not yet encodable by your browser, so the audio track has been omitted.";
    }
    horizontalRule.style.display = "";
    mainContainer.style.display = "";
    output = new Output({
      // We're using fragmented MP4 here; streamable WebM would also work
      format: new Mp4OutputFormat({ fastStart: "fragmented" }),
      // We use StreamTarget to pipe the chunks to the SourceBuffer as soon as they are created
      target: new StreamTarget(new WritableStream({
        write(chunk) {
          chunks.push(chunk.data);
          if (sourceBuffer) {
            appendToSourceBuffer(chunk.data);
          }
        }
      }))
    });
    const mediaSource = new MediaSource();
    let sourceBuffer = null;
    videoElement.src = URL.createObjectURL(mediaSource);
    void videoElement.play();
    await new Promise((resolve) => mediaSource.onsourceopen = resolve);
    let appendPromise = Promise.resolve();
    const appendToSourceBuffer = (source) => {
      appendPromise = appendPromise.then(() => {
        sourceBuffer.appendBuffer(source);
        return new Promise((resolve) => sourceBuffer.onupdateend = () => resolve());
      });
    };
    videoSource = new CanvasSource(canvas, {
      codec: "avc",
      bitrate: QUALITY_MEDIUM,
      keyFrameInterval: 0.5,
      latencyMode: "realtime"
      // Allow the encoder to skip frames to keep up with real-time constraints
    });
    output.addVideoTrack(videoSource, { frameRate });
    if (audioTrack) {
      const audioSource = new MediaStreamAudioTrackSource(audioTrack, {
        codec: "opus",
        bitrate: QUALITY_MEDIUM
      });
      audioSource.errorPromise.catch(cancelRecording);
      output.addAudioTrack(audioSource);
    }
    await output.start();
    startTime = Number(document.timeline.currentTime);
    readyForMoreFrames = true;
    lastFrameNumber = -1;
    void addVideoFrame().catch(cancelRecording);
    videoCaptureInterval = window.setInterval(() => void addVideoFrame().catch(cancelRecording), 1e3 / frameRate);
    const mimeType = await output.getMimeType();
    sourceBuffer = mediaSource.addSourceBuffer(mimeType);
    chunks.forEach((chunk) => appendToSourceBuffer(chunk));
    toggleRecordingButton.textContent = "Stop recording";
    toggleRecordingButton.disabled = false;
  } catch (error) {
    await cancelRecording(error);
  }
};
const cancelRecording = async (error) => {
  if (!recording) {
    return;
  }
  console.error(error);
  errorElement.textContent = String(error);
  clearInterval(videoCaptureInterval);
  mainContainer.style.display = "none";
  toggleRecordingButton.textContent = "Start recording";
  toggleRecordingButton.disabled = false;
  recording = false;
  await (output == null ? void 0 : output.cancel());
  mediaStream == null ? void 0 : mediaStream.getTracks().forEach((track) => track.stop());
};
const stopRecording = async () => {
  toggleRecordingButton.textContent = "Stopping...";
  toggleRecordingButton.disabled = true;
  clearInterval(videoCaptureInterval);
  mediaStream == null ? void 0 : mediaStream.getTracks().forEach((track) => track.stop());
  await output.finalize();
  const blob = new Blob(chunks, { type: output.format.mimeType });
  const url = URL.createObjectURL(blob);
  downloadButton.style.display = "";
  downloadButton.href = url;
  downloadButton.download = "michelangelo" + output.format.fileExtension;
  toggleRecordingButton.textContent = "Start recording";
  toggleRecordingButton.disabled = false;
  recording = false;
};
toggleRecordingButton.addEventListener("click", () => {
  if (!recording) {
    void startRecording();
  } else {
    void stopRecording();
  }
});
const addVideoFrame = async () => {
  if (!readyForMoreFrames) {
    return;
  }
  const elapsedSeconds = (Number(document.timeline.currentTime) - startTime) / 1e3;
  const frameNumber = Math.round(elapsedSeconds * frameRate);
  if (frameNumber === lastFrameNumber) {
    return;
  }
  lastFrameNumber = frameNumber;
  const timestamp = frameNumber / frameRate;
  readyForMoreFrames = false;
  await videoSource.add(timestamp, 1 / frameRate);
  readyForMoreFrames = true;
};
let drawing = false;
let lastPos = new DOMPoint(0, 0);
const getRelativeMousePos = (event) => {
  const rect = canvas.getBoundingClientRect();
  return new DOMPoint(
    event.clientX - rect.x,
    event.clientY - rect.y
  );
};
const drawLine = (from, to) => {
  context.beginPath();
  context.moveTo(from.x, from.y);
  context.lineTo(to.x, to.y);
  context.strokeStyle = "#27272a";
  context.lineWidth = 5;
  context.lineCap = "round";
  context.stroke();
};
canvas.addEventListener("pointerdown", (event) => {
  if (event.button !== 0) return;
  drawing = true;
  lastPos = getRelativeMousePos(event);
  drawLine(lastPos, lastPos);
});
window.addEventListener("pointerup", () => {
  drawing = false;
});
window.addEventListener("pointermove", (event) => {
  if (!drawing) return;
  const newPos = getRelativeMousePos(event);
  drawLine(lastPos, newPos);
  lastPos = newPos;
});
