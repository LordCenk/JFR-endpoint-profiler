package dev.shashank.profiler.starter.capture;

import dev.shashank.profiler.starter.autoconfigure.ProfilerProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

import java.io.IOException;
import java.util.List;

/**
 * Brackets every HTTP request with an {@link EndpointRequestEvent}. This is
 * step one of the correlation design: JFR's {@code jdk.ExecutionSample}
 * knows the thread and the stack but nothing about HTTP, so this filter is
 * what ties a thread + time window back to "GET /orders/{id}".
 *
 * <p>The path is read from {@link HandlerMapping#BEST_MATCHING_PATTERN_ATTRIBUTE}
 * after the handler has run, so we get the route template
 * ({@code /orders/{id}}) rather than the raw URI ({@code /orders/123}) -
 * otherwise every distinct id would become its own "endpoint".
 */
public class EndpointTaggingFilter extends OncePerRequestFilter {

    private final ProfilerProperties properties;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public EndpointTaggingFilter(ProfilerProperties properties) {
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (isExcluded(request.getRequestURI())) {
            chain.doFilter(request, response);
            return;
        }

        EndpointRequestEvent event = new EndpointRequestEvent();
        if (!event.isEnabled()) {
            // JFR recording isn't active (or this event type is disabled) - skip the bookkeeping entirely.
            chain.doFilter(request, response);
            return;
        }

        event.begin();
        try {
            chain.doFilter(request, response);
        } finally {
            event.httpMethod = request.getMethod();
            event.pathPattern = resolvePathPattern(request);
            event.status = response.getStatus();
            event.commit();
        }
    }

    private boolean isExcluded(String requestUri) {
        List<String> excludePaths = properties.getExcludePaths();
        if (excludePaths == null) {
            return false;
        }
        for (String pattern : excludePaths) {
            if (pathMatcher.match(pattern, requestUri)) {
                return true;
            }
        }
        return false;
    }

    private static String resolvePathPattern(HttpServletRequest request) {
        Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        if (pattern instanceof String patternString && !patternString.isBlank()) {
            return patternString;
        }
        return request.getRequestURI();
    }
}
