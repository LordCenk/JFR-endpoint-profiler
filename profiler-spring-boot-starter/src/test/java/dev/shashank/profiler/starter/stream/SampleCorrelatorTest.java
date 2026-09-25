package dev.shashank.profiler.starter.stream;

import dev.shashank.profiler.core.aggregate.ProfileRegistry;
import dev.shashank.profiler.core.model.EndpointKey;
import dev.shashank.profiler.core.model.Frame;
import dev.shashank.profiler.core.model.RequestRecord;
import dev.shashank.profiler.core.model.StackSample;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SampleCorrelatorTest {

    private static final EndpointKey ORDERS = EndpointKey.of("GET", "/orders/{id}");
    private static final Frame HOT_FRAME = new Frame("dev.shashank.demo.OrdersController", "get", 42);

    @Test
    void samplesWithinRequestWindowAreCorrelated() {
        SampleBuffer buffer = new SampleBuffer();
        ProfileRegistry registry = new ProfileRegistry();
        SampleCorrelator correlator = new SampleCorrelator(buffer, registry, Duration.ofSeconds(5));

        long threadId = 7L;
        correlator.onExecutionSample(new StackSample(threadId, 1000L, List.of(HOT_FRAME)));
        correlator.onExecutionSample(new StackSample(threadId, 1010L, List.of(HOT_FRAME)));
        // outside the request window - belongs to a different/earlier request
        correlator.onExecutionSample(new StackSample(threadId, 500L, List.of(HOT_FRAME)));

        RequestRecord request = new RequestRecord(threadId, 900L, 1050L, ORDERS, 200);
        correlator.onEndpointRequest(request);

        var profile = registry.profile(ORDERS);
        assertEquals(1, profile.totalRequests());
        assertEquals(2, profile.callTree().totalSamples(), "only the two in-window samples should be correlated");
    }

    @Test
    void samplesOnDifferentThreadAreNotCorrelated() {
        SampleBuffer buffer = new SampleBuffer();
        ProfileRegistry registry = new ProfileRegistry();
        SampleCorrelator correlator = new SampleCorrelator(buffer, registry, Duration.ofSeconds(5));

        correlator.onExecutionSample(new StackSample(99L, 1000L, List.of(HOT_FRAME)));

        RequestRecord request = new RequestRecord(7L, 900L, 1100L, ORDERS, 200);
        correlator.onEndpointRequest(request);

        assertEquals(0, registry.profile(ORDERS).callTree().totalSamples());
    }

    @Test
    void consumedSamplesAreNotReusedByALaterRequest() {
        SampleBuffer buffer = new SampleBuffer();
        ProfileRegistry registry = new ProfileRegistry();
        SampleCorrelator correlator = new SampleCorrelator(buffer, registry, Duration.ofSeconds(5));

        long threadId = 7L;
        correlator.onExecutionSample(new StackSample(threadId, 1000L, List.of(HOT_FRAME)));

        correlator.onEndpointRequest(new RequestRecord(threadId, 900L, 1100L, ORDERS, 200));
        correlator.onEndpointRequest(new RequestRecord(threadId, 900L, 1100L, ORDERS, 200));

        assertEquals(2, registry.profile(ORDERS).totalRequests());
        assertEquals(1, registry.profile(ORDERS).callTree().totalSamples(), "second request should get zero samples");
    }

    @Test
    void staleSamplesAreEvictedAndNoLongerCorrelate() {
        SampleBuffer buffer = new SampleBuffer();
        ProfileRegistry registry = new ProfileRegistry();
        Instant now = Instant.parse("2026-01-01T00:00:10Z");
        Clock clock = Clock.fixed(now, ZoneOffset.UTC);
        SampleCorrelator correlator = new SampleCorrelator(buffer, registry, Duration.ofSeconds(5), clock);

        long threadId = 7L;
        long oldTimestamp = now.minusSeconds(10).toEpochMilli();
        correlator.onExecutionSample(new StackSample(threadId, oldTimestamp, List.of(HOT_FRAME)));

        correlator.evictStaleSamples();

        RequestRecord request = new RequestRecord(threadId, oldTimestamp - 100, oldTimestamp + 100, ORDERS, 200);
        correlator.onEndpointRequest(request);

        assertTrue(registry.profile(ORDERS).callTree().totalSamples() == 0);
    }
}
