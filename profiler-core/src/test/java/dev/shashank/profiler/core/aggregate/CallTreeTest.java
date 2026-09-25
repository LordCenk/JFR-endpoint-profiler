package dev.shashank.profiler.core.aggregate;

import dev.shashank.profiler.core.model.Frame;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CallTreeTest {

    private static final Frame MAIN = new Frame("dev.shashank.demo.Controller", "handle", 10);
    private static final Frame SERVICE = new Frame("dev.shashank.demo.Service", "process", 20);
    private static final Frame REGEX_HOT = new Frame("dev.shashank.demo.Service", "matchRegex", 25);
    private static final Frame JSON_HOT = new Frame("dev.shashank.demo.Service", "buildJson", 30);

    @Test
    void mergeSingleStackProducesRootToLeafChain() {
        CallTree tree = new CallTree();
        // JFR order is leaf-first: [REGEX_HOT, SERVICE, MAIN]
        tree.merge(List.of(REGEX_HOT, SERVICE, MAIN));

        CallTreeNode root = tree.root();
        assertEquals(1, root.totalCount());
        assertEquals(0, root.selfCount());

        CallTreeNode mainNode = child(root, MAIN);
        assertEquals(1, mainNode.totalCount());
        assertEquals(0, mainNode.selfCount());

        CallTreeNode serviceNode = child(mainNode, SERVICE);
        assertEquals(1, serviceNode.totalCount());
        assertEquals(0, serviceNode.selfCount());

        CallTreeNode leafNode = child(serviceNode, REGEX_HOT);
        assertEquals(1, leafNode.totalCount());
        assertEquals(1, leafNode.selfCount(), "leaf frame should record self time");
    }

    @Test
    void mergeSharesCommonPrefixesAndSumsCounts() {
        CallTree tree = new CallTree();
        tree.merge(List.of(REGEX_HOT, SERVICE, MAIN));
        tree.merge(List.of(REGEX_HOT, SERVICE, MAIN));
        tree.merge(List.of(JSON_HOT, SERVICE, MAIN));

        CallTreeNode root = tree.root();
        assertEquals(3, root.totalCount());

        CallTreeNode mainNode = child(root, MAIN);
        assertEquals(3, mainNode.totalCount());

        CallTreeNode serviceNode = child(mainNode, SERVICE);
        assertEquals(3, serviceNode.totalCount(), "both branches share the SERVICE frame");
        assertEquals(2, serviceNode.children().size(), "SERVICE should fan out to two distinct leaves");

        CallTreeNode regexNode = child(serviceNode, REGEX_HOT);
        assertEquals(2, regexNode.totalCount());
        assertEquals(2, regexNode.selfCount());

        CallTreeNode jsonNode = child(serviceNode, JSON_HOT);
        assertEquals(1, jsonNode.totalCount());
        assertEquals(1, jsonNode.selfCount());
    }

    @Test
    void emptyStackIsIgnored() {
        CallTree tree = new CallTree();
        tree.merge(List.of());
        assertEquals(0, tree.root().totalCount());
        assertTrue(tree.root().children().isEmpty());
    }

    private static CallTreeNode child(CallTreeNode parent, Frame frame) {
        return parent.children().stream()
                .filter(n -> n.frame().equals(frame))
                .findFirst()
                .orElseThrow(() -> new AssertionError("expected child frame " + frame + " under " + parent.frame()));
    }
}
