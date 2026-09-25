package dev.shashank.profiler.starter.capture;

import jdk.jfr.Category;
import jdk.jfr.Description;
import jdk.jfr.Label;
import jdk.jfr.Name;
import jdk.jfr.StackTrace;
import jdk.jfr.Threshold;

/**
 * Custom JFR event committed once per HTTP request, bracketing the request
 * with {@code begin()}/{@code commit()}. This is the piece of the puzzle
 * that {@code jdk.ExecutionSample} can't give us on its own: it tells us
 * which thread and time window belong to which endpoint, so
 * {@code SampleCorrelator} can join CPU samples to the request that caused
 * them by thread id and timestamp.
 *
 * <p>{@code @StackTrace(false)} is deliberate - the stack for this event is
 * always just the filter chain, and we already get real call stacks from
 * {@code jdk.ExecutionSample}. Recording one here would be redundant cost.
 */
@Name("dev.shashank.profiler.EndpointRequest")
@Label("Endpoint Request")
@Category({"Profiler", "HTTP"})
@Description("One HTTP request handled while the JFR endpoint profiler was recording")
@StackTrace(false)
@Threshold("0 ms")
public class EndpointRequestEvent extends jdk.jfr.Event {

    @Label("HTTP Method")
    public String httpMethod;

    @Label("Path Pattern")
    @Description("The matched Spring MVC handler pattern, e.g. /orders/{id}, not the raw request URI")
    public String pathPattern;

    @Label("HTTP Status")
    public int status;
}
