package dev.shashank.profiler.core.aggregate;

import dev.shashank.profiler.core.model.Frame;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * One node in a merged call tree. {@code totalCount} is the number of
 * samples that passed through this frame (this frame or one of its
 * descendants was on the stack); {@code selfCount} is the number of samples
 * where this frame was the leaf, i.e. actually executing rather than merely
 * waiting on a callee. Thread-safe: multiple threads may merge stacks into
 * the same tree concurrently.
 */
public final class CallTreeNode {

    private final Frame frame;
    private final LongAdder selfCount = new LongAdder();
    private final LongAdder totalCount = new LongAdder();
    private final ConcurrentHashMap<Frame, CallTreeNode> children = new ConcurrentHashMap<>();

    public CallTreeNode(Frame frame) {
        this.frame = frame;
    }

    public Frame frame() {
        return frame;
    }

    public long selfCount() {
        return selfCount.sum();
    }

    public long totalCount() {
        return totalCount.sum();
    }

    public List<CallTreeNode> children() {
        return children.values().stream()
                .sorted(Comparator.comparingLong(CallTreeNode::totalCount).reversed())
                .toList();
    }

    boolean hasChildren() {
        return !children.isEmpty();
    }

    Map<Frame, CallTreeNode> childMap() {
        return children;
    }

    void markVisited() {
        totalCount.increment();
    }

    void markSelf() {
        selfCount.increment();
    }

    CallTreeNode childFor(Frame childFrame) {
        return children.computeIfAbsent(childFrame, CallTreeNode::new);
    }
}
