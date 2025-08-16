/*
 * SPDX-FileCopyrightText: © 2025 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.fluxtion.server.service.metrics;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * Minimal metrics for event processing throughput and latency.
 * Thread-safe and allocation-free on the hot path.
 */
public final class EventProcessingMetrics {

    private final String name;
    private final long startTimeNanos;

    private final LongAdder processedCount = new LongAdder();
    private final LongAdder failedCount = new LongAdder();
    private final LongAdder totalProcessingTimeNanos = new LongAdder();
    private final AtomicLong lastLatencyNanos = new AtomicLong();
    private final AtomicBoolean enabled = new AtomicBoolean();

    EventProcessingMetrics(String name) {
        this.name = name;
        this.startTimeNanos = System.nanoTime();
    }

    public String getName() {
        return name;
    }

    /**
     * Record the latency for a single processed event in nanoseconds.
     */
    public void recordLatencyNanos(long nanos) {
        if (nanos < 0) {
            nanos = 0; // guard against clock anomalies
        }
        processedCount.increment();
        totalProcessingTimeNanos.add(nanos);
        lastLatencyNanos.lazySet(nanos);
    }

    /**
     * Record a processing failure.
     */
    public void recordFailure() {
        failedCount.increment();
    }

    public long getProcessedCount() {
        return processedCount.sum();
    }

    public long getFailedCount() {
        return failedCount.sum();
    }

    public long getTotalProcessingTimeNanos() {
        return totalProcessingTimeNanos.sum();
    }

    public long getLastLatencyNanos() {
        return lastLatencyNanos.get();
    }

    public boolean isEnabled() {
        return enabled.get();
    }

    /**
     * Average latency in nanoseconds across all processed events.
     */
    public long getAverageLatencyNanos() {
        long count = getProcessedCount();
        return count == 0 ? 0 : getTotalProcessingTimeNanos() / count;
    }

    /**
     * Throughput calculated as events per second since this metrics was created.
     */
    public double getThroughputEventsPerSecond() {
        long elapsedNanos = Math.max(1L, System.nanoTime() - startTimeNanos);
        double seconds = elapsedNanos / 1_000_000_000.0;
        return getProcessedCount() / seconds;
    }
}
