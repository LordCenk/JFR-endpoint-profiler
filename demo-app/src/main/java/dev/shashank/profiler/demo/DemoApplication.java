package dev.shashank.profiler.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Demo Spring Boot app with deliberately slow endpoints, used to exercise
 * the JFR endpoint profiler starter end to end. Run it, hit the endpoints
 * below (or run {@code benchmarks/load-test.js}), then open
 * {@code http://localhost:8080/profiler/index.html}.
 */
@SpringBootApplication
public class DemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }
}
