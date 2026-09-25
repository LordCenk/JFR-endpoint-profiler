package dev.shashank.profiler.core.aggregate;

import dev.shashank.profiler.core.model.Frame;
import dev.shashank.profiler.core.model.StackSample;

import java.util.List;

/**
 * Merges many {@link StackSample} stacks (each ordered leaf-first, as JFR
 * reports them) into a single tree rooted at a synthetic root frame. Walking
 * a stack root-to-leaf and incrementing {@code totalCount} along the path,
 * with {@code selfCount} only on the leaf, is the same "merge" step every
 * flame-graph / sampling profiler uses.
 */
public final class CallTree {

    public static final Frame ROOT_FRAME = new Frame("", "root", -1);

    private final CallTreeNode root = new CallTreeNode(ROOT_FRAME);

    public void merge(StackSample sample) {
        merge(sample.stack());
    }

    /**
     * @param stackLeafFirst frames ordered leaf (currently executing) first,
     *                       matching JFR's {@code RecordedStackTrace} order
     */
    public void merge(List<Frame> stackLeafFirst) {
        if (stackLeafFirst.isEmpty()) {
            return;
        }
        CallTreeNode node = root;
        root.markVisited();
        for (int i = stackLeafFirst.size() - 1; i >= 0; i--) {
            node = node.childFor(stackLeafFirst.get(i));
            node.markVisited();
        }
        node.markSelf();
    }

    public CallTreeNode root() {
        return root;
    }

    public long totalSamples() {
        return root.totalCount();
    }
}
