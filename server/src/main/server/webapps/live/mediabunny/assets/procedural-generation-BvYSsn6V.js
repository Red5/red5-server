import { O as Output, M as Mp4OutputFormat, a as BufferTarget, h as getFirstEncodableVideoCodec, d as CanvasSource, i as QUALITY_HIGH, j as getFirstEncodableAudioCodec, k as AudioBufferSource } from "./base-pNSOx5JE.js";
const durationSlider = document.querySelector("#duration-slider");
const durationValue = document.querySelector("#duration-value");
const ballsSlider = document.querySelector("#balls-slider");
const ballsValue = document.querySelector("#balls-value");
const renderButton = document.querySelector("#render-button");
const horizontalRule = document.querySelector("hr");
const progressBarContainer = document.querySelector("#progress-bar-container");
const progressBar = document.querySelector("#progress-bar");
const progressText = document.querySelector("#progress-text");
const resultVideo = document.querySelector("#result-video");
const videoInfo = document.querySelector("#video-info");
const errorElement = document.querySelector("#error-element");
const renderCanvas = new OffscreenCanvas(1280, 720);
const renderCtx = renderCanvas.getContext("2d", { alpha: false });
let audioContext;
let globalGainNode;
let dryGain;
let wetGain;
let reverbConvolver;
const scaleProgression = [
  [-24, -12, -10, -8, -7, -5, -3, -1, 0, 2, 4, 5, 7, 9, 11, 12],
  [-22, -12, -10, -8, -7, -5, -3, -1, 0, 2, 4, 5, 7, 9, 11, 14],
  [-29, -17, -15, -13, -12, -10, -8, -5, -3, -1, 0, 2, 4, 5, 7, 16],
  [-26, -14, -12, -10, -9, -7, -5, -2, 0, 2, 3, 5, 7, 9, 10, 12]
];
const scaleHues = [
  215,
  151,
  273,
  335
];
const wallWidth = 10;
const frameRate = 60;
const numberOfChannels = 2;
const sampleRate = 48e3;
let balls = [];
let currentScaleIndex = 0;
let collisionCount = 0;
let collisionsPerScale = 0;
let output;
const generateVideo = async () => {
  let progressInterval = -1;
  try {
    renderButton.disabled = true;
    renderButton.textContent = "Generating...";
    horizontalRule.style.display = "";
    progressBarContainer.style.display = "";
    progressText.style.display = "";
    progressText.textContent = "Initializing...";
    resultVideo.style.display = "none";
    resultVideo.src = "";
    videoInfo.style.display = "none";
    errorElement.textContent = "";
    const duration = Number(durationSlider.value);
    const totalFrames = duration * frameRate;
    initScene(duration);
    output = new Output({
      target: new BufferTarget(),
      // Stored in memory
      format: new Mp4OutputFormat()
    });
    const videoCodec = await getFirstEncodableVideoCodec(output.format.getSupportedVideoCodecs(), {
      width: renderCanvas.width,
      height: renderCanvas.height
    });
    if (!videoCodec) {
      throw new Error("Your browser doesn't support video encoding.");
    }
    const canvasSource = new CanvasSource(renderCanvas, {
      codec: videoCodec,
      bitrate: QUALITY_HIGH
    });
    output.addVideoTrack(canvasSource, { frameRate });
    let audioBufferSource = null;
    const audioCodec = await getFirstEncodableAudioCodec(output.format.getSupportedAudioCodecs(), {
      numberOfChannels,
      sampleRate
    });
    if (audioCodec) {
      audioBufferSource = new AudioBufferSource({
        codec: audioCodec,
        bitrate: QUALITY_HIGH
      });
      output.addAudioTrack(audioBufferSource);
    } else {
      alert("Your browser doesn't support audio encoding, so we won't include audio in the output file.");
    }
    await output.start();
    let currentFrame = 0;
    progressInterval = window.setInterval(() => {
      const videoProgress = currentFrame / totalFrames;
      const overallProgress = videoProgress * (audioBufferSource ? 0.9 : 0.95);
      progressBar.style.width = `${overallProgress * 100}%`;
      if (currentFrame === totalFrames && audioBufferSource) {
        progressText.textContent = "Rendering audio...";
      } else {
        progressText.textContent = `Rendering frame ${currentFrame}/${totalFrames}`;
      }
    }, 1e3 / 60);
    for (currentFrame; currentFrame < totalFrames; currentFrame++) {
      const currentTime = currentFrame / frameRate;
      updateScene(currentTime);
      await canvasSource.add(currentTime, 1 / frameRate);
    }
    canvasSource.close();
    if (audioBufferSource) {
      const audioBuffer = await audioContext.startRendering();
      await audioBufferSource.add(audioBuffer);
      audioBufferSource.close();
    }
    clearInterval(progressInterval);
    progressText.textContent = "Finalizing file...";
    progressBar.style.width = "95%";
    await output.finalize();
    progressBar.style.width = "100%";
    progressBarContainer.style.display = "none";
    progressText.style.display = "none";
    resultVideo.style.display = "";
    videoInfo.style.display = "";
    const videoBlob = new Blob([output.target.buffer], { type: output.format.mimeType });
    resultVideo.src = URL.createObjectURL(videoBlob);
    void resultVideo.play();
    const fileSizeMiB = (videoBlob.size / (1024 * 1024)).toPrecision(3);
    videoInfo.textContent = `File size: ${fileSizeMiB} MiB`;
  } catch (error) {
    console.error(error);
    await (output == null ? void 0 : output.cancel());
    clearInterval(progressInterval);
    errorElement.textContent = String(error);
    progressBarContainer.style.display = "none";
    progressText.style.display = "none";
  } finally {
    renderButton.disabled = false;
    renderButton.textContent = "Generate video";
  }
};
const initScene = (duration) => {
  audioContext = new OfflineAudioContext(numberOfChannels, duration * sampleRate, sampleRate);
  reverbConvolver = audioContext.createConvolver();
  reverbConvolver.buffer = createReverbImpulse(5);
  globalGainNode = audioContext.createGain();
  dryGain = audioContext.createGain();
  wetGain = audioContext.createGain();
  globalGainNode.connect(dryGain);
  globalGainNode.connect(reverbConvolver);
  reverbConvolver.connect(wetGain);
  dryGain.connect(audioContext.destination);
  wetGain.connect(audioContext.destination);
  globalGainNode.gain.setValueAtTime(0.8, 0);
  dryGain.gain.setValueAtTime(0.5, 0);
  wetGain.gain.setValueAtTime(0.5, 0);
  const numBalls = Number(ballsSlider.value);
  balls = [];
  collisionsPerScale = Math.max(10, Math.ceil(numBalls ** 1.5));
  collisionCount = 0;
  currentScaleIndex = 0;
  for (let i = 0; i < numBalls; i++) {
    const scaleIndex = Math.floor(Math.random() * scaleProgression[0].length);
    balls.push(new Ball(0, 0, scaleIndex));
  }
  balls.sort((a, b) => b.radius - a.radius);
  for (let i = 0; i < balls.length; i++) {
    const ball = balls[i];
    for (let attempts = 0; attempts < 100; attempts++) {
      ball.x = ball.radius + Math.random() * (renderCanvas.width - 2 * ball.radius);
      ball.y = ball.radius + Math.random() * (renderCanvas.height - 2 * ball.radius);
      const overlapsOtherBall = balls.some((otherBall, index) => {
        if (index >= i) {
          return false;
        }
        const dx = ball.x - otherBall.x;
        const dy = ball.y - otherBall.y;
        const distanceSquared = dx * dx + dy * dy;
        return distanceSquared < (ball.radius + otherBall.radius + 5) ** 2;
      });
      if (!overlapsOtherBall) {
        break;
      }
    }
  }
};
const updateScene = (currentTime) => {
  renderCtx.clearRect(0, 0, renderCanvas.width, renderCanvas.height);
  renderCtx.beginPath();
  renderCtx.rect(0, 0, wallWidth, renderCanvas.height);
  renderCtx.rect(renderCanvas.width - wallWidth, 0, wallWidth, renderCanvas.height);
  renderCtx.rect(0, 0, renderCanvas.width, wallWidth);
  renderCtx.rect(0, renderCanvas.height - wallWidth, renderCanvas.width, wallWidth);
  renderCtx.fillStyle = "#27272a";
  renderCtx.fill();
  for (const ball of balls) {
    ball.update(currentTime, renderCanvas.width, renderCanvas.height);
  }
  for (let i = 0; i < balls.length - 1; i++) {
    for (let j = i + 1; j < balls.length; j++) {
      const ballI = balls[i];
      const ballJ = balls[j];
      if (ballI.checkCollision(ballJ)) {
        ballI.collideWith(ballJ);
        ballI.lastHitTime = currentTime;
        ballJ.lastHitTime = currentTime;
        ballI.scheduleSound(currentTime);
        ballJ.scheduleSound(currentTime);
        collisionCount++;
      }
    }
  }
  if (collisionCount >= collisionsPerScale) {
    currentScaleIndex = (currentScaleIndex + 1) % scaleProgression.length;
    collisionCount = 0;
  }
  for (const ball of balls) {
    ball.draw(renderCtx, currentTime);
  }
};
const ballHitAnimationDuration = 0.2;
class Ball {
  constructor(x, y, scaleIndex) {
    this.x = x;
    this.y = y;
    this.scaleIndex = scaleIndex;
    this.vx = (Math.random() - 0.5) * 700;
    this.vy = (Math.random() - 0.5) * 700;
    this.lastHitTime = -Infinity;
    const baseRadius = 40;
    this.radius = lerp(
      2 * baseRadius,
      0.3 * baseRadius,
      (this.scaleIndex / (scaleProgression[0].length - 1)) ** 0.5
    );
  }
  getColorFromScale() {
    const hue = scaleHues[currentScaleIndex];
    const lightness = 25 + this.scaleIndex / 15 * 50;
    return `hsl(${hue}, 50%, ${lightness}%)`;
  }
  update(currentTime, canvasWidth, canvasHeight) {
    this.x += this.vx / frameRate;
    this.y += this.vy / frameRate;
    let wallHit = false;
    if (this.x - this.radius <= wallWidth || this.x + this.radius >= canvasWidth - wallWidth) {
      this.vx = -this.vx;
      this.x = Math.max(wallWidth + this.radius, Math.min(canvasWidth - wallWidth - this.radius, this.x));
      wallHit = true;
      collisionCount++;
    }
    if (this.y - this.radius <= wallWidth || this.y + this.radius >= canvasHeight - wallWidth) {
      this.vy = -this.vy;
      this.y = Math.max(wallWidth + this.radius, Math.min(canvasHeight - wallWidth - this.radius, this.y));
      wallHit = true;
      collisionCount++;
    }
    if (wallHit) {
      this.lastHitTime = currentTime;
      this.scheduleSound(currentTime);
    }
  }
  draw(ctx, currentTime) {
    const timeSinceHit = currentTime - this.lastHitTime;
    const progress = clamp(timeSinceHit / ballHitAnimationDuration, 0, 1);
    const radius = this.radius * (1 + (1 - progress) * 0.1);
    ctx.beginPath();
    ctx.arc(this.x, this.y, radius, 0, Math.PI * 2);
    const color = this.getColorFromScale();
    ctx.fillStyle = color;
    ctx.globalAlpha = 0.333;
    ctx.fill();
    const lineWidth = 0.15 * radius;
    ctx.beginPath();
    ctx.arc(this.x, this.y, radius - lineWidth / 2, 0, Math.PI * 2);
    ctx.globalAlpha = 1;
    ctx.strokeStyle = color;
    ctx.lineWidth = lineWidth;
    ctx.stroke();
    if (progress < 1) {
      const intensity = 1 - progress;
      ctx.globalAlpha = intensity * 0.8;
      ctx.strokeStyle = "white";
      ctx.stroke();
    }
    ctx.globalAlpha = 1;
  }
  checkCollision(other) {
    const dx = this.x - other.x;
    const dy = this.y - other.y;
    const distance = Math.sqrt(dx * dx + dy * dy);
    return distance < this.radius + other.radius;
  }
  collideWith(other) {
    const dx = other.x - this.x;
    const dy = other.y - this.y;
    const distance = Math.hypot(dx, dy);
    const nx = dx / distance;
    const ny = dy / distance;
    const rvx = other.vx - this.vx;
    const rvy = other.vy - this.vy;
    const speed = rvx * nx + rvy * ny;
    if (speed > 0) {
      return;
    }
    const thisMass = this.radius ** 2;
    const otherMass = other.radius ** 2;
    const impulse = 2 * speed / (thisMass + otherMass);
    this.vx += impulse * otherMass * nx;
    this.vy += impulse * otherMass * ny;
    other.vx -= impulse * thisMass * nx;
    other.vy -= impulse * thisMass * ny;
    const overlap = this.radius + other.radius - distance;
    if (overlap > 0) {
      const separateX = nx * overlap * 0.5;
      const separateY = ny * overlap * 0.5;
      this.x -= separateX;
      this.y -= separateY;
      other.x += separateX;
      other.y += separateY;
    }
  }
  scheduleSound(currentTime) {
    const oscillator = audioContext.createOscillator();
    const gainNode = audioContext.createGain();
    const pannerNode = audioContext.createStereoPanner();
    const panValue = (this.x / renderCanvas.width - 0.5) * Math.SQRT2;
    oscillator.connect(gainNode);
    gainNode.connect(pannerNode);
    pannerNode.connect(globalGainNode);
    const frequency = getFrequencyFromScaleIndex(this.scaleIndex);
    oscillator.frequency.setValueAtTime(frequency, currentTime);
    oscillator.type = "sine";
    pannerNode.pan.setValueAtTime(panValue, currentTime);
    gainNode.gain.setValueAtTime(0, currentTime);
    gainNode.gain.linearRampToValueAtTime(0.08, currentTime + 0.01);
    gainNode.gain.exponentialRampToValueAtTime(1e-3, currentTime + 0.4);
    gainNode.gain.linearRampToValueAtTime(0, currentTime + 0.5);
    oscillator.start(currentTime);
    oscillator.stop(currentTime + 0.6);
  }
}
const clamp = (value, min, max) => {
  return Math.min(Math.max(value, min), max);
};
const lerp = (a, b, t) => {
  return a + (b - a) * t;
};
const semitoneToFreq = (semitones) => {
  return 440 * Math.pow(2, semitones / 12);
};
const getCurrentScale = () => {
  return scaleProgression[currentScaleIndex];
};
const getFrequencyFromScaleIndex = (scaleIndex) => {
  const currentScale = getCurrentScale();
  return semitoneToFreq(currentScale[scaleIndex]);
};
const createReverbImpulse = (duration) => {
  const length = sampleRate * duration;
  const impulse = audioContext.createBuffer(numberOfChannels, length, sampleRate);
  for (let channel = 0; channel < 2; channel++) {
    const channelData = impulse.getChannelData(channel);
    for (let i = 0; i < length; i++) {
      const decayFactor = Math.pow(1e-3, i / length);
      let sample = 0;
      sample += (Math.random() * 2 - 1) * decayFactor;
      sample += (Math.random() * 2 - 1) * decayFactor * 0.7;
      sample += (Math.random() * 2 - 1) * decayFactor * 0.5;
      channelData[i] = sample * 0.5;
    }
  }
  return impulse;
};
const updateSliderDisplays = () => {
  durationValue.textContent = `${durationSlider.value} seconds`;
  ballsValue.textContent = `${ballsSlider.value} ${ballsSlider.value === "1" ? "ball" : "balls"}`;
};
durationSlider.addEventListener("input", updateSliderDisplays);
ballsSlider.addEventListener("input", updateSliderDisplays);
updateSliderDisplays();
renderButton.addEventListener("click", () => {
  void generateVideo();
});
