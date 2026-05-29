# Red5 Low-Latency Tuning on Constrained (2-Core) Deployments — Executive Summary

## Situation

A prior optimization pass (`perf/rtmp-ingest-cpu`, merged as #444) reduced RTMP relay **average CPU ~12%** and **allocation ~39%**. QA/DevOps reported the build felt **no smoother than 2.0.34** on **2-core, CPU-limited container** deployments, where the metric that matters is **latency / smoothness (jitter, glitches)**.

## Key Finding

The earlier work optimized the wrong axis for this symptom. **Average CPU and garbage are not what cause streaming jitter on a CPU-capped container.** Jitter there is driven by *tail* events — CPU-quota throttling, thread over-subscription, and GC scheduling — none of which changed between 2.0.34 and the new build. The improvements were real but moved the median (p50), not the p99 that QA observes.

Three drivers were identified and measured (Docker, cgroup v2, 2 cores, 15 publishers + 30 subscribers):

| Driver | Evidence | Status |
|---|---|---|
| **Thread over-subscription** | One dedicated platform thread per connection (60 connections → 132 OS threads) plus a soft thread leak | **Fixed** — moved to virtual threads (132 → 69 OS threads, leak gone) |
| **Wrong GC for few cores** | Legacy ZGC's always-on concurrent threads steal from the 2 app cores | **Fixed** — GC now selected by core count |
| **CPU-quota (CFS) throttling** | A hard fractional CPU quota freezes all threads when the per-period budget is spent | **Mitigated + deployment guidance** |
| **JVM core mis-detection** | CPU *requests*/shares without a hard *limit* make the JVM size every pool for host cores | **Deployment guidance** |

## What Changed (branch `perf/rtmp-thread-oversubscription`)

1. **Connection receive threads → virtual threads.** Eliminated one platform thread per connection and a never-shutdown executor leak. Ordering preserved. *Result: ~48% fewer OS threads under load.*
2. **Resource-aware GC selection** in `red5.sh` (≤4 cores → ParallelGC, else G1). *Result: ~2.3× less quota throttling than ZGC on 2 cores, ~7 ms worst-case pause.*
3. **Packaging fixes** so the distribution boots from scratch: restored `jcodec` + `commons-io` (dropped by a wildcard dependency exclusion) and added `json-smart` for the chat demo webapp.

### Measured impact (2-core container, identical 60-connection load)

| Metric | Before (2.0.34) | After |
|---|---|---|
| OS threads | 132 | **69** |
| Per-connection platform threads | 60 | **0** |
| GC quota-throttled time / 30 s | 617 ms (ZGC) | **266 ms** (Parallel) |
| Worst GC pause | n/a (concurrent) | **6.8 ms** |
| CPU-quota throttling with whole-core pinning | n/a | **0 ms** |

> Honest caveat: the thread fix reduces memory and scheduling overhead but **does not by itself remove CPU-quota throttling** — throttling is bound by total CPU demand. The largest jitter reductions on a capped container come from the **GC change** and, above all, **how CPU is allocated** (see below).

## Recommended Startup Settings

The startup script now **auto-selects the GC by detected core count**, so most deployments need no GC flags. The settings below cover the cases that still matter.

### Without Docker (bare metal / VM)

`red5.sh` detects cores via `nproc` and configures itself:

- **2-core VM:** automatically uses `ParallelGC` — no action needed.
- **Larger server:** automatically uses `G1` with a 200 ms pause goal.

Adjust only the heap for the box, via the `JVM_OPTS` environment variable (overrides the script default):

```bash
# Example: 2-core VM, give the relay a 2 GB heap, keep the auto-selected ParallelGC
export JVM_OPTS="-XX:+UseParallelGC -Xms512m -Xmx2g -XX:ReservedCodeCacheSize=32m"
./red5.sh
```

If the host is large but Red5 is meant to use only some cores, pin it and tell the JVM:

```bash
taskset -c 0,1 env JVM_OPTS="-XX:+UseParallelGC -XX:ActiveProcessorCount=2 -Xms512m -Xmx2g" ./red5.sh
```

### With Docker

**Prefer whole-core pinning over a fractional quota** — it eliminates CPU-quota throttling entirely (measured 0 ms vs ~300 ms / 30 s):

```bash
# Recommended: pin to whole cores (no CFS quota → no throttling stalls)
docker run -d --cpuset-cpus="0,1" --memory=2g \
  -p 1935:1935 -p 5080:5080 \
  mondain/red5:latest
# red5.sh sees 2 cores and selects ParallelGC automatically.
```

If you must use a fractional quota (`--cpus`), make the JVM size its pools to the limit and expect some throttling:

```bash
docker run -d --cpus=2 --memory=2g \
  -e JVM_OPTS="-XX:+UseParallelGC -XX:ActiveProcessorCount=2 -Xms512m -Xmx2g -XX:ReservedCodeCacheSize=32m" \
  -p 1935:1935 -p 5080:5080 \
  mondain/red5:latest
```

### With Kubernetes

- Use **integer CPU limits** (e.g. `limits.cpu: "2"`) and enable the **static CPU Manager policy** (`--cpu-manager-policy=static`) so pods get exclusive whole cores — this is the k8s equivalent of `--cpuset-cpus` and avoids quota throttling.
- Avoid setting only `requests.cpu` with no `limits.cpu`: the JVM then reads the **host** core count and over-sizes GC, JIT, and thread pools.
- If fractional limits are unavoidable, add `-XX:ActiveProcessorCount=<limit>` via `JVM_OPTS`.

```yaml
resources:
  requests: { cpu: "2", memory: "2Gi" }
  limits:   { cpu: "2", memory: "2Gi" }   # integer + static CPU manager → exclusive cores
```

## Bottom Line

- The relay code and packaging are now correct and lighter on threads.
- For **smoothness on capped deployments, the highest-leverage change is how CPU is allocated**: pin whole cores (cpuset / k8s static CPU manager) instead of a fractional quota, and ensure a hard limit so the JVM sizes itself correctly.
- Validate with **p99 frame jitter and cgroup `cpu.stat` (`nr_throttled` / `throttled_usec`)** — not average CPU — comparing the same load against 2.0.34.
