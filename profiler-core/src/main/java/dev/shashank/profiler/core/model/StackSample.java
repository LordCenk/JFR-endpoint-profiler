package dev.shashank.profiler.core.model;

import java.util.List;

/**
 * One CPU sample taken by JFR's {@code jdk.ExecutionSample} event: the thread
 * it was taken on, the instant it was taken, and the call stack at that
 * instant, ordered leaf (top of stack) first.
 */
public record StackSample(long threadId, long timestampMillis, List<Frame> stack) {

    public StackSample {
        stack = List.copyOf(stack);
    }

    public boolean isEmpty() {
        return stack.isEmpty();
    }
}
