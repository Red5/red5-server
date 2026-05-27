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

## Remaining scaling levers (not addressed here)

- Socket write syscalls dominate (~24-29%). Coalescing per-packet writes or raising the
  outbound RTMP chunk size would cut the number of `write()` syscalls.
- `RTMP$ChannelInfo` was ~12% of allocations; worth investigating for reuse.
