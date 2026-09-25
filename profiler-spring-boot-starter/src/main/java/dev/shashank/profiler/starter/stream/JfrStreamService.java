package dev.shashank.profiler.starter.stream;

import dev.shashank.profiler.starter.autoconfigure.ProfilerProperties;
import dev.shashank.profiler.starter.capture.EndpointRequestEvent;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jdk.jfr.consumer.RecordingStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Owns the {@link RecordingStream} lifecycle: enables {@code jdk.ExecutionSample}
 * at the configured period plus our custom {@link EndpointRequestEvent}, and
 * routes both into {@link SampleCorrelator}. Also drives periodic eviction
 * of samples that never got claimed by a request.
 */
public class JfrStreamService {

    private static final Logger log = LoggerFactory.getLogger(JfrStreamService.class);
    private static final Duration EVICTION_INTERVAL = Duration.ofSeconds(1);
    private static final Duration STREAM_MAX_AGE = Duration.ofSeconds(30);

    private final ProfilerProperties properties;
    private final SampleCorrelator correlator;

    private RecordingStream stream;
    private ScheduledExecutorService evictionExecutor;

    public JfrStreamService(ProfilerProperties properties, SampleCorrelator correlator) {
        this.properties = properties;
        this.correlator = correlator;
    }

    @PostConstruct
    public void start() {
        if (!properties.isEnabled()) {
            log.info("JFR endpoint profiler is disabled (profiler.enabled=false)");
            return;
        }

        stream = new RecordingStream();
        stream.setMaxAge(STREAM_MAX_AGE);
        stream.setStartTime(Instant.now());

        stream.enable(JfrMapper.EXECUTION_SAMPLE_EVENT).withPeriod(properties.getSamplePeriod());
        stream.enable(EndpointRequestEvent.class);

        stream.onEvent(JfrMapper.EXECUTION_SAMPLE_EVENT, event -> {
            try {
                correlator.onExecutionSample(JfrMapper.toStackSample(event));
            } catch (Exception e) {
                log.warn("Failed to process execution sample", e);
            }
        });
        stream.onEvent(JfrMapper.ENDPOINT_REQUEST_EVENT, event -> {
            try {
                correlator.onEndpointRequest(JfrMapper.toRequestRecord(event));
            } catch (Exception e) {
                log.warn("Failed to process endpoint request event", e);
            }
        });

        stream.startAsync();
        log.info("JFR endpoint profiler started (sample period={})", properties.getSamplePeriod());

        evictionExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "jfr-profiler-eviction");
            t.setDaemon(true);
            return t;
        });
        evictionExecutor.scheduleAtFixedRate(
                correlator::evictStaleSamples,
                EVICTION_INTERVAL.toMillis(),
                EVICTION_INTERVAL.toMillis(),
                TimeUnit.MILLISECONDS);
    }

    @PreDestroy
    public void stop() {
        if (evictionExecutor != null) {
            evictionExecutor.shutdownNow();
        }
        if (stream != null) {
            stream.close();
            log.info("JFR endpoint profiler stopped");
        }
    }

    public boolean isRunning() {
        return stream != null;
    }
}
