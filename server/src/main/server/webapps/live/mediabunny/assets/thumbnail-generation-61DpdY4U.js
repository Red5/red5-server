import { B as BlobSource, U as UrlSource, I as Input, A as ALL_FORMATS, f as CanvasSink } from "./base-C1Isxu1w.js";
import { S as SampleFileUrl } from "./big-buck-bunny-trimmed-DgXJ7ivd.js";
document.querySelector("#sample-file-download").href = SampleFileUrl;
const selectMediaButton = document.querySelector("#select-file");
const loadUrlButton = document.querySelector("#load-url");
const fileNameElement = document.querySelector("#file-name");
const horizontalRule = document.querySelector("hr");
const thumbnailContainer = document.querySelector("#thumbnail-container");
const errorElement = document.querySelector("#error-element");
const THUMBNAIL_COUNT = 16;
const THUMBNAIL_SIZE = 200;
const generateThumbnails = async (resource) => {
  fileNameElement.textContent = resource instanceof File ? resource.name : resource;
  horizontalRule.style.display = "";
  errorElement.textContent = "";
  thumbnailContainer.innerHTML = "";
  try {
    const source = resource instanceof File ? new BlobSource(resource) : new UrlSource(resource);
    const input = new Input({
      source,
      formats: ALL_FORMATS
      // Accept all formats
    });
    const videoTrack = await input.getPrimaryVideoTrack();
    if (!videoTrack) {
      throw new Error("File has no video track.");
    }
    if (videoTrack.codec === null) {
      throw new Error("Unsupported video codec.");
    }
    if (!await videoTrack.canDecode()) {
      throw new Error("Unable to decode the video track.");
    }
    const width = videoTrack.displayWidth > videoTrack.displayHeight ? THUMBNAIL_SIZE : Math.floor(THUMBNAIL_SIZE * videoTrack.displayWidth / videoTrack.displayHeight);
    const height = videoTrack.displayHeight > videoTrack.displayWidth ? THUMBNAIL_SIZE : Math.floor(THUMBNAIL_SIZE * videoTrack.displayHeight / videoTrack.displayWidth);
    const thumbnailElements = [];
    for (let i2 = 0; i2 < THUMBNAIL_COUNT; i2++) {
      const thumbnailElement = document.createElement("div");
      thumbnailElement.className = "rounded-lg overflow-hidden bg-zinc-100 dark:bg-zinc-800 relative";
      thumbnailElement.style.width = `${width}px`;
      thumbnailElement.style.height = `${height}px`;
      thumbnailElements.push(thumbnailElement);
      thumbnailContainer.append(thumbnailElement);
    }
    const firstTimestamp = await videoTrack.getFirstTimestamp();
    const lastTimestamp = await videoTrack.computeDuration();
    const timestamps = Array.from(
      { length: THUMBNAIL_COUNT },
      (_, i2) => firstTimestamp + i2 * (lastTimestamp - firstTimestamp) / THUMBNAIL_COUNT
    );
    const sink = new CanvasSink(videoTrack, {
      width: Math.floor(width * window.devicePixelRatio),
      height: Math.floor(height * window.devicePixelRatio),
      fit: "fill"
    });
    let i = 0;
    for await (const wrappedCanvas of sink.canvasesAtTimestamps(timestamps)) {
      const container = thumbnailElements[i];
      if (wrappedCanvas) {
        const canvasElement = wrappedCanvas.canvas;
        canvasElement.className = "size-full";
        container.append(canvasElement);
        canvasElement.animate(
          [{ transform: "scale(1.2)" }, { transform: "scale(1)" }],
          { duration: 333, easing: "cubic-bezier(0.22, 1, 0.36, 1)" }
        );
        const timestampElement = document.createElement("p");
        timestampElement.textContent = wrappedCanvas.timestamp.toFixed(2) + " s";
        timestampElement.className = "absolute bottom-0 right-0 bg-black/30 text-white px-1 py-0.5 text-[11px] rounded-tl-lg";
        container.append(timestampElement);
      } else {
        const p = document.createElement("p");
        p.textContent = "?";
        p.className = "absolute inset-0 flex items-center justify-center text-3xl opacity-50";
        container.append(p);
      }
      i++;
    }
  } catch (error) {
    console.error(error);
    errorElement.textContent = String(error);
    thumbnailContainer.innerHTML = "";
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
    void generateThumbnails(file);
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
  void generateThumbnails(url);
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
    void generateThumbnails(file);
  }
});
