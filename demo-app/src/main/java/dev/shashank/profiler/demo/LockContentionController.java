package dev.shashank.profiler.demo;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoint that is slow under concurrent load because every request
 * serializes through one coarse-grained lock, doing real CPU work inside
 * the critical section. Under a concurrent load test this endpoint's p95
 * should visibly diverge from its p50 - the queueing delay of a shared
 * lock, not raw per-request cost.
 */
@RestController
public class LockContentionController {

    private final Object sharedLock = new Object();
    private long counter = 0;
    private long checksum = 0;

    @GetMapping("/lock/increment")
    public String incrementEndpoint(@RequestParam(value = "work", defaultValue = "200000") int work) {
        long result;
        synchronized (sharedLock) {
            result = contendedIncrement(work);
        }
        return "counter=" + result;
    }

    // Deliberately does non-trivial CPU work while holding the lock, instead
    // of the minimal work an increment actually needs, so contention shows up
    // as real time spent inside this method rather than idle waiting.
    private long contendedIncrement(int work) {
        counter++;
        long localChecksum = 0;
        for (int i = 0; i < work; i++) {
            localChecksum = (localChecksum * 31 + i) % 1_000_000_007L;
        }
        checksum += localChecksum;
        return counter;
    }
}
