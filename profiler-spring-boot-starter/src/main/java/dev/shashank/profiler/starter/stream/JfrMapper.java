package dev.shashank.profiler.starter.stream;

import dev.shashank.profiler.core.model.EndpointKey;
import dev.shashank.profiler.core.model.Frame;
import dev.shashank.profiler.core.model.RequestRecord;
import dev.shashank.profiler.core.model.StackSample;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedFrame;
import jdk.jfr.consumer.RecordedStackTrace;

import java.util.List;

/**
 * Translates raw JFR {@link RecordedEvent}s into the JDK/JFR-free model
 * types from profiler-core. This is the only place in the starter module
 * that should know both the JFR API and the core model shapes.
 */
public final class JfrMapper {

    public static final String EXECUTION_SAMPLE_EVENT = "jdk.ExecutionSample";
    public static final String ENDPOINT_REQUEST_EVENT = "dev.shashank.profiler.EndpointRequest";

    private JfrMapper() {
    }

    /** Maps a {@code jdk.ExecutionSample} event to a {@link StackSample}. */
    public static StackSample toStackSample(RecordedEvent event) {
        // jdk.ExecutionSample names its thread field "sampledThread", not the
        // conventional "eventThread" that RecordedEvent.getThread() looks up -
        // so the sampled thread must be fetched by name or every sample comes
        // back with a null thread and can never be correlated to a request.
        var thread = event.getThread("sampledThread");
        long threadId = thread != null ? thread.getJavaThreadId() : -1;
        long timestampMillis = event.getStartTime().toEpochMilli();
        List<Frame> frames = toFrames(event.getStackTrace());
        return new StackSample(threadId, timestampMillis, frames);
    }

    private static List<Frame> toFrames(RecordedStackTrace stackTrace) {
        if (stackTrace == null) {
            return List.of();
        }
        List<RecordedFrame> jfrFrames = stackTrace.getFrames();
        return jfrFrames.stream()
                .map(JfrMapper::toFrame)
                .toList();
    }

    private static Frame toFrame(RecordedFrame jfrFrame) {
        var method = jfrFrame.getMethod();
        String className = method.getType() != null ? method.getType().getName() : "<unknown>";
        String methodName = method.getName() != null ? method.getName() : "<unknown>";
        int line = jfrFrame.getLineNumber();
        return new Frame(className, methodName, line);
    }

    /** Maps our custom {@code EndpointRequestEvent} to a {@link RequestRecord}. */
    public static RequestRecord toRequestRecord(RecordedEvent event) {
        long threadId = event.getThread() != null ? event.getThread().getJavaThreadId() : -1;
        long startMillis = event.getStartTime().toEpochMilli();
        long endMillis = event.getEndTime().toEpochMilli();
        String httpMethod = event.getString("httpMethod");
        String pathPattern = event.getString("pathPattern");
        int status = event.getInt("status");
        EndpointKey key = EndpointKey.of(httpMethod, pathPattern);
        return new RequestRecord(threadId, startMillis, endMillis, key, status);
    }
}
