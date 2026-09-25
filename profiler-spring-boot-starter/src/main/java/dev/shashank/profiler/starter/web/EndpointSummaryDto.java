package dev.shashank.profiler.starter.web;

import dev.shashank.profiler.core.aggregate.EndpointProfile;
import dev.shashank.profiler.core.aggregate.LatencyStats;

/** JSON view of one endpoint's latency profile for the dashboard table. */
public record EndpointSummaryDto(
        String httpMethod,
        String pathPattern,
        long count,
        long errorCount,
        double meanMillis,
        long p50Millis,
        long p95Millis,
        long p99Millis,
        long maxMillis,
        long samplesCorrelated) {

    public static EndpointSummaryDto from(EndpointProfile profile) {
        LatencyStats stats = profile.latencyStats();
        return new EndpointSummaryDto(
                profile.key().httpMethod(),
                profile.key().pathPattern(),
                stats.count(),
                profile.totalErrors(),
                round(stats.meanMillis()),
                stats.p50Millis(),
                stats.p95Millis(),
                stats.p99Millis(),
                stats.maxMillis(),
                profile.totalSamplesCorrelated());
    }

    private static double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
