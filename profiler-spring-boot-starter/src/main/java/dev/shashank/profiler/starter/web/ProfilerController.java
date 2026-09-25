package dev.shashank.profiler.starter.web;

import dev.shashank.profiler.core.aggregate.EndpointProfile;
import dev.shashank.profiler.core.aggregate.ProfileRegistry;
import dev.shashank.profiler.core.model.EndpointKey;
import dev.shashank.profiler.core.report.FlameGraphExporter;
import dev.shashank.profiler.core.report.HotMethodsReport;
import dev.shashank.profiler.starter.autoconfigure.ProfilerProperties;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Read-only JSON API backing the {@code /profiler/index.html} dashboard:
 * the endpoint table (sorted by p95) and, per endpoint, its flame graph and
 * hot-methods list.
 */
@RestController
@RequestMapping("/profiler/api")
public class ProfilerController {

    private final ProfileRegistry registry;
    private final ProfilerProperties properties;

    public ProfilerController(ProfileRegistry registry, ProfilerProperties properties) {
        this.registry = registry;
        this.properties = properties;
    }

    @GetMapping("/endpoints")
    public List<EndpointSummaryDto> endpoints() {
        return registry.byP95Descending().stream()
                .map(EndpointSummaryDto::from)
                .toList();
    }

    @GetMapping(value = "/endpoints/flamegraph", produces = MediaType.APPLICATION_JSON_VALUE)
    public String flameGraph(@RequestParam("method") String method, @RequestParam("pattern") String pattern) {
        return FlameGraphExporter.toJson(profileOrNotFound(method, pattern).callTree());
    }

    @GetMapping("/endpoints/hotmethods")
    public List<HotMethodDto> hotMethods(
            @RequestParam("method") String method,
            @RequestParam("pattern") String pattern,
            @RequestParam(value = "topN", required = false) Integer topN) {
        EndpointProfile profile = profileOrNotFound(method, pattern);
        int n = topN != null ? topN : properties.getHotMethodsPerEndpoint();
        return HotMethodsReport.topN(profile.callTree(), n).entries().stream()
                .map(HotMethodDto::from)
                .toList();
    }

    private EndpointProfile profileOrNotFound(String method, String pattern) {
        EndpointProfile profile = registry.profile(EndpointKey.of(method, pattern));
        if (profile == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No profile for " + method + " " + pattern);
        }
        return profile;
    }
}
