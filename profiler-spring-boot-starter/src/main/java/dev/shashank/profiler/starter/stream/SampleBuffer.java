package dev.shashank.profiler.starter.stream;

import dev.shashank.profiler.core.model.StackSample;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;

/**
 * Buffers {@code jdk.ExecutionSample} events per thread, keyed and sorted by
 * timestamp, until a matching {@code EndpointRequestEvent} claims the ones
 * that fall inside its request window. Because JFR streams events
 * asynchronously (roughly a one-second delay is typical), samples usually
 * sit here briefly before the request event that should collect them
 * arrives - and some never get claimed at all (background threads, requests
 * excluded from profiling), so unclaimed samples are evicted by age to keep
 * memory bounded.
 */
public final class SampleBuffer {

    private final Map<Long, NavigableMap<Long, StackSample>> byThread = new ConcurrentHashMap<>();

    public void add(StackSample sample) {
        byThread.computeIfAbsent(sample.threadId(), t -> new ConcurrentSkipListMap<>())
                .put(sample.timestampMillis(), sample);
    }

    /**
     * Removes and returns every buffered sample for {@code threadId} whose
     * timestamp falls within {@code [startMillis, endMillis]} inclusive.
     */
    public List<StackSample> consumeRange(long threadId, long startMillis, long endMillis) {
        NavigableMap<Long, StackSample> threadSamples = byThread.get(threadId);
        if (threadSamples == null) {
            return List.of();
        }
        NavigableMap<Long, StackSample> window = threadSamples.subMap(startMillis, true, endMillis, true);
        List<StackSample> result = new ArrayList<>(window.values());
        window.clear();
        return result;
    }

    /** Drops buffered samples older than {@code cutoffMillis} that no request ever claimed. */
    public void evictOlderThan(long cutoffMillis) {
        for (NavigableMap<Long, StackSample> threadSamples : byThread.values()) {
            threadSamples.headMap(cutoffMillis, false).clear();
        }
    }

    public int bufferedSampleCount() {
        return byThread.values().stream().mapToInt(NavigableMap::size).sum();
    }
}
