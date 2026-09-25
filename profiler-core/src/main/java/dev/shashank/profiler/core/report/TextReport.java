package dev.shashank.profiler.core.report;

import dev.shashank.profiler.core.aggregate.EndpointProfile;
import dev.shashank.profiler.core.aggregate.LatencyStats;
import dev.shashank.profiler.core.aggregate.ProfileRegistry;

/**
 * Plain-text summary of a {@link ProfileRegistry}: endpoints sorted by p95
 * latency with their hottest methods underneath. Useful for a console demo
 * or a log line, without needing the web dashboard.
 */
public final class TextReport {

    private static final int HOT_METHODS_PER_ENDPOINT = 5;

    private TextReport() {
    }

    public static String render(ProfileRegistry registry) {
        StringBuilder sb = new StringBuilder();
        var profiles = registry.byP95Descending();
        if (profiles.isEmpty()) {
            return "No requests profiled yet.\n";
        }
        sb.append(String.format("%-32s %8s %8s %8s %8s %8s%n",
                "ENDPOINT", "COUNT", "p50 ms", "p95 ms", "p99 ms", "max ms"));
        sb.append("-".repeat(80)).append(System.lineSeparator());
        for (EndpointProfile profile : profiles) {
            LatencyStats stats = profile.latencyStats();
            sb.append(String.format("%-32s %8d %8d %8d %8d %8d%n",
                    profile.key().display(), stats.count(), stats.p50Millis(),
                    stats.p95Millis(), stats.p99Millis(), stats.maxMillis()));
            HotMethodsReport hot = HotMethodsReport.topN(profile.callTree(), HOT_METHODS_PER_ENDPOINT);
            for (HotMethodsReport.Entry entry : hot.entries()) {
                sb.append(String.format("    %6.1f%%  %s%n", entry.percentOfTotal(), entry.frame().fullLabel()));
            }
        }
        return sb.toString();
    }
}
