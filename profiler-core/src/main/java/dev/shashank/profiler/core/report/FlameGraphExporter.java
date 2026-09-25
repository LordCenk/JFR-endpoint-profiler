package dev.shashank.profiler.core.report;

import dev.shashank.profiler.core.aggregate.CallTree;
import dev.shashank.profiler.core.aggregate.CallTreeNode;
import dev.shashank.profiler.core.model.Frame;

/**
 * Renders a {@link CallTree} as the nested {@code {name, value, children}}
 * JSON that d3-flame-graph consumes directly. Hand-rolled rather than
 * pulling in a JSON library, since profiler-core has no third-party
 * dependencies by design.
 */
public final class FlameGraphExporter {

    private FlameGraphExporter() {
    }

    public static String toJson(CallTree tree) {
        StringBuilder sb = new StringBuilder();
        writeNode(sb, tree.root(), CallTree.ROOT_FRAME.methodName());
        return sb.toString();
    }

    private static void writeNode(StringBuilder sb, CallTreeNode node, String displayName) {
        sb.append("{\"name\":");
        writeJsonString(sb, displayName);
        sb.append(",\"value\":").append(node.totalCount());
        sb.append(",\"selfValue\":").append(node.selfCount());
        var children = node.children();
        if (children.isEmpty()) {
            sb.append("}");
            return;
        }
        sb.append(",\"children\":[");
        boolean first = true;
        for (CallTreeNode child : children) {
            if (!first) {
                sb.append(",");
            }
            first = false;
            writeNode(sb, child, labelFor(child.frame()));
        }
        sb.append("]}");
    }

    private static String labelFor(Frame frame) {
        return frame.shortLabel();
    }

    private static void writeJsonString(StringBuilder sb, String value) {
        sb.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
    }
}
