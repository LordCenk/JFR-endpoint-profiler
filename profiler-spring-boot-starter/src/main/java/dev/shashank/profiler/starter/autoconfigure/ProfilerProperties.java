package dev.shashank.profiler.starter.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

/**
 * Configuration for the JFR endpoint profiler, bound from
 * {@code profiler.*} properties.
 */
@ConfigurationProperties(prefix = "profiler")
public class ProfilerProperties {

    /** Master switch. When false, no filter, no JFR stream, near-zero overhead. */
    private boolean enabled = false;

    /** Period between {@code jdk.ExecutionSample} events, e.g. "10ms". */
    private Duration samplePeriod = Duration.ofMillis(10);

    /** Request paths never tagged or profiled, e.g. the dashboard itself. */
    private List<String> excludePaths = List.of("/profiler/**", "/actuator/**");

    /** How long correlated CPU samples are kept per-thread before eviction, if never claimed by a request. */
    private Duration sampleRetention = Duration.ofSeconds(5);

    /** Max requests worth of latency history kept per endpoint, bounding memory. */
    private int latencyWindowSize = 5_000;

    /** Max distinct method frames reported as "hot methods" per endpoint. */
    private int hotMethodsPerEndpoint = 10;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Duration getSamplePeriod() {
        return samplePeriod;
    }

    public void setSamplePeriod(Duration samplePeriod) {
        this.samplePeriod = samplePeriod;
    }

    public List<String> getExcludePaths() {
        return excludePaths;
    }

    public void setExcludePaths(List<String> excludePaths) {
        this.excludePaths = excludePaths;
    }

    public Duration getSampleRetention() {
        return sampleRetention;
    }

    public void setSampleRetention(Duration sampleRetention) {
        this.sampleRetention = sampleRetention;
    }

    public int getLatencyWindowSize() {
        return latencyWindowSize;
    }

    public void setLatencyWindowSize(int latencyWindowSize) {
        this.latencyWindowSize = latencyWindowSize;
    }

    public int getHotMethodsPerEndpoint() {
        return hotMethodsPerEndpoint;
    }

    public void setHotMethodsPerEndpoint(int hotMethodsPerEndpoint) {
        this.hotMethodsPerEndpoint = hotMethodsPerEndpoint;
    }
}
