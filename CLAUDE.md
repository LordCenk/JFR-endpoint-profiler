# CLAUDE.md

Guidance for Claude Code sessions working in this repository.

## What this project is

A CPU profiler for Spring Boot apps that attributes JFR samples to HTTP
endpoints. See `README.md` for the full architecture and design rationale
before making changes - the short version:

1. `EndpointTaggingFilter` brackets every request with a custom
   `EndpointRequestEvent` (JFR `begin()`/`commit()`), tagged with the
   matched route pattern (not the raw URI).
2. `JfrStreamService` runs a `RecordingStream` with `jdk.ExecutionSample`
   enabled alongside it.
3. `SampleCorrelator` joins the two by **thread id + timestamp window**:
   for each completed request, pull every buffered sample on that thread
   whose timestamp falls in `[request.start, request.end]`.
4. `ProfileRegistry` (pure Java, `profiler-core`) aggregates: latency
   percentiles + a merged call tree per endpoint.
5. `ProfilerController` and the static dashboard serve it as JSON / a
   flame graph.

## Module boundaries - do not violate these

- **`profiler-core` must never import Spring or `jdk.jfr.*`.** It's the
  one module unit-tested with hand-built fixtures and no running JVM
  profiler. If you need a new core type or aggregation, it goes here, and
  it should be constructible from plain Java objects in a test.
- **`JfrMapper`** (`profiler-spring-boot-starter/.../stream/JfrMapper.java`)
  is the *only* place that should translate between JFR's `RecordedEvent`
  and the core model types (`Frame`, `StackSample`, `RequestRecord`). Don't
  reach into `RecordedEvent` from anywhere else.
- **`demo-app` depends on the starter as a published dependency**, the way
  a real consumer would - don't add a source-level shortcut between them.

## Known JFR gotchas (read before touching `JfrMapper` or `JfrStreamService`)

- **`jdk.ExecutionSample`'s thread field is named `sampledThread`, not
  `eventThread`.** `RecordedEvent.getThread()` (no-arg) only resolves a
  field literally named `eventThread` and returns `null` for anything
  else - it does NOT search for "whichever field has type Thread". Always
  use `event.getThread("sampledThread")` for this event. This exact bug
  shipped once already (every sample got thread id `-1`, correlation
  silently matched nothing) and is covered by
  `JfrMapperIntegrationTest`, which opens a real `RecordingStream` - don't
  delete or neuter that test, and don't assume `SampleCorrelatorTest`
  (which builds `StackSample` by hand) would catch a regression here, since
  it never goes through `JfrMapper` at all.
- **Custom `Event` subclasses (`EndpointRequestEvent`) do use the
  conventional `eventThread` field** - JFR generates that field
  automatically for any `Event` subclass. `event.getThread()` (no-arg) is
  correct there. The inconsistency is JFR's, not this codebase's.
- **JFR streaming has real delivery latency** (~1s is typical, per the
  JFR docs) - `ExecutionSample` and `EndpointRequest` events for the same
  request do not arrive back-to-back. This is why `SampleBuffer` exists
  (buffer samples until the matching request event shows up) and why
  `SampleCorrelator.evictStaleSamples()` runs on a timer rather than
  correlating synchronously.
- **`@RequestParam` needs either an explicit name or the `-parameters`
  compiler flag.** The parent POM sets
  `<parameters>true</parameters>` on `maven-compiler-plugin` - if you add a
  new Maven module or override compiler config, keep that flag, or every
  unnamed `@RequestParam`/`@PathVariable` will throw
  `IllegalArgumentException` at request time (this shipped once; the demo
  app's endpoints all 500'd silently until it was caught by actually
  curling them, not by the unit tests).

## Testing conventions

- `profiler-core` tests build `Frame`/`StackSample`/`RequestRecord`
  objects by hand - no JFR recording, no Spring context. Keep new core
  tests this way.
- `profiler-spring-boot-starter` has two kinds of test:
  - Fast, synthetic ones (`SampleCorrelatorTest`) that construct
    `StackSample`/`RequestRecord` directly to test buffering/eviction/join
    logic without a real JFR recording.
  - One real-JFR integration test (`JfrMapperIntegrationTest`) that opens
    an actual `RecordingStream` and busy-loops until a real
    `jdk.ExecutionSample` arrives, specifically to catch JFR API
    surprises like the `sampledThread` one above. Keep this test - it's
    the only thing that exercises the real `RecordedEvent` shape.
- Run everything with `mvn test` from the repo root. `profiler-core` must
  build and test standalone via `mvn -pl profiler-core test` too (it's
  parent-relative but has no inter-module dependency).

## Verifying changes end to end

Unit tests don't exercise the actual JFR pipeline end to end (only
`JfrMapperIntegrationTest` touches real JFR, and only for the mapper). If
you change `EndpointTaggingFilter`, `JfrStreamService`, or
`SampleCorrelator`, verify manually:

```bash
mvn -pl demo-app -am package -DskipTests
java -jar demo-app/target/demo-app-0.1.0-SNAPSHOT.jar &
curl "http://localhost:8080/cpu/regex?iterations=30000"
sleep 2   # let the JFR stream flush
curl "http://localhost:8080/profiler/api/endpoints"
```

Check `samplesCorrelated` in the response is non-zero for endpoints that
were actually hit - `0` there is the symptom both bugs above produced, and
is the first thing to check if correlation seems broken again. `curl` the
`hotmethods` endpoint too and sanity-check the reported method actually
matches where the demo endpoint spends its time (e.g. `/cpu/regex` should
point at `Pattern.compile`/`Pattern.matcher`).

## Dashboard

`profiler-spring-boot-starter/src/main/resources/static/profiler/index.html`
loads `d3` and `d3-flame-graph` from a CDN (jsdelivr) - it needs outbound
internet to render the flame graph. The endpoint table and hot-methods
panel work without it (pure JSON fetch + DOM rendering), so don't assume a
blank flame graph means the backend is broken - check the browser console
for a blocked CDN request first.
