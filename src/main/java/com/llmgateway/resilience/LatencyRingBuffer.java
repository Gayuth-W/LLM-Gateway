package com.llmgateway.resilience;

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

}
