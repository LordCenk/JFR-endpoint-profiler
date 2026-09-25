package dev.shashank.profiler.core.aggregate;

import dev.shashank.profiler.core.model.EndpointKey;
import dev.shashank.profiler.core.model.RequestRecord;
import dev.shashank.profiler.core.model.StackSample;

import java.util.concurrent.atomic.LongAdder;

/**
 * Everything known about one endpoint: its latency distribution and the
 * merged call tree of every CPU sample correlated to a request against it.
 */
public final class EndpointProfile {

    private static final int DEFAULT_DURATION_WINDOW = 5_000;

    private final EndpointKey key;
    private final CallTree callTree = new CallTree();
    private final DurationWindow durations;
    private final LongAdder totalRequests = new LongAdder();
    private final LongAdder totalErrors = new LongAdder();
    private final LongAdder totalSamplesCorrelated = new LongAdder();

    public EndpointProfile(EndpointKey key) {
        this(key, DEFAULT_DURATION_WINDOW);
    }

    public EndpointProfile(EndpointKey key, int durationWindowCapacity) {
        this.key = key;
        this.durations = new DurationWindow(durationWindowCapacity);
    }

    public void recordRequest(RequestRecord request, Iterable<StackSample> correlatedSamples) {
        totalRequests.increment();
        if (request.isError()) {
            totalErrors.increment();
        }
        durations.record(request.durationMillis());
        for (StackSample sample : correlatedSamples) {
            callTree.merge(sample);
            totalSamplesCorrelated.increment();
        }
    }

    public EndpointKey key() {
        return key;
    }

    public CallTree callTree() {
        return callTree;
    }

    public LatencyStats latencyStats() {
        LatencyStats windowed = LatencyStats.of(durations.snapshot());
        if (windowed.count() == 0) {
            return windowed;
        }
        return new LatencyStats(
                totalRequests.sum(),
                windowed.meanMillis(),
                windowed.p50Millis(),
                windowed.p95Millis(),
                windowed.p99Millis(),
                windowed.maxMillis());
    }

    public long totalRequests() {
        return totalRequests.sum();
    }

    public long totalErrors() {
        return totalErrors.sum();
    }

    public long totalSamplesCorrelated() {
        return totalSamplesCorrelated.sum();
    }
}
