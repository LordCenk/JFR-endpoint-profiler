package dev.shashank.profiler.core.aggregate;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LatencyStatsTest {

    @Test
    void emptyInputProducesEmptyStats() {
        LatencyStats stats = LatencyStats.of(List.of());
        assertEquals(0, stats.count());
        assertEquals(0, stats.p50Millis());
        assertEquals(0, stats.maxMillis());
    }

    @Test
    void percentilesUseNearestRankOnSortedInput() {
        // 1..100 ms, nearest-rank: p50 -> index ceil(0.50*100)-1 = 49 -> value 50
        List<Long> durations = new ArrayList<>();
        for (long i = 1; i <= 100; i++) {
            durations.add(i);
        }
        LatencyStats stats = LatencyStats.of(durations);

        assertEquals(100, stats.count());
        assertEquals(50, stats.p50Millis());
        assertEquals(95, stats.p95Millis());
        assertEquals(99, stats.p99Millis());
        assertEquals(100, stats.maxMillis());
        assertEquals(50.5, stats.meanMillis(), 0.0001);
    }

    @Test
    void singleValueIsReturnedForEveryPercentile() {
        LatencyStats stats = LatencyStats.of(List.of(42L));
        assertEquals(1, stats.count());
        assertEquals(42, stats.p50Millis());
        assertEquals(42, stats.p95Millis());
        assertEquals(42, stats.p99Millis());
        assertEquals(42, stats.maxMillis());
    }

    @Test
    void inputOrderDoesNotMatter() {
        LatencyStats shuffled = LatencyStats.of(List.of(30L, 10L, 20L, 50L, 40L));
        assertEquals(5, shuffled.count());
        assertEquals(30, shuffled.p50Millis());
        assertEquals(50, shuffled.maxMillis());
    }
}
