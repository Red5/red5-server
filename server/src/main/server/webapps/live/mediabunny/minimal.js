import {
  Input,
  ALL_FORMATS,
  ReadableStreamSource,
  CanvasSink,
  AudioBufferSink
} from "./mediabunny.mjs";

const streamInput = document.getElementById("stream");
const startButton = document.getElementById("start");
const stopButton = document.getElementById("stop");
const statusEl = document.getElementById("status");
const errorEl = document.getElementById("error");
const canvas = document.getElementById("canvas");
const ctx = canvas.getContext("2d");

let abortController = null;
let input = null;
let running = false;
let videoIterator = null;
let audioIterator = null;
let audioContext = null;
let gainNode = null;
const audioNodes = new Set();
let videoEnded = false;
let audioEnded = false;
let audioClockStart = null;
let nextAudioTime = null;

const setStatus = (text) => {
  statusEl.textContent = text;
};

const setError = (text) => {
  errorEl.textContent = text;
};

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

const stopPlayback = async () => {
  running = false;
  stopButton.disabled = true;
  startButton.disabled = false;
  setStatus("Stopping...");
  try {
    abortController?.abort();
    abortController = null;
    await videoIterator?.return();
    await audioIterator?.return();
    videoIterator = null;
    audioIterator = null;
    input?.dispose();
    input = null;
    for (const node of audioNodes) {
      try {
        node.stop();
      } catch {
        // ignored
      }
    }
    audioNodes.clear();
    if (audioContext) {
      await audioContext.close();
      audioContext = null;
      gainNode = null;
    }
  } finally {
    setStatus("Idle");
  }
};

const runVideo = async (videoTrack, startEpochSeconds) => {
  try {
    const sink = new CanvasSink(videoTrack, { fit: "contain" });
    videoIterator = sink.canvases(0, Infinity);
    let baseTimestamp = null;
    for await (const frame of videoIterator) {
      if (!running) break;
      if (!frame) continue;
      if (baseTimestamp === null) {
        baseTimestamp = frame.timestamp;
        console.log("First video frame timestamp:", baseTimestamp);
        canvas.width = frame.canvas.width;
        canvas.height = frame.canvas.height;
      }
      const targetTime = (audioClockStart ?? startEpochSeconds) + (frame.timestamp - baseTimestamp);
      const now = audioContext ? audioContext.currentTime : performance.now() / 1000;
      if (targetTime > now) {
        await sleep((targetTime - now) * 1000);
      }
      if (!running) break;
      ctx.clearRect(0, 0, canvas.width, canvas.height);
      ctx.drawImage(frame.canvas, 0, 0);
    }
    console.warn("Video iterator ended.");
    videoEnded = true;
    if (audioEnded) {
      running = false;
    }
  } catch (error) {
    console.error("Video iterator error:", error);
    setError(String(error));
    running = false;
  }
};

const runAudio = async (audioTrack, startEpochSeconds) => {
  try {
    const sink = new AudioBufferSink(audioTrack);
    audioIterator = sink.buffers(0, Infinity);
    for await (const data of audioIterator) {
      if (!running) break;
      if (!data) continue;
      if (audioClockStart === null) {
        audioClockStart = (audioContext?.currentTime ?? 0) + 0.1;
        nextAudioTime = audioClockStart;
        console.log("First audio buffer timestamp:", data.timestamp);
      }

      const source = audioContext.createBufferSource();
      source.buffer = data.buffer;
      source.connect(gainNode);
      const duration = data.duration ?? (data.buffer.length / audioContext.sampleRate);
      const startTime = Math.max(nextAudioTime, audioContext.currentTime);
      source.start(startTime);
      source.onended = () => audioNodes.delete(source);
      audioNodes.add(source);
      nextAudioTime = startTime + duration;
    }
    console.warn("Audio iterator ended.");
    audioEnded = true;
    if (videoEnded) {
      running = false;
    }
  } catch (error) {
    console.error("Audio iterator error:", error);
    setError(String(error));
    running = false;
  }
};

const startPlayback = async () => {
  if (running) return;
  setError("");

  const streamName = streamInput.value.trim();
  if (!streamName) {
    setError("Stream name is required.");
    return;
  }

  startButton.disabled = true;
  stopButton.disabled = false;
  running = true;
  setStatus("Connecting...");

  abortController = new AbortController();
  const url = `/live/mb?stream=${encodeURIComponent(streamName)}`;

  try {
    const response = await fetch(url, { signal: abortController.signal });
    if (!response.ok) {
      throw new Error(`HTTP ${response.status} ${response.statusText}`);
    }
    if (!response.body) {
      throw new Error("Response has no body stream.");
    }

    let totalBytes = 0;
    let chunkCount = 0;
    const tapStream = new TransformStream({
      transform(chunk, controller) {
        const byteLength = chunk.byteLength ?? chunk.length ?? 0;
        totalBytes += byteLength;
        if (chunkCount < 3 && byteLength > 0) {
          const view = new Uint8Array(chunk);
          const prefix = Array.from(view.slice(0, 16))
            .map((b) => b.toString(16).padStart(2, "0"))
            .join("");
          console.log(`Stream chunk ${chunkCount} bytes=${view.length} prefix=${prefix}`);
        }
        chunkCount++;
        controller.enqueue(chunk);
      },
      flush() {
        console.warn(`ReadableStream closed. totalBytes=${totalBytes} chunks=${chunkCount}`);
      }
    });
    const source = new ReadableStreamSource(response.body.pipeThrough(tapStream));
    input = new Input({ source, formats: ALL_FORMATS });

    try {
      const format = await input.getFormat();
      console.log("Mediabunny format:", format);
    } catch (error) {
      console.warn("Mediabunny getFormat() failed:", error);
    }
    try {
      const mime = await input.getMimeType();
      console.log("Mediabunny mime type:", mime);
    } catch (error) {
      console.warn("Mediabunny getMimeType() failed:", error);
    }

    const videoTrack = await input.getPrimaryVideoTrack();
    const audioTrack = await input.getPrimaryAudioTrack();

    if (!videoTrack && !audioTrack) {
      throw new Error("No audio or video track found in stream.");
    }

    const startEpochSeconds = performance.now() / 1000;
    setStatus("Playing");

    const tasks = [];
    if (audioTrack) {
      const AudioContextCtor = window.AudioContext || window.webkitAudioContext;
      audioContext = new AudioContextCtor({ sampleRate: audioTrack.sampleRate });
      gainNode = audioContext.createGain();
      gainNode.gain.value = 1;
      gainNode.connect(audioContext.destination);
      if (audioContext.state === "suspended") {
        await audioContext.resume();
      }
      tasks.push(runAudio(audioTrack, startEpochSeconds));
    }
    if (videoTrack) tasks.push(runVideo(videoTrack, startEpochSeconds));
    await Promise.allSettled(tasks);
  } catch (error) {
    if (running) {
      console.error("Playback error:", error);
      setError(String(error));
    }
  } finally {
    if (running) {
      await stopPlayback();
    }
  }
};

startButton.addEventListener("click", () => void startPlayback());
stopButton.addEventListener("click", () => void stopPlayback());
