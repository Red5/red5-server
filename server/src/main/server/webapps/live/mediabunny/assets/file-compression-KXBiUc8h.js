import { B as BlobSource, U as UrlSource, I as Input, A as ALL_FORMATS, O as Output, M as Mp4OutputFormat, a as BufferTarget, C as Conversion, Q as QUALITY_VERY_LOW } from "./base-pNSOx5JE.js";
import { S as SampleFileUrl } from "./big-buck-bunny-trimmed-k2jTEXmX.js";
document.querySelector("#sample-file-download").href = SampleFileUrl;
const selectMediaButton = document.querySelector("#select-file");
const loadUrlButton = document.querySelector("#load-url");
const fileNameElement = document.querySelector("#file-name");
const horizontalRule = document.querySelector("hr");
const progressBarContainer = document.querySelector("#progress-bar-container");
const progressBar = document.querySelector("#progress-bar");
const speedometer = document.querySelector("#speedometer");
const videoElement = document.querySelector("video");
const compressionFacts = document.querySelector("#compression-facts");
const errorElement = document.querySelector("#error-element");
let currentConversion = null;
let currentIntervalId = -1;
const compressFile = async (resource) => {
  clearInterval(currentIntervalId);
  await (currentConversion == null ? void 0 : currentConversion.cancel());
  fileNameElement.textContent = resource instanceof File ? resource.name : resource;
  horizontalRule.style.display = "";
  progressBarContainer.style.display = "";
  speedometer.style.display = "";
  speedometer.textContent = "Speed: -";
  videoElement.style.display = "none";
  videoElement.src = "";
  errorElement.textContent = "";
  try {
    const source = resource instanceof File ? new BlobSource(resource) : new UrlSource(resource);
    const input = new Input({
      source,
      formats: ALL_FORMATS
      // Accept all formats
    });
    const fileSize = await source.getSize();
    const output = new Output({
      target: new BufferTarget(),
      format: new Mp4OutputFormat()
    });
    currentConversion = await Conversion.init({
      input,
      output,
      video: (track) => ({
        width: 320,
        // Height will be deduced automatically to retain aspect ratio
        bitrate: QUALITY_VERY_LOW,
        discard: track.number > 1
        // Keep only the first video track
      }),
      audio: (track) => ({
        bitrate: 32e3,
        discard: track.number > 1
        // Keep only the first audio track
      })
    });
    let progress = 0;
    currentConversion.onProgress = (newProgress) => progress = newProgress;
    const fileDuration = await input.computeDuration() - await input.getFirstTimestamp();
    const startTime = performance.now();
    const updateProgress = () => {
      progressBar.style.width = `${progress * 100}%`;
      const now = performance.now();
      const elapsedSeconds = (now - startTime) / 1e3;
      const factor = fileDuration / (elapsedSeconds / progress);
      speedometer.textContent = `Speed: ~${factor.toPrecision(3)}x real time`;
    };
    currentIntervalId = window.setInterval(updateProgress, 1e3 / 60);
    await currentConversion.execute();
    clearInterval(currentIntervalId);
    updateProgress();
    videoElement.style.display = "";
    videoElement.src = URL.createObjectURL(new Blob([output.target.buffer], { type: output.format.mimeType }));
    void videoElement.play();
    compressionFacts.style.display = "";
    compressionFacts.textContent = `${(output.target.buffer.byteLength / fileSize * 100).toPrecision(3)}% of original size`;
  } catch (error) {
    console.error(error);
    await (currentConversion == null ? void 0 : currentConversion.cancel());
    errorElement.textContent = String(error);
    clearInterval(currentIntervalId);
    progressBarContainer.style.display = "none";
    speedometer.style.display = "none";
    compressionFacts.style.display = "none";
    videoElement.style.display = "none";
  }
};
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
    void compressFile(file);
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
  void compressFile(url);
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
    void compressFile(file);
  }
});
