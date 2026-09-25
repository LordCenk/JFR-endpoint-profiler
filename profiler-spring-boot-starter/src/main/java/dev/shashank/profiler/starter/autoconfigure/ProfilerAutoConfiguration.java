package dev.shashank.profiler.starter.autoconfigure;

import dev.shashank.profiler.core.aggregate.ProfileRegistry;
import dev.shashank.profiler.starter.capture.EndpointTaggingFilter;
import dev.shashank.profiler.starter.stream.JfrStreamService;
import dev.shashank.profiler.starter.stream.SampleBuffer;
import dev.shashank.profiler.starter.stream.SampleCorrelator;
import dev.shashank.profiler.starter.web.ProfilerController;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;

/**
 * Wires the JFR endpoint profiler when {@code profiler.enabled=true}: the
 * request-tagging filter, the {@link ProfileRegistry} it feeds into via
 * JFR, the streaming/correlation pipeline, and the dashboard's REST API.
 * Everything is off by default so adding the starter as a dependency costs
 * nothing until explicitly enabled.
 */
@AutoConfiguration
@EnableConfigurationProperties(ProfilerProperties.class)
@ConditionalOnProperty(prefix = "profiler", name = "enabled", havingValue = "true")
public class ProfilerAutoConfiguration {

    @Bean
    public ProfileRegistry profileRegistry() {
        return new ProfileRegistry();
    }

    @Bean
    public SampleBuffer sampleBuffer() {
        return new SampleBuffer();
    }

    @Bean
    public SampleCorrelator sampleCorrelator(SampleBuffer sampleBuffer, ProfileRegistry registry, ProfilerProperties properties) {
        return new SampleCorrelator(sampleBuffer, registry, properties.getSampleRetention());
    }

    @Bean
    public JfrStreamService jfrStreamService(ProfilerProperties properties, SampleCorrelator correlator) {
        return new JfrStreamService(properties, correlator);
    }

    @Bean
    public FilterRegistrationBean<EndpointTaggingFilter> endpointTaggingFilter(ProfilerProperties properties) {
        FilterRegistrationBean<EndpointTaggingFilter> registration =
                new FilterRegistrationBean<>(new EndpointTaggingFilter(properties));
        registration.setName("endpointTaggingFilter");
        registration.addUrlPatterns("/*");
        registration.setOrder(Integer.MIN_VALUE + 10);
        return registration;
    }

    @Bean
    public ProfilerController profilerController(ProfileRegistry registry, ProfilerProperties properties) {
        return new ProfilerController(registry, properties);
    }
}
