package dev.shashank.profiler.core.aggregate;

import dev.shashank.profiler.core.model.EndpointKey;
import dev.shashank.profiler.core.model.Frame;
import dev.shashank.profiler.core.model.RequestRecord;
import dev.shashank.profiler.core.model.StackSample;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProfileRegistryTest {

    private static final EndpointKey SLOW = EndpointKey.of("GET", "/orders/{id}");
    private static final EndpointKey FAST = EndpointKey.of("GET", "/health");
    private static final Frame HOT_FRAME = new Frame("dev.shashank.demo.Controller", "slowWork", 42);

    @Test
    void recordAggregatesLatencyAndCallTreePerEndpoint() {
        ProfileRegistry registry = new ProfileRegistry();

        RequestRecord request = new RequestRecord(1L, 1000L, 1150L, SLOW, 200);
        StackSample sample = new StackSample(1L, 1050L, List.of(HOT_FRAME));
        registry.record(request, List.of(sample));

        EndpointProfile profile = registry.profile(SLOW);
        assertEquals(1, profile.totalRequests());
        assertEquals(150, profile.latencyStats().maxMillis());
        assertEquals(1, profile.callTree().totalSamples());
    }

    @Test
    void byP95DescendingSortsSlowestFirst() {
        ProfileRegistry registry = new ProfileRegistry();

        registry.record(new RequestRecord(1L, 0L, 500L, SLOW, 200), List.of());
        registry.record(new RequestRecord(2L, 0L, 5L, FAST, 200), List.of());

        List<EndpointProfile> sorted = registry.byP95Descending();
        assertEquals(2, sorted.size());
        assertEquals(SLOW, sorted.get(0).key());
        assertEquals(FAST, sorted.get(1).key());
    }

    @Test
    void multipleRequestsToSameEndpointAccumulate() {
        ProfileRegistry registry = new ProfileRegistry();

        for (int i = 0; i < 10; i++) {
            registry.record(new RequestRecord(1L, 0L, 100L + i, SLOW, 200), List.of());
        }

        EndpointProfile profile = registry.profile(SLOW);
        assertEquals(10, profile.totalRequests());
        assertEquals(1, registry.endpointCount());
    }

    @Test
    void errorStatusesAreCounted() {
        ProfileRegistry registry = new ProfileRegistry();
        registry.record(new RequestRecord(1L, 0L, 10L, SLOW, 500), List.of());
        registry.record(new RequestRecord(1L, 0L, 10L, SLOW, 200), List.of());

        assertEquals(1, registry.profile(SLOW).totalErrors());
        assertTrue(registry.profile(SLOW).totalRequests() == 2);
    }
}
