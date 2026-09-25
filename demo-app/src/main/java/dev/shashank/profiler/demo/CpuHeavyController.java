package dev.shashank.profiler.demo;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.regex.Pattern;

/**
 * Endpoints that are slow because they burn CPU doing real work
 * inefficiently - the profiler should point straight at
 * {@link #fibonacci} and {@link #matchRegex} as the hot methods.
 */
@RestController
public class CpuHeavyController {

    @GetMapping("/cpu/fibonacci")
    public String fibonacciEndpoint(@RequestParam(value = "n", defaultValue = "32") int n) {
        long result = fibonacci(n);
        return "fibonacci(" + n + ") = " + result;
    }

    // Deliberately naive exponential recursion instead of memoization.
    private long fibonacci(int n) {
        if (n <= 1) {
            return n;
        }
        return fibonacci(n - 1) + fibonacci(n - 2);
    }

    @GetMapping("/cpu/regex")
    public String regexEndpoint(@RequestParam(value = "iterations", defaultValue = "5000") int iterations) {
        String text = "order-42 shipped to zone-7 on 2026-01-15 via carrier-ACME with tracking XZ-1029384756";
        int matches = 0;
        for (int i = 0; i < iterations; i++) {
            matches += matchRegex(text) ? 1 : 0;
        }
        return "matched " + matches + "/" + iterations + " times";
    }

    // Deliberately re-compiling the pattern on every call instead of caching it.
    private boolean matchRegex(String text) {
        Pattern pattern = Pattern.compile("order-(\\d+).*zone-(\\d+).*carrier-(\\w+).*tracking\\s+([A-Z0-9-]+)");
        return pattern.matcher(text).find();
    }
}
