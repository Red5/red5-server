import { B as BlobSource, U as UrlSource, I as Input, A as ALL_FORMATS, f as CanvasSink, g as AudioBufferSink } from "./base-pNSOx5JE.js";
import { S as SampleFileUrl } from "./big-buck-bunny-trimmed-k2jTEXmX.js";
document.querySelector("#sample-file-download").href = SampleFileUrl;
const selectMediaButton = document.querySelector("#select-file");
const loadUrlButton = document.querySelector("#load-url");
const fileNameElement = document.querySelector("#file-name");
const horizontalRule = document.querySelector("hr");
const loadingElement = document.querySelector("#loading-element");
const playerContainer = document.querySelector("#player");
const canvas = document.querySelector("canvas");
const controlsElement = document.querySelector("#controls");
const playButton = document.querySelector("#play-button");
const playIcon = document.querySelector("#play-icon");
const pauseIcon = document.querySelector("#pause-icon");
const currentTimeElement = document.querySelector("#current-time");
const durationElement = document.querySelector("#duration");
const progressBarContainer = document.querySelector("#progress-bar-container");
const progressBar = document.querySelector("#progress-bar");
const volumeBarContainer = document.querySelector("#volume-bar-container");
const volumeBar = document.querySelector("#volume-bar");
const volumeIconWrapper = document.querySelector("#volume-icon-wrapper");
const volumeButton = document.querySelector("#volume-button");
const fullscreenButton = document.querySelector("#fullscreen-button");
const errorElement = document.querySelector("#error-element");
const warningElement = document.querySelector("#warning-element");
const context = canvas.getContext("2d");
let audioContext = null;
let gainNode = null;
let fileLoaded = false;
let videoSink = null;
let audioSink = null;
let totalDuration = 0;
let audioContextStartTime = null;
let playing = false;
let playbackTimeAtStart = 0;
let videoFrameIterator = null;
let audioBufferIterator = null;
let nextFrame = null;
const queuedAudioNodes = /* @__PURE__ */ new Set();
let asyncId = 0;
let draggingProgressBar = false;
let volume = 0.7;
let draggingVolumeBar = false;
let volumeMuted = false;
const initMediaPlayer = async (resource) => {
  try {
    if (playing) {
      pause();
    }
    void (videoFrameIterator == null ? void 0 : videoFrameIterator.return());
    void (audioBufferIterator == null ? void 0 : audioBufferIterator.return());
    asyncId++;
    fileLoaded = false;
    fileNameElement.textContent = resource instanceof File ? resource.name : resource;
    horizontalRule.style.display = "";
    loadingElement.style.display = "";
    playerContainer.style.display = "none";
    errorElement.textContent = "";
    warningElement.textContent = "";
    const source = resource instanceof File ? new BlobSource(resource) : new UrlSource(resource);
    const input = new Input({
      source,
      formats: ALL_FORMATS
    });
    playbackTimeAtStart = 0;
    totalDuration = await input.computeDuration();
    durationElement.textContent = formatSeconds(totalDuration);
    let videoTrack = await input.getPrimaryVideoTrack();
    let audioTrack = await input.getPrimaryAudioTrack();
    let problemMessage = "";
    if (videoTrack) {
      if (videoTrack.codec === null) {
        problemMessage += "Unsupported video codec. ";
        videoTrack = null;
      } else if (!await videoTrack.canDecode()) {
        problemMessage += "Unable to decode the video track. ";
        videoTrack = null;
      }
    }
    if (audioTrack) {
      if (audioTrack.codec === null) {
        problemMessage += "Unsupported audio codec. ";
        audioTrack = null;
      } else if (!await audioTrack.canDecode()) {
        problemMessage += "Unable to decode the audio track. ";
        audioTrack = null;
      }
    }
    if (!videoTrack && !audioTrack) {
      if (!problemMessage) {
        problemMessage = "No audio or video track found.";
      }
      throw new Error(problemMessage);
    }
    if (problemMessage) {
      warningElement.textContent = problemMessage;
    }
    const AudioContext = window.AudioContext || window.webkitAudioContext;
    audioContext = new AudioContext({ sampleRate: audioTrack == null ? void 0 : audioTrack.sampleRate });
    gainNode = audioContext.createGain();
    gainNode.connect(audioContext.destination);
    updateVolume();
    const videoCanBeTransparent = videoTrack ? await videoTrack.canBeTransparent() : false;
    playerContainer.style.background = videoCanBeTransparent ? "transparent" : "";
    videoSink = videoTrack && new CanvasSink(videoTrack, {
      poolSize: 2,
      fit: "contain",
      // In case the video changes dimensions over time
      alpha: videoCanBeTransparent
    });
    audioSink = audioTrack && new AudioBufferSink(audioTrack);
    if (videoTrack) {
      canvas.style.display = "";
      canvas.width = videoTrack.displayWidth;
      canvas.height = videoTrack.displayHeight;
    } else {
      canvas.style.display = "none";
    }
    if (audioTrack) {
      volumeButton.style.display = "";
      volumeBarContainer.style.display = "";
    } else {
      volumeButton.style.display = "none";
      volumeBarContainer.style.display = "none";
    }
    fileLoaded = true;
    await startVideoIterator();
    if (audioContext.state === "running") {
      await play();
    }
    loadingElement.style.display = "none";
    playerContainer.style.display = "";
    if (!videoSink) {
      controlsElement.style.opacity = "1";
      controlsElement.style.pointerEvents = "";
      playerContainer.style.cursor = "";
    }
  } catch (error) {
    console.error(error);
    errorElement.textContent = String(error);
    loadingElement.style.display = "none";
    playerContainer.style.display = "none";
  }
};
const startVideoIterator = async () => {
  if (!videoSink) {
    return;
  }
  asyncId++;
  await (videoFrameIterator == null ? void 0 : videoFrameIterator.return());
  videoFrameIterator = videoSink.canvases(getPlaybackTime());
  const firstFrame = (await videoFrameIterator.next()).value ?? null;
  const secondFrame = (await videoFrameIterator.next()).value ?? null;
  nextFrame = secondFrame;
  if (firstFrame) {
    context.clearRect(0, 0, canvas.width, canvas.height);
    context.drawImage(firstFrame.canvas, 0, 0);
  }
};
const render = (requestFrame = true) => {
  if (fileLoaded) {
    const playbackTime = getPlaybackTime();
    if (playbackTime >= totalDuration) {
      pause();
      playbackTimeAtStart = totalDuration;
    }
    if (nextFrame && nextFrame.timestamp <= playbackTime) {
      context.clearRect(0, 0, canvas.width, canvas.height);
      context.drawImage(nextFrame.canvas, 0, 0);
      nextFrame = null;
      void updateNextFrame();
    }
    if (!draggingProgressBar) {
      updateProgressBarTime(playbackTime);
    }
  }
  if (requestFrame) {
    requestAnimationFrame(() => render());
  }
};
render();
setInterval(() => render(false), 500);
const updateNextFrame = async () => {
  const currentAsyncId = asyncId;
  while (true) {
    const newNextFrame = (await videoFrameIterator.next()).value ?? null;
    if (!newNextFrame) {
      break;
    }
    if (currentAsyncId !== asyncId) {
      break;
    }
    const playbackTime = getPlaybackTime();
    if (newNextFrame.timestamp <= playbackTime) {
      context.clearRect(0, 0, canvas.width, canvas.height);
      context.drawImage(newNextFrame.canvas, 0, 0);
    } else {
      nextFrame = newNextFrame;
      break;
    }
  }
};
const runAudioIterator = async () => {
  if (!audioSink) {
    return;
  }
  for await (const { buffer, timestamp } of audioBufferIterator) {
    const node = audioContext.createBufferSource();
    node.buffer = buffer;
    node.connect(gainNode);
    const startTimestamp = audioContextStartTime + timestamp - playbackTimeAtStart;
    if (startTimestamp >= audioContext.currentTime) {
      node.start(startTimestamp);
    } else {
      node.start(audioContext.currentTime, audioContext.currentTime - startTimestamp);
    }
    queuedAudioNodes.add(node);
    node.onended = () => {
      queuedAudioNodes.delete(node);
    };
    if (timestamp - getPlaybackTime() >= 1) {
      await new Promise((resolve) => {
        const id = setInterval(() => {
          if (timestamp - getPlaybackTime() < 1) {
            clearInterval(id);
            resolve();
          }
        }, 100);
      });
    }
  }
};
const getPlaybackTime = () => {
  if (playing) {
    return audioContext.currentTime - audioContextStartTime + playbackTimeAtStart;
  } else {
    return playbackTimeAtStart;
  }
};
const play = async () => {
  if (audioContext.state === "suspended") {
    await audioContext.resume();
  }
  if (getPlaybackTime() === totalDuration) {
    playbackTimeAtStart = 0;
    await startVideoIterator();
  }
  audioContextStartTime = audioContext.currentTime;
  playing = true;
  if (audioSink) {
    void (audioBufferIterator == null ? void 0 : audioBufferIterator.return());
    audioBufferIterator = audioSink == null ? void 0 : audioSink.buffers(getPlaybackTime());
    void runAudioIterator();
  }
  playIcon.style.display = "none";
  pauseIcon.style.display = "";
};
const pause = () => {
  playbackTimeAtStart = getPlaybackTime();
  playing = false;
  void (audioBufferIterator == null ? void 0 : audioBufferIterator.return());
  audioBufferIterator = null;
  for (const node of queuedAudioNodes) {
    node.stop();
  }
  queuedAudioNodes.clear();
  playIcon.style.display = "";
  pauseIcon.style.display = "none";
};
const togglePlay = () => {
  if (playing) {
    pause();
  } else {
    void play();
  }
};
const seekToTime = async (seconds) => {
  updateProgressBarTime(seconds);
  const wasPlaying = playing;
  if (wasPlaying) {
    pause();
  }
  playbackTimeAtStart = seconds;
  await startVideoIterator();
  if (wasPlaying && playbackTimeAtStart < totalDuration) {
    void play();
  }
};
const updateProgressBarTime = (seconds) => {
  currentTimeElement.textContent = formatSeconds(seconds);
  progressBar.style.width = `${seconds / totalDuration * 100}%`;
};
progressBarContainer.addEventListener("pointerdown", (event) => {
  draggingProgressBar = true;
  progressBarContainer.setPointerCapture(event.pointerId);
  const rect = progressBarContainer.getBoundingClientRect();
  const completion = Math.max(Math.min((event.clientX - rect.left) / rect.width, 1), 0);
  updateProgressBarTime(completion * totalDuration);
  clearTimeout(hideControlsTimeout);
  window.addEventListener("pointerup", (event2) => {
    draggingProgressBar = false;
    progressBarContainer.releasePointerCapture(event2.pointerId);
    const rect2 = progressBarContainer.getBoundingClientRect();
    const completion2 = Math.max(Math.min((event2.clientX - rect2.left) / rect2.width, 1), 0);
    const newTime = completion2 * totalDuration;
    void seekToTime(newTime);
    showControlsTemporarily();
  }, { once: true });
});
progressBarContainer.addEventListener("pointermove", (event) => {
  if (draggingProgressBar) {
    const rect = progressBarContainer.getBoundingClientRect();
    const completion = Math.max(Math.min((event.clientX - rect.left) / rect.width, 1), 0);
    updateProgressBarTime(completion * totalDuration);
  }
});
const updateVolume = () => {
  const actualVolume = volumeMuted ? 0 : volume;
  volumeBar.style.width = `${actualVolume * 100}%`;
  gainNode.gain.value = actualVolume ** 2;
  const iconNumber = volumeMuted ? 0 : Math.ceil(1 + 3 * volume);
  for (let i = 0; i < volumeIconWrapper.children.length; i++) {
    const icon = volumeIconWrapper.children[i];
    icon.style.display = i === iconNumber ? "" : "none";
  }
};
volumeBarContainer.addEventListener("pointerdown", (event) => {
  draggingVolumeBar = true;
  volumeBarContainer.setPointerCapture(event.pointerId);
  const rect = volumeBarContainer.getBoundingClientRect();
  volume = Math.max(Math.min((event.clientX - rect.left) / rect.width, 1), 0);
  volumeMuted = false;
  updateVolume();
  clearTimeout(hideControlsTimeout);
  window.addEventListener("pointerup", (event2) => {
    draggingVolumeBar = false;
    volumeBarContainer.releasePointerCapture(event2.pointerId);
    const rect2 = volumeBarContainer.getBoundingClientRect();
    volume = Math.max(Math.min((event2.clientX - rect2.left) / rect2.width, 1), 0);
    updateVolume();
    showControlsTemporarily();
  }, { once: true });
});
volumeButton.addEventListener("click", () => {
  volumeMuted = !volumeMuted;
  updateVolume();
});
volumeBarContainer.addEventListener("pointermove", (event) => {
  if (draggingVolumeBar) {
    const rect = volumeBarContainer.getBoundingClientRect();
    volume = Math.max(Math.min((event.clientX - rect.left) / rect.width, 1), 0);
    updateVolume();
  }
});
const showControlsTemporarily = () => {
  if (!videoSink) {
    return;
  }
  controlsElement.style.opacity = "1";
  controlsElement.style.pointerEvents = "";
  playerContainer.style.cursor = "";
  clearTimeout(hideControlsTimeout);
  hideControlsTimeout = window.setTimeout(() => {
    if (draggingProgressBar) {
      return;
    }
    hideControls();
    playerContainer.style.cursor = "none";
  }, 2e3);
};
const hideControls = () => {
  controlsElement.style.opacity = "0";
  controlsElement.style.pointerEvents = "none";
};
hideControls();
let hideControlsTimeout = -1;
playerContainer.addEventListener("pointermove", (event) => {
  if (event.pointerType !== "touch") {
    showControlsTemporarily();
  }
});
playerContainer.addEventListener("pointerleave", (event) => {
  if (!videoSink) {
    return;
  }
  if (draggingProgressBar || draggingVolumeBar || event.pointerType === "touch") {
    return;
  }
  hideControls();
  clearTimeout(hideControlsTimeout);
});
playButton.addEventListener("click", togglePlay);
window.addEventListener("keydown", (e) => {
  if (!fileLoaded) {
    return;
  }
  if (e.code === "Space" || e.code === "KeyK") {
    togglePlay();
  } else if (e.code === "KeyF") {
    fullscreenButton.click();
  } else if (e.code === "ArrowLeft") {
    const newTime = Math.max(getPlaybackTime() - 5, 0);
    void seekToTime(newTime);
  } else if (e.code === "ArrowRight") {
    const newTime = Math.min(getPlaybackTime() + 5, totalDuration);
    void seekToTime(newTime);
  } else if (e.code === "KeyM") {
    volumeButton.click();
  } else {
    return;
  }
  showControlsTemporarily();
  e.preventDefault();
});
fullscreenButton.addEventListener("click", () => {
  if (document.fullscreenElement) {
    void document.exitFullscreen();
  } else {
    playerContainer.requestFullscreen().catch((e) => {
      console.error("Failed to enter fullscreen mode:", e);
    });
  }
});
const isTouchDevice = () => {
  return "ontouchstart" in window;
};
playerContainer.addEventListener("click", () => {
  if (isTouchDevice()) {
    if (controlsElement.style.opacity === "1") {
      hideControls();
    } else {
      showControlsTemporarily();
    }
  } else {
    togglePlay();
  }
});
controlsElement.addEventListener("click", (event) => {
  event.stopPropagation();
  showControlsTemporarily();
});
const formatSeconds = (seconds) => {
  const showMilliseconds = window.innerWidth >= 640;
  seconds = Math.round(seconds * 1e3) / 1e3;
  const hours = Math.floor(seconds / 3600);
  const minutes = Math.floor(seconds % 3600 / 60);
  const remainingSeconds = Math.floor(seconds % 60);
  const millisecs = Math.floor(1e3 * seconds % 1e3).toString().padStart(3, "0");
  let result;
  if (hours > 0) {
    result = `${hours}:${minutes.toString().padStart(2, "0")}:${remainingSeconds.toString().padStart(2, "0")}`;
  } else {
    result = `${minutes.toString().padStart(2, "0")}:${remainingSeconds.toString().padStart(2, "0")}`;
  }
  if (showMilliseconds) {
    result += `.${millisecs}`;
  }
  return result;
};
window.addEventListener("resize", () => {
  if (totalDuration) {
    updateProgressBarTime(getPlaybackTime());
    durationElement.textContent = formatSeconds(totalDuration);
  }
});
selectMediaButton.addEventListener("click", () => {
  const fileInput = document.createElement("input");
  fileInput.type = "file";
  fileInput.accept = "video/*,video/x-matroska,video/mp2t,.ts,audio/*,audio/aac";
  fileInput.addEventListener("change", () => {
    var _a;
    const file = (_a = fileInput.files) == null ? void 0 : _a[0];
    if (!file) {
      return;
    }
    void initMediaPlayer(file);
  });
  fileInput.click();
});
loadUrlButton.addEventListener("click", () => {
  const url = prompt(
    "Please enter a URL of a media file. Note that it must be HTTPS and support cross-origin requests, so have the right CORS headers set.",
    "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"
  );
  if (!url) {
    return;
  }
  void initMediaPlayer(url);
});
document.addEventListener("dragover", (event) => {
  event.preventDefault();
  event.dataTransfer.dropEffect = "copy";
});
document.addEventListener("drop", (event) => {
  var _a;
  event.preventDefault();
  const files = (_a = event.dataTransfer) == null ? void 0 : _a.files;
  const file = files && files.length > 0 ? files[0] : void 0;
  if (file) {
    void initMediaPlayer(file);
  }
});
