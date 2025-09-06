/*
 * SPDX-FileCopyrightText: © 2025 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.fluxtion.server.benchmark.objectpool;

import com.fluxtion.agrona.concurrent.BusySpinIdleStrategy;
import com.fluxtion.runtime.annotations.runtime.ServiceRegistered;
import com.fluxtion.runtime.node.ObjectEventHandlerNode;
import com.fluxtion.server.FluxtionServer;
import com.fluxtion.server.config.AppConfig;
import com.fluxtion.server.config.ThreadConfig;
import com.fluxtion.server.service.extension.AbstractEventSourceService;
import com.fluxtion.server.service.pool.ObjectPool;
import com.fluxtion.server.service.pool.ObjectPoolsRegistry;
import com.fluxtion.server.service.pool.impl.BasePoolAware;
import org.HdrHistogram.Histogram;

import java.util.List;

/**
 * End-to-end example that boots a FluxtionServer and uses an EventSource that
 * acquires messages from the global ObjectPool using try-with-resources. The
 * default PoolAware.close() releases the caller's reference and attempts to
 * return to the pool, while the pipeline (queues/consumers) holds and releases
 * its own references.
 * <p>
 * Run from your IDE by executing main().
 */
public class BenchmarkObjectPoolDistribution {

    public static final int PUBLISH_FREQUENCY_NANOS = 500;

    /**
     * A simple pooled message type.
     */
    public static class PooledMessage extends BasePoolAware {
        public long value;
        public long offSet;

        @Override
        public String toString() {
            return "PooledMessage{" + value + '}';
        }
    }

    /**
     * EventSource that publishes pooled messages. It uses try-with-resources to
     * ensure the origin reference is dropped on scope exit.
     */
    public static class PooledEventSource extends AbstractEventSourceService<PooledMessage> {
        private ObjectPool<PooledMessage> pool;

        public PooledEventSource() {
            super("pooledSource");
        }

        @ServiceRegistered
        public void setObjectPoolsRegistry(ObjectPoolsRegistry objectPoolsRegistry, String name) {
            this.pool = objectPoolsRegistry.getOrCreate(
                    PooledMessage.class,
                    PooledMessage::new,
                    pm -> pm.value = -1,
                    1024
            );
        }

        /**
         * Publish a message value using try-with-resources. The PoolAware.close()
         * drops the origin reference and attempts to return to pool; queued/consumer
         * references keep the object alive until fully processed.
         */
        public void publish(long value) {
            PooledMessage msg = pool.acquire();
            msg.value = value;
            output.publish(msg);
        }
    }

    public static class MyHandler extends ObjectEventHandlerNode {

        private long count;
        private long startTime = 0;
        private final Histogram histogram = new Histogram(3_600_000_000L, 3);

        @Override
        protected boolean handleEvent(Object event) {
            if (event instanceof PooledMessage pooledMessage && pooledMessage.value == -1) {
                System.out.println("this is a null message");
            }

            if (event instanceof PooledMessage pooledMessage) {
                count++;
                if (count < 1_000_000) {
                    return true;
                }

                if (startTime == 0) {
                    startTime = pooledMessage.value;
                }

                long now = System.nanoTime();
                long simpleLatency = now - pooledMessage.value;

                histogram.recordValue(simpleLatency);
                if (count % 5_000_000 == 0) {
                    System.out.println("HDR Histogram latency (ns): p50=" + histogram.getValueAtPercentile(50)
                            + ", p90=" + histogram.getValueAtPercentile(90)
                            + ", p99=" + histogram.getValueAtPercentile(99)
                            + ", p99.9=" + histogram.getValueAtPercentile(99.9)
                            + ", p99.99=" + histogram.getValueAtPercentile(99.99)
                            + ", p99.999=" + histogram.getValueAtPercentile(99.999)
                            + ", max=" + histogram.getMaxValue()
                            + ", count=" + histogram.getTotalCount()
                            + ", avg(ns)=" + (histogram.getMean()));
                    histogram.reset();
                    count = 0;
                    startTime = 0;
                }

            }
            return true;
        }
    }

    public static void main(String[] args) throws Exception {
        PooledEventSource source = new PooledEventSource();

        ThreadConfig threadConfig = ThreadConfig.builder()
                .agentName("pinned-agent-thread")
                .idleStrategy(new BusySpinIdleStrategy())
                .coreId(0) // zero-based index
                .build();

        AppConfig cfg = new AppConfig()
                .addProcessor("pinned-agent-thread", new MyHandler(), "processor")
                .addEventSource(source, "pooledSource", true);

        cfg.setAgentThreads(List.of(threadConfig));

        FluxtionServer server = FluxtionServer.bootServer(cfg, rec -> {
        });

        boolean running = true;
        Thread publisher = new Thread(() -> {
            long nextPublishTime = System.nanoTime();
            long now = System.nanoTime();
            long counter = 0;
            while (running) {
                if (now >= nextPublishTime) {
                    counter++;
                    source.publish(now);
                    nextPublishTime = now + PUBLISH_FREQUENCY_NANOS; // 1 microsecond = 1000 nanoseconds - 250 = 4 million events per second
                }
                now = System.nanoTime();
            }
        });
        publisher.start();
    }
}
