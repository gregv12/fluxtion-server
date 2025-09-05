/*
 * SPDX-FileCopyrightText: © 2025 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.fluxtion.server.benchmark.hdr;

import com.fluxtion.agrona.concurrent.BusySpinIdleStrategy;
import com.fluxtion.runtime.annotations.runtime.ServiceRegistered;
import com.fluxtion.runtime.node.ObjectEventHandlerNode;
import com.fluxtion.server.FluxtionServer;
import com.fluxtion.server.config.*;
import com.fluxtion.server.service.extension.AbstractEventSourceService;
import com.fluxtion.server.service.pool.ObjectPool;
import com.fluxtion.server.service.pool.ObjectPoolsRegistry;
import com.fluxtion.server.service.pool.impl.BasePoolAware;
import org.HdrHistogram.Histogram;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * HdrHistogram-based latency measurement for Fluxtion Server event flow.
 * <p>
 * - Boots the server with BusySpin and core pinning for source and processor agents
 * - Uses an ObjectPool-backed event type to avoid per-op allocations
 * - Records end-to-end publish->handle latency (ns) into an HdrHistogram
 * - Writes a markdown report into docs/benchmark/reports
 */
public class ServerLatencyHdrHistogramTest {

    /**
     * Pooled event carrying a send timestamp.
     */
    public static class TimedMsg extends BasePoolAware {
        public long sendNano;
        public long value;
    }

    public static class PooledEventSource extends AbstractEventSourceService<TimedMsg> {
        private ObjectPool<TimedMsg> pool;

        public PooledEventSource(String name) {
            super(name);
        }

        @ServiceRegistered
        public void setObjectPoolsRegistry(ObjectPoolsRegistry objectPoolsRegistry, String name) {
            this.pool = objectPoolsRegistry.getOrCreate(
                    TimedMsg.class,
                    TimedMsg::new,
                    m -> {
                        m.sendNano = 0L;
                        m.value = 0L;
                    }
            );
        }

        public void publish(long v) {
            TimedMsg m = pool.acquire();
            m.value = v;
            m.sendNano = System.nanoTime();
            output.publish(m);
        }
    }

    public static class LatencyHandler extends ObjectEventHandlerNode {
        private final Histogram histogram;
        private final CountDownLatch latch;
        public volatile long last;

        public LatencyHandler(Histogram histogram, CountDownLatch latch) {
            this.histogram = histogram;
            this.latch = latch;
        }

        @Override
        protected boolean handleEvent(Object event) {
            if (event instanceof TimedMsg m) {
                if (m.value == -1) {
                    histogram.reset();
                    return false;
                }
                long latency = System.nanoTime() - m.sendNano;
                // record latency in nanoseconds
                histogram.recordValue(latency);
                last = latency;
//                if (latch.getCount() > 0) {
//                    latch.countDown();
//                }
            }
            return true;
        }
    }

    @Test
    public void hdrHistogram_latency_report() throws Exception {
        final int warmup = 50_000_000; // warm-up messages
        final int measure = 30_000_000; // measured messages
        final String sourceAgent = "hdr-source-agent";
        final String procAgent = "hdr-processor-agent";

        Histogram histogram = new Histogram(TimeUnit.MILLISECONDS.toNanos(1000), 3);
        CountDownLatch measuredLatch = new CountDownLatch(measure);

        PooledEventSource source = new PooledEventSource("hdrPooledSource");
        LatencyHandler handler = new LatencyHandler(histogram, measuredLatch);

        EventProcessorGroupConfig processors = EventProcessorGroupConfig.builder()
                .agentName(procAgent)
                .put("latency-handler", new EventProcessorConfig<>(handler))
                .build();

        EventFeedConfig<?> feed = EventFeedConfig.builder()
                .instance(source)
                .name("hdrPooledSource")
                .broadcast(true)
                .wrapWithNamedEvent(false)
//                .agent(sourceAgent, new BusySpinIdleStrategy())
                .build();

        AppConfig appConfig = AppConfig.builder()
                .addProcessorGroup(processors)
                .addEventFeed(feed)
                .addThread(ThreadConfig.builder().agentName(procAgent).idleStrategy(new BusySpinIdleStrategy()).coreId(1).build())
                .build();

        FluxtionServer server = FluxtionServer.bootServer(appConfig, rec -> {});
        try {
            // warm-up
            for (int i = 0; i < warmup; i++) {
                source.publish(i);
            }

            source.publish(-1);
            // measure
            for (int i = 0; i < measure; i++) {
                source.publish(i);
            }
            System.out.println("waiting for measured events");
            Thread.sleep(3000);
//            assertTrue(measuredLatch.await(10, TimeUnit.SECONDS), "Timed out waiting for measured events");

            // Write results to docs
            writeReport(histogram);
        } finally {
            server.stop();
        }
    }

    private static void writeReport(Histogram histogram) throws Exception {
        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        File outDir = new File("docs/benchmark/reports");
        if (!outDir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            outDir.mkdirs();
        }
        File outFile = new File(outDir, "server-latency-hdrhistogram.md");
        try (FileOutputStream fos = new FileOutputStream(outFile, false);
             PrintStream ps = new PrintStream(fos, true, StandardCharsets.UTF_8)) {
            ps.println("# Server Latency (HdrHistogram)\n");
            ps.println("Generated: " + ts + "\n");
            ps.println("Units: nanoseconds (ns)\n");
            ps.println("## Summary\n");
            ps.printf("Count: %d\n", histogram.getTotalCount());
            ps.printf("Min: %d ns\n", histogram.getMinValue());
            ps.printf("p50: %d ns\n", histogram.getValueAtPercentile(50));
            ps.printf("p90: %d ns\n", histogram.getValueAtPercentile(90));
            ps.printf("p99: %d ns\n", histogram.getValueAtPercentile(99));
            ps.printf("Max: %d ns\n\n", histogram.getMaxValue());
            ps.println("## Percentile distribution\n");
            histogram.outputPercentileDistribution(ps, 1.0);
        }
    }
}
