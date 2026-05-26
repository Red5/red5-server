# Low-latency browser playback with Mediabunny

This guide shows how to view a live RTMP stream published to Red5 directly in a browser using [Mediabunny](https://mediabunny.dev). It is intended for users who came here looking for [jsmpeg](https://jsmpeg.com/) support (issue [#283](https://github.com/Red5/red5-server/issues/283)) — Mediabunny solves the same problem (very low-latency browser playback over a plain HTTP connection) but without the MPEG1/MP2 transcoding tax that jsmpeg requires.

## Why Mediabunny instead of jsmpeg

| | jsmpeg | Mediabunny |
|---|---|---|
| Wire format | MPEG-TS over WebSocket | fragmented MP4 (CMAF) over HTTP |
| Codec on the wire | MPEG1 video + MP2 audio | AVC (H.264) + AAC — same as RTMP ingest |
| Transcode required? | Yes (RTMP H.264/AAC → MPEG1/MP2) | No — pass-through |
| Decode in browser | Pure JavaScript | Native WebCodecs (hardware where available) |
| CPU cost (client) | High | Low |
| Typical latency | ~200–500 ms | ~200–500 ms |
| Out of the box in Red5? | No (separate plugin) | Yes — bundled in the `live` webapp |

If you can publish AVC/AAC (which OBS, FFmpeg, mobile encoders, hardware encoders all do by default for RTMP), Mediabunny gets you the same latency profile with zero server-side transcoding.

## How the integration is wired

Red5 ships a small servlet + listener pair that re-wraps an incoming RTMP stream as fragmented MP4 on demand. No extra configuration is needed; everything is already in the `live` webapp.

```
RTMP publisher                Red5                                   browser
─────────────                 ────                                   ───────

OBS / FFmpeg ──► rtmp://host:1935/live/<stream>
                                │
                                ▼
                 ClientBroadcastStream (AVC + AAC)
                                │
                                │  StreamListener
                                ▼
                 MediaBunnyStreamListener
                  (Fmp4InitSegmentBuilder + Fmp4FragmentBuilder)
                                │
                                │  init segment + media fragments
                                ▼
                 MediaBunnyStreamRegistry
                                │
                                ▼
                 MediaBunnyServlet ────► GET /live/mb?stream=<stream>
                                                                    │
                                                                    ▼
                                                       Mediabunny `Input`
                                                       ├── CanvasSink   ──► <canvas>
                                                       └── AudioBufferSink ──► WebAudio
```

Source code references:
- `server/src/main/java/org/red5/server/net/mediabunny/MediaBunnyServlet.java`
- `server/src/main/java/org/red5/server/net/mediabunny/MediaBunnyStreamRegistry.java`
- `server/src/main/java/org/red5/server/net/mediabunny/MediaBunnyStreamListener.java`
- `server/src/main/server/webapps/live/WEB-INF/web.xml` (servlet mapping at `/mb`)
- `server/src/main/server/webapps/live/mediabunny/minimal.{html,js}` (browser example)

## Prerequisites

- Red5 Server 2.0.30 or newer (the Mediabunny integration shipped with the `live` webapp).
- A modern browser with [WebCodecs](https://developer.mozilla.org/en-US/docs/Web/API/WebCodecs_API) (Chrome 94+, Edge 94+, Firefox 130+, Safari 16.4+).
- An RTMP encoder that produces AVC (H.264) video and AAC audio. OBS, FFmpeg, and most hardware encoders do this by default.

## Step 1 — Start Red5

Use the bundled distribution or run from source:

```bash
# from a release tarball
./red5.sh

# or, when building from source
mvn -Dmaven.test.skip=true clean package -P assemble
cd target/red5-server-*-server/
./red5.sh
```

Confirm the `live` webapp loaded by visiting `http://<host>:5080/live/`. You should see a "Minimal Example" link.

## Step 2 — Publish an RTMP stream

Push H.264/AAC to `rtmp://<host>:1935/live/<streamName>`.

OBS Studio:
- Settings → Stream → Service: **Custom**
- Server: `rtmp://<host>:1935/live`
- Stream Key: `mystream` (or whatever name you want to view later)

FFmpeg (from a file or device):

```bash
ffmpeg -re -i input.mp4 \
       -c:v libx264 -preset veryfast -tune zerolatency -profile:v baseline -g 60 \
       -c:a aac -ar 48000 -b:a 128k \
       -f flv rtmp://localhost:1935/live/mystream
```

Latency tips for the encoder:
- Short keyframe interval (`-g 30` or `-g 60` for 30 fps).
- `tune zerolatency` and `preset veryfast` (or faster).
- Smallest possible CBR bitrate that meets your quality bar.

## Step 3 — View the stream in the browser

Open `http://<host>:5080/live/mediabunny/minimal.html`, type the stream name you pushed (e.g. `mystream`) into the input, and click **Start**. The minimal page fetches `/live/mb?stream=<name>`, hands the response body to a Mediabunny `Input`, and renders frames into a `<canvas>` using `CanvasSink`. Audio is played through WebAudio via `AudioBufferSink`.

You can copy `minimal.html` and `minimal.js` out of `server/src/main/server/webapps/live/mediabunny/` and embed the player in your own webapp. The only Red5-specific URL is the fetch endpoint.

## The endpoint contract

```
GET /<app>/mb?stream=<streamName>
Content-Type: video/mp4
Transfer-Encoding: chunked   (servlet async write)
```

- `<app>` is the Red5 webapp name. The bundled example is `live`. If you copy the servlet mapping into your own webapp's `WEB-INF/web.xml` (see below) it follows your app's context path.
- Response body is a continuous fMP4 byte stream: one `ftyp+moov` init segment, optionally an immediately-following keyframe `moof+mdat` so new viewers don't wait, then a stream of `moof+mdat` media fragments.
- The connection stays open for the life of the broadcast; the server emits a zero-length sentinel chunk to close gracefully when the publisher disconnects.
- CORS is permissive (`Access-Control-Allow-Origin` mirrors `Origin` or falls back to `*`) so you can host the player on a different origin than Red5.

If you only want to verify the stream is flowing without writing JS:

```bash
curl -N 'http://localhost:5080/live/mb?stream=mystream' -o capture.mp4
# then open capture.mp4 in VLC or ffprobe it
```

## Embedding the servlet in a custom webapp

The bundled servlet lives in the `live` webapp. To add it to your own Red5 webapp, copy the mapping from `server/src/main/server/webapps/live/WEB-INF/web.xml` into your webapp's `WEB-INF/web.xml`:

```xml
<servlet>
    <servlet-name>mediabunny</servlet-name>
    <servlet-class>org.red5.server.net.mediabunny.MediaBunnyServlet</servlet-class>
    <load-on-startup>4</load-on-startup>
    <async-supported>true</async-supported>
</servlet>
<servlet-mapping>
    <servlet-name>mediabunny</servlet-name>
    <url-pattern>/mb</url-pattern>
</servlet-mapping>
```

`async-supported=true` is required — the servlet uses `startAsync` to push fragments without blocking a request thread. The class is on the server classpath; nothing extra needs to ship inside your webapp.

## Minimal browser snippet

If you don't want the full `minimal.js`, this is the smallest amount of code that will display a stream:

```html
<canvas id="v" width="1280" height="720"></canvas>
<script type="module">
  import {
    Input, ALL_FORMATS, ReadableStreamSource, CanvasSink
  } from '/live/mediabunny/mediabunny.mjs';

  const res = await fetch('/live/mb?stream=mystream');
  const input = new Input({
    source: new ReadableStreamSource(res.body),
    formats: ALL_FORMATS,
  });
  const track = await input.getPrimaryVideoTrack();
  const sink = new CanvasSink(track, { fit: 'contain' });
  const canvas = document.getElementById('v');
  const ctx = canvas.getContext('2d');
  for await (const frame of sink.canvases(0, Infinity)) {
    if (!frame) continue;
    canvas.width = frame.canvas.width;
    canvas.height = frame.canvas.height;
    ctx.drawImage(frame.canvas, 0, 0);
  }
</script>
```

For lip-synced audio, add an `AudioBufferSink` driven by an `AudioContext` — see `minimal.js` for a working reference (look at `runAudio`/`runVideo` for the clock-syncing pattern).

## Troubleshooting

- **`404 Stream not found: <name>`** — the publisher hasn't connected yet, or the stream name in the URL does not match the publish key exactly. Names are case-sensitive.
- **`500 Scope not available`** — the servlet started before the `live` Spring context was ready. Confirm `live` is in `red5.properties` `webapp.virtualHosts` and check the logs for context-load errors.
- **First frame takes several seconds** — the servlet waits for the publisher's first keyframe so it can build a valid init segment. Lower the encoder's keyframe interval (`-g 30`).
- **Stalls or A/V drift after a long time** — confirm the publisher is using a sane PTS clock. A misbehaving encoder that sends non-monotonic timestamps will cause `Fmp4FragmentBuilder` to drop samples.
- **CORS errors when the player is on a different origin** — the servlet sends `Access-Control-Allow-Origin` with credentials enabled; make sure your `fetch` does not pass `credentials: 'include'` unless your reverse proxy is set up for it.
- **Other codecs (HEVC, AV1, Opus, etc.)** — the current listener only handles AVC + AAC. Other codecs arriving over RTMP will be ignored; the stream will appear to have no playable tracks. Use OBS/FFmpeg settings above to confirm AVC/AAC on the way in.

## What this does *not* do

- It is not a generic HLS / DASH / LL-HLS endpoint — there are no `.m3u8` or `.mpd` manifests, no segment files on disk, and no CDN-friendly cache headers. It is intended for direct point-to-point browser playback at low latency.
- It does not authenticate. If you need to gate access to streams, put the servlet behind a reverse-proxy auth check or wrap it in your own filter.
- It does not transcode. AVC + AAC in, AVC + AAC out.

## See also

- [Mediabunny documentation](https://mediabunny.dev)
- Issue [#283](https://github.com/Red5/red5-server/issues/283) — the original jsmpeg request that motivated this integration
- Enhanced RTMP v2 spec (newer FourCC codecs Red5 ingests): <https://veovera.org/docs/enhanced/enhanced-rtmp-v2.html>
