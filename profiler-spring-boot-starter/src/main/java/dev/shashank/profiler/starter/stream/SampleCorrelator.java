package dev.shashank.profiler.starter.stream;

import dev.shashank.profiler.core.aggregate.ProfileRegistry;
import dev.shashank.profiler.core.model.RequestRecord;
import dev.shashank.profiler.core.model.StackSample;

import java.time.Clock;
import java.time.Duration;

/**
 * Joins buffered CPU samples to completed requests by thread id and time
 * window - the core correlation step the whole design hinges on:
 * {@code jdk.ExecutionSample} tells us thread + stack, our own
 * {@code EndpointRequestEvent} tells us thread + time window + endpoint,
 * and this class is where the two get matched up.
 */
public final class SampleCorrelator {

    private final SampleBuffer sampleBuffer;
    private final ProfileRegistry registry;
    private final Duration sampleRetention;
    private final Clock clock;

    public SampleCorrelator(SampleBuffer sampleBuffer, ProfileRegistry registry, Duration sampleRetention) {
        this(sampleBuffer, registry, sampleRetention, Clock.systemUTC());
    }

    public SampleCorrelator(SampleBuffer sampleBuffer, ProfileRegistry registry, Duration sampleRetention, Clock clock) {
        this.sampleBuffer = sampleBuffer;
        this.registry = registry;
        this.sampleRetention = sampleRetention;
        this.clock = clock;
    }

    public void onExecutionSample(StackSample sample) {
        sampleBuffer.add(sample);
    }

    public void onEndpointRequest(RequestRecord request) {
        var samples = sampleBuffer.consumeRange(request.threadId(), request.startMillis(), request.endMillis());
        registry.record(request, samples);
    }

    /** Call periodically to bound memory used by samples no request ever claimed. */
    public void evictStaleSamples() {
        long cutoff = clock.millis() - sampleRetention.toMillis();
        sampleBuffer.evictOlderThan(cutoff);
    }
}
