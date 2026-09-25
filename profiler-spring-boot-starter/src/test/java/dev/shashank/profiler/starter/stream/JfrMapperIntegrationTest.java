package dev.shashank.profiler.starter.stream;

import dev.shashank.profiler.core.model.StackSample;
import jdk.jfr.consumer.RecordingStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises {@link JfrMapper#toStackSample} against a real
 * {@code jdk.ExecutionSample} event from a live {@link RecordingStream}.
 *
 * <p>This guards against a specific regression: {@code jdk.ExecutionSample}
 * names its thread field {@code sampledThread}, not the conventional
 * {@code eventThread} that {@code RecordedEvent.getThread()} looks up by
 * default. Calling the no-arg {@code getThread()} silently returns null,
 * which meant every {@link StackSample} came back with thread id -1 and
 * could never be correlated to a request - a bug hand-built samples in
 * {@code SampleCorrelatorTest} can't catch, since they construct
 * {@link StackSample} directly rather than going through the mapper.
 */
class JfrMapperIntegrationTest {

    @Test
    @Timeout(30)
    void mapsRealExecutionSampleWithCorrectThreadId() throws InterruptedException {
        long expectedThreadId = Thread.currentThread().getId();
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<StackSample> captured = new AtomicReference<>();

        try (RecordingStream stream = new RecordingStream()) {
            stream.enable(JfrMapper.EXECUTION_SAMPLE_EVENT).withPeriod(Duration.ofMillis(5));
            stream.onEvent(JfrMapper.EXECUTION_SAMPLE_EVENT, event -> {
                if (captured.get() == null) {
                    captured.set(JfrMapper.toStackSample(event));
                    latch.countDown();
                }
            });
            stream.startAsync();

            // Burn CPU on this thread until a sample lands or we time out.
            long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
            long busy = 0;
            while (latch.getCount() > 0 && System.nanoTime() < deadline) {
                busy = busy * 31 + 7;
                if (busy % 1_000_000_000L == 0) {
                    Thread.onSpinWait();
                }
            }
            boolean sampled = latch.await(1, TimeUnit.SECONDS);
            assertTrue(sampled, "expected at least one jdk.ExecutionSample within the deadline");
        }

        StackSample sample = captured.get();
        assertEquals(expectedThreadId, sample.threadId(), "sample thread id must match the sampled thread, not -1");
        assertFalse(sample.isEmpty(), "a real execution sample should carry a non-empty stack");
    }
}
