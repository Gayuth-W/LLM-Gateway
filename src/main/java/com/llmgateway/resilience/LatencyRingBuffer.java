package com.llmgateway.resilience;

import java.util.Arrays;

/**
 * Fixed-capacity circular buffer of recent latency samples (milliseconds) for one
 * model, used to compute p50/p95/p99 without unbounded memory growth.
 *
 * Data-structure / algorithm:
 *   - A ring buffer (array + write cursor) holds the last N samples in O(1) per write,
 *     overwriting the oldest sample once full.
 *   - Percentiles are computed by copying the live region and sorting it
 *     (O(k log k), k = number of samples), then nearest-rank indexing.
 *
 * Methods are synchronized: samples arrive from both the request event loop and the
 * scheduled health pinger.
 */
public final class LatencyRingBuffer {

    private final double[] buffer;
    private int writeIndex = 0;
    private int count = 0;

    public LatencyRingBuffer(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be > 0");
        }
        this.buffer = new double[capacity];
    }

    public synchronized void record(double latencyMs) {
        buffer[writeIndex] = latencyMs;
        writeIndex = (writeIndex + 1) % buffer.length;
        if (count < buffer.length) {
            count++;
        }
    }

    public synchronized long sampleCount() {
        return count;
    }

    /** Nearest-rank percentile (p in [0,100]). Returns 0 when empty. */
    public synchronized double percentile(double p) {
        if (count == 0) {
            return 0.0;
        }
        double[] snapshot = Arrays.copyOf(buffer, count);
        Arrays.sort(snapshot);
        int rank = (int) Math.ceil((p / 100.0) * snapshot.length);
        int index = Math.min(Math.max(rank - 1, 0), snapshot.length - 1);
        return snapshot[index];
    }

    public double p50() { return percentile(50); }
    public double p95() { return percentile(95); }
    public double p99() { return percentile(99); }
}
