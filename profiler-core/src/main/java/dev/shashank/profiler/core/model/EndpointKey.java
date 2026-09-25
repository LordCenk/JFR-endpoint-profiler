package dev.shashank.profiler.core.model;

/**
 * Identifies an endpoint by HTTP method and route pattern, e.g.
 * {@code GET /orders/{id}}. Using the Spring MVC matched pattern rather than
 * the raw request URI keeps the key space bounded regardless of how many
 * distinct path variable values are seen.
 */
public record EndpointKey(String httpMethod, String pathPattern) {

    public EndpointKey {
        if (httpMethod == null || httpMethod.isBlank()) {
            throw new IllegalArgumentException("httpMethod must not be blank");
        }
        if (pathPattern == null || pathPattern.isBlank()) {
            throw new IllegalArgumentException("pathPattern must not be blank");
        }
        httpMethod = httpMethod.toUpperCase();
    }

    public static EndpointKey of(String httpMethod, String pathPattern) {
        return new EndpointKey(httpMethod, pathPattern);
    }

    public String display() {
        return httpMethod + " " + pathPattern;
    }

    @Override
    public String toString() {
        return display();
    }
}
