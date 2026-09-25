package dev.shashank.profiler.core.report;

import dev.shashank.profiler.core.aggregate.CallTree;
import dev.shashank.profiler.core.model.Frame;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class FlameGraphExporterTest {

    private static final Frame MAIN = new Frame("dev.shashank.demo.Controller", "handle", 10);
    private static final Frame REGEX_HOT = new Frame("dev.shashank.demo.Service", "matchRegex", 25);

    @Test
    void exportsRootWithNestedChildren() {
        CallTree tree = new CallTree();
        tree.merge(List.of(REGEX_HOT, MAIN));
        tree.merge(List.of(REGEX_HOT, MAIN));

        String json = FlameGraphExporter.toJson(tree);

        assertTrue(json.startsWith("{\"name\":\"root\",\"value\":2"), json);
        assertTrue(json.contains("\"name\":\"Controller.handle\""), json);
        assertTrue(json.contains("\"name\":\"Service.matchRegex\""), json);
        assertTrue(json.contains("\"selfValue\":2"), json);
        assertTrue(json.endsWith("]}]}"), json);
    }

    @Test
    void leafNodeHasNoChildrenKey() {
        CallTree tree = new CallTree();
        tree.merge(List.of(REGEX_HOT));

        String json = FlameGraphExporter.toJson(tree);
        assertTrue(json.contains("\"name\":\"Service.matchRegex\""));
        assertTrue(json.contains("\"selfValue\":1"));
    }

    @Test
    void escapesSpecialCharactersInNames() {
        Frame weird = new Frame("dev.shashank.Foo\"Bar", "m", 1);
        CallTree tree = new CallTree();
        tree.merge(List.of(weird));

        String json = FlameGraphExporter.toJson(tree);
        assertTrue(json.contains("Foo\\\"Bar"), json);
    }
}
