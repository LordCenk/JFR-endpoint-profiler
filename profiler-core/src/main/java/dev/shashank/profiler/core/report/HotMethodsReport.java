package dev.shashank.profiler.core.report;

import dev.shashank.profiler.core.aggregate.CallTree;
import dev.shashank.profiler.core.aggregate.CallTreeNode;
import dev.shashank.profiler.core.model.Frame;

import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Top-N methods by self time (samples where the method was the leaf, i.e.
 * actually running rather than waiting on a callee) across an entire call
 * tree. The same method can appear at several places in the tree - e.g. a
 * shared utility called from multiple handlers - so self counts are summed
 * across every occurrence before ranking.
 */
public final class HotMethodsReport {

    public record Entry(Frame frame, long selfCount, double percentOfTotal) {
    }

    private final List<Entry> entries;

    private HotMethodsReport(List<Entry> entries) {
        this.entries = entries;
    }

    public static HotMethodsReport topN(CallTree tree, int n) {
        Map<Frame, Long> selfByFrame = new HashMap<>();
        long totalSelf = 0;
        Deque<CallTreeNode> stack = new ArrayDeque<>();
        stack.push(tree.root());
        while (!stack.isEmpty()) {
            CallTreeNode node = stack.pop();
            long self = node.selfCount();
            if (self > 0) {
                selfByFrame.merge(node.frame(), self, Long::sum);
                totalSelf += self;
            }
            for (CallTreeNode child : node.children()) {
                stack.push(child);
            }
        }
        final long total = totalSelf;
        List<Entry> ranked = selfByFrame.entrySet().stream()
                .sorted(Map.Entry.<Frame, Long>comparingByValue().reversed())
                .limit(n)
                .map(e -> new Entry(e.getKey(), e.getValue(), total == 0 ? 0.0 : (100.0 * e.getValue() / total)))
                .toList();
        return new HotMethodsReport(ranked);
    }

    public List<Entry> entries() {
        return entries;
    }
}
