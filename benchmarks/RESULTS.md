# Overhead: baseline vs profiler vs plain JFR

## Methodology

These numbers were measured inside the sandboxed container the profiler was
built in (4 shared vCPUs, no dedicated CPU quota), **not** with the
`load-test.js` k6 script above (k6 isn't installed in that sandbox). They
were gathered with a small curl-based harness instead:

- **Sequential latency**: 80 sequential requests to `GET /cpu/regex?iterations=20000`
  (a fixed, CPU-bound workload - re-compiles and matches a regex 20,000
  times per request), timed with `curl -w "%{time_total}"`. Percentiles are
  nearest-rank over the 80 samples.
- **Concurrent throughput**: wall-clock time to complete 60 concurrent
  requests to the same endpoint, averaged over 3 trials after a 20-request
  JIT warm-up.

Three configurations of the same `demo-app` jar:

| Config | Flag | What it isolates |
|---|---|---|
| **Baseline** | `-Dprofiler.enabled=false` | No filter, no JFR stream at all |
| **Plain JFR** | `-Dprofiler.enabled=false -XX:StartFlightRecording=settings=profile` | JFR's own `jdk.ExecutionSample` cost, with none of this project's code running |
| **Profiler** | `-Dprofiler.enabled=true` | Full pipeline: tagging filter + custom event + `RecordingStream` + correlation |

Because this is a shared, noisy sandbox rather than a dedicated benchmark
host, treat these as **directional**, not authoritative - re-run
`load-test.js` against your own machine for real numbers (see below).

## Results

### Sequential latency, `GET /cpu/regex?iterations=20000` (n=80)

| Config | mean | p50 | p95 | p99 | max |
|---|---:|---:|---:|---:|---:|
| Baseline | 51.05 ms | 46.34 ms | 73.86 ms | 79.91 ms | 79.91 ms |
| Plain JFR | 51.77 ms | 46.94 ms | 78.75 ms | 94.48 ms | 94.48 ms |
| Profiler | 48.07 ms | 45.25 ms | 76.54 ms | 81.62 ms | 81.62 ms |

### Concurrent throughput, 60 concurrent requests (mean of 3 trials, post-warm-up)

| Config | mean wall time |
|---|---:|
| Baseline | 0.87 s |
| Profiler | 0.88 s |

### Reading these numbers

- At p50/mean, the profiler's overhead is within the run-to-run noise of
  this sandbox (a couple of milliseconds either way, same order as
  plain-JFR's own noise band). The tagging filter does one
  `Event.isEnabled()` check plus a `begin()`/`commit()` pair per request -
  cheap relative to a 20,000-iteration regex workload.
- The design keeps expensive work off the request path entirely: sample
  correlation, call-tree merging, and percentile computation all happen on
  `JfrStreamService`'s background thread as JFR delivers events
  asynchronously (roughly the ~1s delivery lag JFR streaming is documented
  to have), not inside `EndpointTaggingFilter.doFilterInternal`.
- Memory is bounded independently of load: `SampleBuffer` evicts
  unclaimed samples older than `profiler.sample-retention` (default 5s) on
  a 1s tick, and each `EndpointProfile`'s latency window is a fixed-size
  ring buffer (`profiler.latency-window-size`, default 5,000 durations) -
  so a long-running process doesn't accumulate memory proportional to
  total requests served.

## Reproducing this yourself

```bash
mvn -pl demo-app -am package -DskipTests

# Baseline
java -Dprofiler.enabled=false -jar demo-app/target/demo-app-0.1.0-SNAPSHOT.jar &
k6 run -e BASE_URL=http://localhost:8080 benchmarks/load-test.js

# Profiler enabled
java -Dprofiler.enabled=true -jar demo-app/target/demo-app-0.1.0-SNAPSHOT.jar &
k6 run -e BASE_URL=http://localhost:8080 benchmarks/load-test.js
```

Compare k6's `http_req_duration` summary across the two runs. On a
dedicated (non-shared, non-virtualized) machine you should see a cleaner
signal than the numbers above.
