# RTMP Ingest CPU/Memory Profiling and Fixes

Investigation date: 2026-05-27

## Symptom

DevOps reported that a 2 vCPU instance saturated at roughly 20 concurrent RTMP
publishers (plain RTMP, ~4-8 Mbps 1080p30, live fan-out to 1-5 subscribers each),
which was lower throughput per core than expected.

## Method

The live system could not be profiled, so a local profiled reproduction was used:

- Extracted Red5 server distribution, pinned to 2 cores (`taskset -c 0,1`) with
  `-XX:ActiveProcessorCount=2` and the default ZGC configuration, to model a 2 vCPU box.
- Load generated with ffmpeg streaming a pre-encoded 1080p30 / 6 Mbps file using
  `-c copy` (no encode cost), publishers and subscribers pinned to the remaining cores
  so they could not contaminate Red5's two cores.
- 20 publishers x 3 subscribers = 80 concurrent streams (~480 Mbps relayed).
- CPU and allocation profiles captured with async-profiler using the `ctimer` engine
  (the host has `perf_event_paranoid=4`, so perf-based events are unavailable).

A self-contained JMH-style micro-benchmark was also used to isolate the received-packet
dispatch cost before profiling.

## Findings (baseline CPU profile, inclusive)

| Cost                                                  | % CPU | Nature                         |
|-------------------------------------------------------|-------|--------------------------------|
| Socket write syscalls (`SocketDispatcher.write0`)     | ~29%  | Inherent to relaying the bytes |
| Socket read syscalls                                  | ~14%  | Inherent                       |
| `String.format` inside `ChunkHeader.read` (trace log) | ~7.2% | Pure waste (fixed)             |
| Fan-out + per-subscriber encode                       | ~9%   | Mostly necessary               |
| Received-packet dispatch (vthread + join)             | ~5%   | Overhead (fixed)               |
| ZGC                                                   | ~2.6% | Minor                          |

Allocation profile: `byte[]` 61.5% (buffer copies, inherent), `RTMP$ChannelInfo` 12.2%,
and `Matcher` + `Formatter` ~3.8% (the `ChunkHeader` `String.format` again).

Headline conclusion: per-stream compute is small; the workload is dominated by socket
I/O syscalls (~45%). The confirmed waste below is real but accounts for roughly 12% of
compute, not a multiplier.

## Fixes in this change

### 1. `ChunkHeader.read` eager `String.format` in a disabled trace log

`common/.../net/rtmp/message/ChunkHeader.java`

`log.trace(...)` does not print in production (TRACE disabled), but its arguments are
evaluated eagerly. `String.format("%02x", headerByte)` therefore ran on every chunk,
parsing its format string via regex and allocating a `Formatter`/`Matcher`, only to
discard the result. The call is now guarded with `log.isTraceEnabled()`.

Impact: removes ~7% of CPU and ~3.8% of allocations on the decode path.

### 2. Redundant per-packet virtual-thread dispatch

`common/.../net/rtmp/RTMPConnection.java` (`handleMessageReceived`)

Each received packet was wrapped in a `ReceivedMessageTask`, dispatched to a
per-connection virtual-thread executor via `CompletableFuture.supplyAsync(...)`, and then
immediately `join()`-ed. Because the single-threaded receiver loop blocks on the join,
this provided no concurrency benefit while adding a virtual-thread spawn, a
`CompletableFuture` allocation, and two context switches per packet.

The task now runs inline (`task.get()`) on the per-connection receiver thread. Packet
ordering is preserved because that loop is single-threaded. `ReceivedMessageTask.get()`
already records handler exceptions via the connection `exception` attribute, so error
handling is unchanged.

Impact: removes the ~5% dispatch overhead and the associated context-switch and
allocation churn (measured 3.6x throughput and 9x less allocation at saturation in an
isolated micro-benchmark).

## Verification

Re-profiled under the identical 80-stream load after applying both fixes:

| Frame                                   | Before | After |
|-----------------------------------------|--------|-------|
| `ChunkHeader.read` / `String.format`    | 7.3%   | 0.0%  |
| regex                                   | 4.6%   | 0.0%  |
| `CompletableFuture` / `supplyAsync`     | 12.5%  | 0.0%  |
| RTMP decode path (total)                | 12.2%  | 3.7%  |

Both targeted paths were eliminated; nothing untouched disappeared. Overall CPU dropped
single digits (the remainder is I/O-bound), giving roughly 10% more publisher headroom
per core.

Both targeted paths were eliminated; nothing untouched disappeared.

## Follow-up: allocation and encode-path fixes

After the two fixes above, profiling showed allocation (memory cost) dominated by `byte[]`
(61.5%) and `RTMP$ChannelInfo` (12.2%). Three further fixes target the encode/decode path.

### 3. `RTMP.getChannelInfo` allocated per call

`common/.../net/rtmp/codec/RTMP.java`

`getChannelInfo` used `channels.putIfAbsent(channelId, new ChannelInfo())`, which allocated a
`ChannelInfo` on every call and discarded it whenever the channel already existed. The method
is called several times per packet, so it was ~12% of all allocations. Replaced with a
get-first idiom that allocates only when a channel is first seen. (A `computeIfAbsent` lambda
was deliberately avoided: `ChannelInfo` is a non-static inner class, so its construction
captures the enclosing instance and the lambda would itself be allocated per call.)

### 4. Per-chunk temporary `byte[]` in the encoder

`common/.../net/rtmp/codec/RTMPProtocolEncoder.java`

The chunk-writing loop allocated `new byte[chunkSize]` and copied through it for every chunk
of every outbound message. Replaced with a direct buffer-to-buffer copy (slice the source
`IoBuffer` and `put` it), eliminating the per-chunk array allocation.

### 5. Outbound chunk size raised 1024 -> 4096

`common/.../stream/consumer/ConnectionConsumer.java`

The outbound RTMP chunk size sent to subscribing clients was 1024 (with a "not sure of the
best value" TODO). Raised to 4096 (the de-facto standard used by FFmpeg, OBS and nginx-rtmp),
cutting the per-message chunk count ~4x for typical video frames - fewer chunk headers, fewer
copies, less encoder work - with no client compatibility impact.

### 6. Per-chunk `Arrays.copyOfRange` in the decoder

`common/.../net/rtmp/codec/RTMPProtocolDecoder.java`

Chunk reassembly allocated `byte[] chunk = Arrays.copyOfRange(in.array(), ...)` for every chunk
of every inbound packet, purely to transfer bytes from the input buffer into the packet buffer.
Replaced with a direct buffer-to-buffer transfer (limit the source `IoBuffer` to the chunk and
`buf.put(in)`), which also advances the input position so the prior explicit `skip` is removed.
The per-chunk allocation is now only performed when TRACE logging is enabled.

## Combined verification

Re-profiled under the identical 80-stream load with all six fixes applied (allocation profiles
are 15s windows; CPU samples are rate-normalized for comparison):

| Metric                         | Baseline | Final  | Change |
|--------------------------------|----------|--------|--------|
| Allocation (profiler samples)  | 14308    | 8236   | -42%   |
| CPU (rate-normalized)          | 52.7/s   | 46.6/s | -12%   |
| `RTMP$ChannelInfo` allocation  | 12.2%    | 0%     | gone   |
| RTMP decode path CPU           | 12.2%    | ~4%    | gone   |
| `Arrays.copyOfRange` (decode)  | present  | 0%     | gone   |
| ZGC CPU                        | 2.6%     | 1.7%   | less GC|

The ~42% allocation reduction is the main memory win and lowers GC frequency; CPU drops ~12%,
with the remainder being inherent socket I/O. The decoder change is an allocation reduction
(its `copyOfRange` was only ~1-2% CPU). Remaining `byte[]` allocation is dominated by the
necessary per-packet message buffers and MINA I/O buffers. RTMPChunkingTest, OriginEdgeChunkTest
and RTMPExtendedTimestampTest pass, and 80 ffmpeg subscribers consumed the streams cleanly.

## Remaining scaling levers (not addressed)

- Socket read/write syscalls still dominate (~36-45% combined) and are largely inherent to
  per-frame low-latency relaying; coalescing writes would trade latency for fewer syscalls.
