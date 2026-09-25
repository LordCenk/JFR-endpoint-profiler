package dev.shashank.profiler.core.model;

/**
 * A completed HTTP request, as captured by the servlet filter: which thread
 * served it, when it started and ended, which endpoint it matched, and the
 * response status. This is the anchor used to pull CPU samples for the
 * matching {@code [start, end]} window on {@link #threadId()}.
 */
public record RequestRecord(
        long threadId,
        long startMillis,
        long endMillis,
        EndpointKey endpoint,
        int status) {

    public RequestRecord {
        if (endMillis < startMillis) {
            throw new IllegalArgumentException("endMillis must not precede startMillis");
        }
    }

    public long durationMillis() {
        return endMillis - startMillis;
    }

    public boolean contains(long timestampMillis) {
        return timestampMillis >= startMillis && timestampMillis <= endMillis;
    }

    public boolean isError() {
        return status >= 400;
    }
}
