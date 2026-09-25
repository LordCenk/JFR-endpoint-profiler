package dev.shashank.profiler.core.model;

/**
 * A single stack frame: the class and method being executed, and the line
 * number if known. Line may be {@code -1} when the source is unavailable.
 */
public record Frame(String className, String methodName, int line) {

    public String shortLabel() {
        int lastDot = className.lastIndexOf('.');
        String simpleClass = lastDot >= 0 ? className.substring(lastDot + 1) : className;
        return simpleClass + "." + methodName;
    }

    public String fullLabel() {
        return className + "." + methodName;
    }
}
