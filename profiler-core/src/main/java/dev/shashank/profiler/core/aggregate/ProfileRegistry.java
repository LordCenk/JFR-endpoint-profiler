package dev.shashank.profiler.core.aggregate;

import dev.shashank.profiler.core.model.EndpointKey;
import dev.shashank.profiler.core.model.RequestRecord;
import dev.shashank.profiler.core.model.StackSample;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe registry of every endpoint seen since the profiler started,
 * keyed by {@link EndpointKey}. This is the single point where a completed
 * request and its correlated CPU samples are folded into that endpoint's
 * running {@link EndpointProfile}.
 */
public final class ProfileRegistry {

    private final Map<EndpointKey, EndpointProfile> profiles = new ConcurrentHashMap<>();

    public void record(RequestRecord request, List<StackSample> correlatedSamples) {
        EndpointProfile profile = profiles.computeIfAbsent(request.endpoint(), EndpointProfile::new);
        profile.recordRequest(request, correlatedSamples);
    }

    public EndpointProfile profile(EndpointKey key) {
        return profiles.get(key);
    }

    public Collection<EndpointProfile> allProfiles() {
        return List.copyOf(profiles.values());
    }

    /** Endpoints sorted by p95 latency, slowest first - the dashboard's default ordering. */
    public List<EndpointProfile> byP95Descending() {
        return profiles.values().stream()
                .sorted(Comparator.comparingLong((EndpointProfile p) -> p.latencyStats().p95Millis()).reversed())
                .toList();
    }

    public int endpointCount() {
        return profiles.size();
    }

    public void clear() {
        profiles.clear();
    }
}
