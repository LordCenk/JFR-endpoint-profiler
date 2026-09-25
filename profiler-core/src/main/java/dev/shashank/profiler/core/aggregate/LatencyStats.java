package dev.shashank.profiler.core.aggregate;

import java.util.List;

/**
 * Latency distribution for an endpoint, computed from a snapshot of observed
 * request durations. Percentiles use the nearest-rank method, which needs no
 * interpolation and is stable for the sample sizes a demo/dashboard sees.
 */
public record LatencyStats(long count, double meanMillis, long p50Millis, long p95Millis, long p99Millis, long maxMillis) {

    private static final LatencyStats EMPTY = new LatencyStats(0, 0, 0, 0, 0, 0);

    public static LatencyStats empty() {
        return EMPTY;
    }

    public static LatencyStats of(List<Long> durationsMillis) {
        if (durationsMillis.isEmpty()) {
            return EMPTY;
        }
        long[] sorted = durationsMillis.stream().mapToLong(Long::longValue).sorted().toArray();
        int n = sorted.length;
        long sum = 0;
        for (long d : sorted) {
            sum += d;
        }
        return new LatencyStats(
                n,
                (double) sum / n,
                percentile(sorted, 0.50),
                percentile(sorted, 0.95),
                percentile(sorted, 0.99),
                sorted[n - 1]);
    }

    private static long percentile(long[] sorted, double p) {
        int n = sorted.length;
        int rank = (int) Math.ceil(p * n);
        int index = Math.min(Math.max(rank - 1, 0), n - 1);
        return sorted[index];
    }
}
