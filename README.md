# JFR Endpoint Profiler

A CPU profiler for Spring Boot apps that attributes JDK Flight Recorder
samples to HTTP endpoints and reports the slowest endpoints and their
hottest methods - built on `jdk.jfr.consumer.RecordingStream`, with none of
the sampling logic reimplemented.

```
GET /orders/42        (slow)
  p95: 340ms  p99: 410ms
  hot methods:
    41.2%  OrderService.enrichWithInventory
    18.7%  java.util.regex.Pattern.compile
     9.1%  JacksonMapper.writeValueAsString
```

## The core problem

JFR's `jdk.ExecutionSample` event tells you which thread was running and
what its stack looked like at that instant - it says nothing about HTTP.
There is no built-in way to ask "which endpoint was this sample taken
during?"

The fix: emit a **custom JFR event per request** that brackets the request
with `begin()`/`commit()`, carrying the endpoint, thread, and time window.
Then join the two event streams **by thread id and timestamp**: for each
completed request, pull every buffered CPU sample on that thread whose
timestamp falls inside `[request.start, request.end]`.

Everything else - the servlet filter, the sample buffer, the correlator,
the aggregation, the dashboard - exists to make that one join reliable and
cheap.

## Architecture

```
                      ┌─────────────────────────────────────────────┐
                      │              Spring MVC request              │
                      │                                               │
  HTTP request ──────▶│  EndpointTaggingFilter                        │
                      │    event = new EndpointRequestEvent()         │
                      │    event.begin()                              │
                      │    chain.doFilter(...)  ───────▶ your handler │
                      │    event.pathPattern = BEST_MATCHING_PATTERN  │
                      │    event.commit()                             │
                      └───────────────────┬───────────────────────────┘
                                          │ writes to the JFR
                                          │ repository (buffered)
                                          ▼
                      ┌─────────────────────────────────────────────┐
                      │  JVM: Flight Recorder                        │
                      │   - jdk.ExecutionSample   (every ~10ms,      │
                      │     one thread per tick, real call stacks)   │
                      │   - dev.shashank.profiler.EndpointRequest    │
                      │     (one per completed request)              │
                      └───────────────────┬───────────────────────────┘
                                          │ streamed asynchronously
                                          │ (~1s typical delay)
                                          ▼
                      ┌─────────────────────────────────────────────┐
                      │  JfrStreamService (RecordingStream)          │
                      │                                               │
                      │   onEvent(ExecutionSample)                    │
                      │     ──▶ SampleBuffer.add(threadId, sample)   │
                      │                                               │
                      │   onEvent(EndpointRequest)                    │
                      │     ──▶ SampleCorrelator.onEndpointRequest    │
                      │           samples = buffer.consumeRange(      │
                      │             threadId, start, end)             │
                      │           registry.record(request, samples)  │
                      │                                               │
                      │   every 1s: evict samples nobody claimed      │
                      │   (bounds memory for excluded/background      │
                      │   threads)                                    │
                      └───────────────────┬───────────────────────────┘
                                          ▼
                      ┌─────────────────────────────────────────────┐
                      │  ProfileRegistry (profiler-core, no JFR/     │
                      │  Spring imports - pure Java, unit testable)  │
                      │                                               │
                      │   Map<EndpointKey, EndpointProfile>           │
                      │     - LatencyStats (p50/p95/p99 ring buffer) │
                      │     - CallTree (merged, self/total counts)   │
                      └───────────────────┬───────────────────────────┘
                                          ▼
                      ┌─────────────────────────────────────────────┐
                      │  ProfilerController  /profiler/api/...      │
                      │    GET /endpoints             (table)        │
                      │    GET /endpoints/hotmethods   (top-N)        │
                      │    GET /endpoints/flamegraph  (d3 JSON)      │
                      │                                               │
                      │  /profiler/index.html - endpoint table +     │
                      │  d3-flame-graph, sorted by p95, 5s poll       │
                      └───────────────────────────────────────────────┘
```

## Why the path pattern, not the raw URI

`EndpointTaggingFilter` reads
`HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE` **after** `chain.doFilter`
returns, so it captures `/orders/{id}` rather than `/orders/42`,
`/orders/43`, `/orders/44`, .... Without this, every distinct path variable
value becomes its own "endpoint" and the registry grows without bound.

## Why `profiler-core` has zero Spring/JFR imports

Aggregation (merging stacks into a call tree, computing percentiles,
exporting flame-graph JSON) is pure data transformation - it doesn't need a
servlet container or a JVM profiler running to be correct. Keeping it
dependency-free means:

- `CallTreeTest`, `LatencyStatsTest`, `HotMethodsReportTest`, etc. build
  `StackSample`/`RequestRecord` objects by hand and run in milliseconds,
  no JFR recording required.
- The starter module (`profiler-spring-boot-starter`) is the *only* place
  that touches `jdk.jfr.*` or Spring - `JfrMapper` is the sole translation
  boundary between JFR's `RecordedEvent` and the core model types.

## Two bugs this project's own tests caught

Both were found by actually running the demo app end to end, not just
unit-testing the pieces in isolation - worth calling out because they're
easy to reintroduce:

1. **`jdk.ExecutionSample`'s thread field is named `sampledThread`, not
   `eventThread`.** `RecordedEvent.getThread()` (no-arg) only looks up a
   field literally named `eventThread`; for `jdk.ExecutionSample` it
   silently returns `null`, so every `StackSample` came back with thread id
   `-1` and could never match a request. Fixed by calling
   `event.getThread("sampledThread")` explicitly in `JfrMapper`.
   `JfrMapperIntegrationTest` opens a real `RecordingStream` and asserts
   the mapped thread id is correct, specifically to catch this class of
   regression - hand-built `StackSample` fixtures in `SampleCorrelatorTest`
   can't, since they never go through the mapper.

2. **Missing `-parameters` compiler flag.** Every `@RequestParam` without
   an explicit name (`@RequestParam int n` instead of
   `@RequestParam("n") int n`) threw `IllegalArgumentException` at request
   time, because Spring couldn't resolve the parameter name via reflection.
   Fixed by setting `<parameters>true</parameters>` on the compiler plugin
   (and adding explicit names anyway, as defense in depth).

## Module layout

- **`profiler-core`** - `model` (`Frame`, `StackSample`, `RequestRecord`,
  `EndpointKey`), `aggregate` (`CallTree`, `LatencyStats`,
  `ProfileRegistry`), `report` (`FlameGraphExporter`, `HotMethodsReport`,
  `TextReport`). No Spring, no JFR.
- **`profiler-spring-boot-starter`** - `capture` (`EndpointRequestEvent`,
  `EndpointTaggingFilter`), `stream` (`JfrStreamService`, `SampleBuffer`,
  `SampleCorrelator`, `JfrMapper`), `web` (`ProfilerController` + DTOs),
  `autoconfigure` (`ProfilerAutoConfiguration`, `ProfilerProperties`), and
  the static dashboard.
- **`demo-app`** - a Spring Boot app with four deliberately inefficient
  endpoints (naive recursive Fibonacci, a regex recompiled on every call,
  manual string-concatenation JSON serialization, and a coarse-grained
  lock held during real CPU work) to give the profiler something to find.
- **`benchmarks`** - a k6 load-test script and measured overhead numbers.

## Running it

```bash
mvn clean install -DskipTests
java -jar demo-app/target/demo-app-0.1.0-SNAPSHOT.jar
```

Then hit some endpoints and open the dashboard:

```bash
curl "http://localhost:8080/cpu/fibonacci?n=33"
curl "http://localhost:8080/cpu/regex?iterations=30000"
curl "http://localhost:8080/serialize/orders?count=5000"
curl "http://localhost:8080/lock/increment?work=500000"
```

Open `http://localhost:8080/profiler/index.html` - the endpoint table
(sorted by p95, click a column to re-sort) and, per endpoint, its flame
graph and top hot methods (d3-flame-graph loaded from a CDN, so it needs
outbound internet).

Or hit the JSON API directly:

```bash
curl "http://localhost:8080/profiler/api/endpoints"
curl "http://localhost:8080/profiler/api/endpoints/hotmethods?method=GET&pattern=/cpu/regex&topN=5"
curl "http://localhost:8080/profiler/api/endpoints/flamegraph?method=GET&pattern=/cpu/regex"
```

## Configuration (`profiler.*`)

| Property | Default | Meaning |
|---|---|---|
| `profiler.enabled` | `false` | Master switch - filter, stream, and REST API only exist when `true` |
| `profiler.sample-period` | `10ms` | Period between `jdk.ExecutionSample` events |
| `profiler.sample-retention` | `5s` | How long an unclaimed CPU sample is kept before eviction |
| `profiler.latency-window-size` | `5000` | Max recent request durations kept per endpoint (bounds memory) |
| `profiler.hot-methods-per-endpoint` | `10` | Default top-N for the hot-methods API |
| `profiler.exclude-paths` | `/profiler/**`, `/actuator/**` | Ant-style patterns never tagged or profiled |

## Overhead

See [`benchmarks/RESULTS.md`](benchmarks/RESULTS.md) - measured against
baseline (no profiler) and plain JFR (JFR running with none of this
project's code). Summary: the tagging filter's per-request cost
(`Event.isEnabled()` plus `begin()`/`commit()`) is within run-to-run noise
against a CPU-bound workload; the real work (correlation, aggregation,
percentiles) happens off the request path on `JfrStreamService`'s
background thread.

## Tests

```bash
mvn test
```

22 tests: `profiler-core` unit-tests aggregation/reporting against
hand-built samples (no JFR needed); `profiler-spring-boot-starter` tests
the correlation logic (`SampleCorrelatorTest`) against synthetic samples,
plus one integration test (`JfrMapperIntegrationTest`) that opens a real
`RecordingStream` to guard against the `sampledThread` regression above.
