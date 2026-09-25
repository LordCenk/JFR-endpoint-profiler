package dev.shashank.profiler.core.aggregate;

import java.util.ArrayList;
import java.util.List;

/**
 * Fixed-capacity, thread-safe ring buffer of the most recent request
 * durations for one endpoint. Bounding memory here (rather than keeping
 * every duration ever observed) is what keeps a long-running profiled
 * process from growing without limit.
 */
final class DurationWindow {

    private final long[] buffer;
    private int writeIndex = 0;
    private long size = 0;

    DurationWindow(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        this.buffer = new long[capacity];
    }

    synchronized void record(long durationMillis) {
        buffer[writeIndex] = durationMillis;
        writeIndex = (writeIndex + 1) % buffer.length;
        size = Math.min(size + 1, buffer.length);
    }

    synchronized List<Long> snapshot() {
        List<Long> result = new ArrayList<>((int) size);
        for (int i = 0; i < size; i++) {
            result.add(buffer[i]);
        }
        return result;
    }

    synchronized long totalObserved() {
        return size;
    }
}
