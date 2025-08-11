/*
 * SPDX-FileCopyrightText: © 2025 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.fluxtion.server.benchmark;

import com.fluxtion.runtime.EventProcessor;
import com.fluxtion.runtime.StaticEventProcessor;
import com.fluxtion.runtime.audit.LogRecord;
import com.fluxtion.runtime.audit.LogRecordListener;
import com.fluxtion.runtime.input.EventFeed;
import com.fluxtion.server.FluxtionServer;
import com.fluxtion.server.config.AppConfig;
import com.fluxtion.server.dispatch.CallBackType;
import com.fluxtion.server.dispatch.EventSourceKey;
import com.fluxtion.server.dispatch.EventSubscriptionKey;
import com.fluxtion.server.service.AbstractEventSourceService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.LongSummaryStatistics;
import java.util.concurrent.CountDownLatch;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Performance benchmark for event processing in Fluxtion Server.
 * This benchmark measures throughput and latency for event processing.
 */
public class EventProcessingBenchmark {

    private FluxtionServer server;
    private TestEventSource eventSource;
    private TestEventProcessor eventProcessor;
    private TestLogRecordListener logRecordListener;
    private CountDownLatch eventProcessedLatch;

    // Benchmark parameters
    private static final int WARMUP_COUNT = 10_000;
    private static final int BENCHMARK_COUNT = 100_000;
    private static final int BATCH_SIZE = 100;

    @BeforeEach
    void setUp() {
        // Create a log record listener to capture log events
        logRecordListener = new TestLogRecordListener();

        // Create a minimal app config
        AppConfig appConfig = new AppConfig();

        // Create an event processor
        eventProcessor = new TestEventProcessor();
        appConfig.addProcessor(eventProcessor, "testHandler");

        // Create an event source
        eventSource = new TestEventSource("testSource");
        appConfig.addEventSource(eventSource, "testEventSourceFeed", true);

        // Create the server
        server = FluxtionServer.bootServer(appConfig, logRecordListener);
    }

    @AfterEach
    void tearDown() {
        // Clean up resources
        if (server != null) {
            server.stop();
        }
    }

    @Test
    void benchmarkSingleEventProcessing() throws Exception {
        System.out.println("Running single event processing benchmark");

        // Warm up
        System.out.println("Warming up with " + WARMUP_COUNT + " events");
        for (int i = 0; i < WARMUP_COUNT; i++) {
            TestEvent event = new TestEvent("Warmup " + i);
            eventSource.publishEvent(event);
        }

        // Benchmark
        System.out.println("Running benchmark with " + BENCHMARK_COUNT + " events");
        List<Long> latencies = new ArrayList<>(BENCHMARK_COUNT);

        for (int i = 0; i < BENCHMARK_COUNT; i++) {
            TestEvent event = new TestEvent("Benchmark " + i);
            long startTime = System.nanoTime();
            eventSource.publishEvent(event);
            long endTime = System.nanoTime();
            latencies.add(endTime - startTime);

            // Add a small delay to avoid overwhelming the system
            if (i % 1000 == 0) {
                Thread.sleep(10);
            }
        }

        // Calculate statistics
        LongSummaryStatistics stats = latencies.stream().collect(Collectors.summarizingLong(Long::longValue));
        double avgLatencyMicros = stats.getAverage() / 1000.0;
        double throughputPerSecond = 1_000_000_000.0 / stats.getAverage();

        System.out.println("Single Event Processing Results:");
        System.out.println("Total events: " + BENCHMARK_COUNT);
        System.out.println("Min latency: " + stats.getMin() / 1000.0 + " µs");
        System.out.println("Max latency: " + stats.getMax() / 1000.0 + " µs");
        System.out.println("Avg latency: " + avgLatencyMicros + " µs");
        System.out.println("Throughput: " + String.format("%.2f", throughputPerSecond) + " events/second");

        // Verify that the benchmark completed successfully
        assertTrue(eventProcessor.getProcessedCount() >= BENCHMARK_COUNT,
                "Not all events were processed");
    }

    @Test
    void benchmarkBatchEventProcessing() throws Exception {
        System.out.println("Running batch event processing benchmark");

        // Warm up
        System.out.println("Warming up with " + WARMUP_COUNT + " events in batches of " + BATCH_SIZE);
        for (int i = 0; i < WARMUP_COUNT / BATCH_SIZE; i++) {
            final int batchIndex = i;
            List<TestEvent> batch = IntStream.range(0, BATCH_SIZE)
                    .mapToObj(j -> new TestEvent("Warmup " + (batchIndex * BATCH_SIZE + j)))
                    .collect(Collectors.toList());

            for (TestEvent event : batch) {
                eventSource.publishEvent(event);
            }
        }

        // Benchmark
        System.out.println("Running benchmark with " + BENCHMARK_COUNT + " events in batches of " + BATCH_SIZE);
        List<Long> batchLatencies = new ArrayList<>(BENCHMARK_COUNT / BATCH_SIZE);

        for (int i = 0; i < BENCHMARK_COUNT / BATCH_SIZE; i++) {
            final int batchIndex = i;
            List<TestEvent> batch = IntStream.range(0, BATCH_SIZE)
                    .mapToObj(j -> new TestEvent("Benchmark " + (batchIndex * BATCH_SIZE + j)))
                    .collect(Collectors.toList());

            long startTime = System.nanoTime();
            for (TestEvent event : batch) {
                eventSource.publishEvent(event);
            }
            long endTime = System.nanoTime();
            batchLatencies.add(endTime - startTime);

            // Add a small delay between batches
            if (i % 10 == 0) {
                Thread.sleep(10);
            }
        }

        // Calculate statistics
        LongSummaryStatistics batchStats = batchLatencies.stream().collect(Collectors.summarizingLong(Long::longValue));
        double avgBatchLatencyMicros = batchStats.getAverage() / 1000.0;
        double avgEventLatencyMicros = avgBatchLatencyMicros / BATCH_SIZE;
        double batchThroughputPerSecond = 1_000_000_000.0 / batchStats.getAverage();
        double eventThroughputPerSecond = batchThroughputPerSecond * BATCH_SIZE;

        System.out.println("Batch Event Processing Results:");
        System.out.println("Total batches: " + (BENCHMARK_COUNT / BATCH_SIZE));
        System.out.println("Total events: " + BENCHMARK_COUNT);
        System.out.println("Min batch latency: " + batchStats.getMin() / 1000.0 + " µs");
        System.out.println("Max batch latency: " + batchStats.getMax() / 1000.0 + " µs");
        System.out.println("Avg batch latency: " + avgBatchLatencyMicros + " µs");
        System.out.println("Avg event latency: " + avgEventLatencyMicros + " µs");
        System.out.println("Batch throughput: " + String.format("%.2f", batchThroughputPerSecond) + " batches/second");
        System.out.println("Event throughput: " + String.format("%.2f", eventThroughputPerSecond) + " events/second");

        // Verify that the benchmark completed successfully
        assertTrue(eventProcessor.getProcessedCount() >= BENCHMARK_COUNT,
                "Not all events were processed");
    }

    /**
     * A simple event class for testing.
     */
    public static class TestEvent {
        private final String message;
        private final long timestamp;

        public TestEvent(String message) {
            this.message = message;
            this.timestamp = System.nanoTime();
        }

        public String getMessage() {
            return message;
        }

        public long getTimestamp() {
            return timestamp;
        }

        @Override
        public String toString() {
            return "TestEvent{message='" + message + "', timestamp=" + timestamp + '}';
        }
    }

    /**
     * A test event source that can publish events.
     */
    private static class TestEventSource extends AbstractEventSourceService<TestEvent> {
        public TestEventSource(String name) {
            super(name);
        }

        public void publishEvent(TestEvent event) {
            if (output != null) {
                output.publish(event);
            }
        }
    }

    /**
     * A test event processor that processes TestEvents.
     */
    private static class TestEventProcessor implements StaticEventProcessor, EventProcessor<TestEventProcessor> {
        private final List<TestEvent> processedEvents = new ArrayList<>();
        private volatile int processedCount = 0;
        private List<EventFeed> eventFeeds = new ArrayList<>();

        public void handleTestEvent(TestEvent event) {
            processedCount++;
            // Only store a subset of events to avoid memory issues
            if (processedCount % 1000 == 0) {
                processedEvents.add(event);
            }
        }

        public List<TestEvent> getProcessedEvents() {
            return processedEvents;
        }

        public int getProcessedCount() {
            return processedCount;
        }

        @Override
        public void onEvent(Object event) {
            if (event instanceof TestEvent) {
                handleTestEvent((TestEvent) event);
            }
        }

        @Override
        public void addEventFeed(com.fluxtion.runtime.input.EventFeed eventFeed) {
            eventFeeds.add(eventFeed);
        }

        @Override
        public void init() {

        }

        @Override
        public void start() {
            EventSubscriptionKey<Object> subscriptionKey = new EventSubscriptionKey<>(
                    new EventSourceKey<>("testEventSourceFeed"),
                    CallBackType.ON_EVENT_CALL_BACK
            );

            eventFeeds.forEach(feed -> {
                feed.subscribe(this, subscriptionKey);
            });
        }

        @Override
        public void tearDown() {

        }
    }

    /**
     * A test log record listener that captures log records.
     */
    private static class TestLogRecordListener implements LogRecordListener {
        private final List<LogRecord> logRecords = new ArrayList<>();

        @Override
        public void processLogRecord(LogRecord logRecord) {
            // Only store a subset of log records to avoid memory issues
            if (logRecords.size() < 1000) {
                logRecords.add(logRecord);
            }
        }

        public List<LogRecord> getLogRecords() {
            return logRecords;
        }
    }
}
