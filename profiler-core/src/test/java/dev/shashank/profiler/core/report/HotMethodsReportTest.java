package dev.shashank.profiler.core.report;

import dev.shashank.profiler.core.aggregate.CallTree;
import dev.shashank.profiler.core.model.Frame;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HotMethodsReportTest {

    private static final Frame MAIN = new Frame("dev.shashank.demo.Controller", "handle", 10);
    private static final Frame REGEX_HOT = new Frame("dev.shashank.demo.Service", "matchRegex", 25);
    private static final Frame JSON_HOT = new Frame("dev.shashank.demo.Service", "buildJson", 30);

    @Test
    void ranksFramesBySelfTimeDescending() {
        CallTree tree = new CallTree();
        tree.merge(List.of(REGEX_HOT, MAIN));
        tree.merge(List.of(REGEX_HOT, MAIN));
        tree.merge(List.of(REGEX_HOT, MAIN));
        tree.merge(List.of(JSON_HOT, MAIN));

        HotMethodsReport report = HotMethodsReport.topN(tree, 5);

        assertEquals(2, report.entries().size());
        assertEquals(REGEX_HOT, report.entries().get(0).frame());
        assertEquals(3, report.entries().get(0).selfCount());
        assertEquals(75.0, report.entries().get(0).percentOfTotal(), 0.01);

        assertEquals(JSON_HOT, report.entries().get(1).frame());
        assertEquals(1, report.entries().get(1).selfCount());
    }

    @Test
    void sumsSelfTimeAcrossRepeatedOccurrencesOfSameFrame() {
        CallTree tree = new CallTree();
        // REGEX_HOT is the leaf under two different call paths
        tree.merge(List.of(REGEX_HOT, MAIN));
        tree.merge(List.of(REGEX_HOT, JSON_HOT, MAIN));

        HotMethodsReport report = HotMethodsReport.topN(tree, 5);

        assertEquals(1, report.entries().size(), "same leaf frame under different paths should be merged");
        assertEquals(2, report.entries().get(0).selfCount());
    }

    @Test
    void limitsToTopN() {
        CallTree tree = new CallTree();
        tree.merge(List.of(REGEX_HOT, MAIN));
        tree.merge(List.of(JSON_HOT, MAIN));

        HotMethodsReport report = HotMethodsReport.topN(tree, 1);
        assertEquals(1, report.entries().size());
    }
}
