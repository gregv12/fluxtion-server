/*
 * SPDX-FileCopyrightText: © 2025 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.fluxtion.server.benchmark.e2e;

import com.fluxtion.agrona.concurrent.BusySpinIdleStrategy;
import com.fluxtion.runtime.annotations.runtime.ServiceRegistered;
import com.fluxtion.runtime.node.ObjectEventHandlerNode;
import com.fluxtion.server.FluxtionServer;
import com.fluxtion.server.config.AppConfig;
import com.fluxtion.server.config.EventFeedConfig;
import com.fluxtion.server.config.EventProcessorConfig;
import com.fluxtion.server.config.EventProcessorGroupConfig;
import com.fluxtion.server.config.ThreadConfig;
import com.fluxtion.server.service.extension.AbstractEventSourceService;
import com.fluxtion.server.service.pool.ObjectPool;
import com.fluxtion.server.service.pool.ObjectPoolsRegistry;
import com.fluxtion.server.service.pool.impl.BasePoolAware;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.TimeUnit;

/**
 * JMH benchmark that measures end-to-end Fluxtion Server event delivery from an
 * EventSource to a handler using the ObjectPoolsRegistry. The benchmark is designed
 * for zero per-operation allocations by acquiring events from the pool.
 *
 * Run examples:
 *  -Dthreads=8 -Dwarmups=1 -Dmeas=3 -Dforks=1
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 1)
@Measurement(iterations = 3)
@Fork(1)
public class BenchmarkServerEventFlowJmh {

    /**
     * Pooled event type backed by BasePoolAware (has PoolTracker).
     */
    public static class Msg extends BasePoolAware {
        public long value;
    }

    /**
     * EventSource that acquires pooled Msg instances and publishes them.
     */
    public static class PooledEventSource extends AbstractEventSourceService<Msg> {
        private ObjectPool<Msg> pool;

        public PooledEventSource(String name) {
            super(name);
        }

        @ServiceRegistered
        public void setObjectPoolsRegistry(ObjectPoolsRegistry objectPoolsRegistry, String name) {
            // Create the pool with simple creator and reset hook; capacity/partitions default
            this.pool = objectPoolsRegistry.getOrCreate(
                    Msg.class,
                    Msg::new,
                    m -> m.value = 0
            );
        }

        /**
         * Publish with no per-op allocations.
         */
        public void publish(long v) {
            Msg m = pool.acquire();
            m.value = v;
            output.publish(m);
            // Framework manages references and return-to-pool at end of cycle
        }
    }

    /**
     * Minimal handler that touches the event (no allocation) and counts.
     */
    public static class CounterHandler extends ObjectEventHandlerNode {
        long count;
        long last;
        @Override
        protected boolean handleEvent(Object event) {
            if (event instanceof Msg m) {
                last = m.value; // touch
                count++;
            }
            return true;
        }
    }

    @State(Scope.Benchmark)
    public static class BenchmarkState {
        @Param({"1", "4", "16"})
        public int opsPerInvocation;

        @Param({"0"})
        public int workNanos; // keep zero-alloc, can introduce nanos if desired

        FluxtionServer server;
        PooledEventSource source;
        CounterHandler handler;

        @Setup(Level.Trial)
        public void setup() throws Exception {
            // Build server using fluent AppConfig with explicit agent threads and core pinning
            source = new PooledEventSource("benchPooledSource");
            handler = new CounterHandler();

            EventProcessorGroupConfig processors = EventProcessorGroupConfig.builder()
                    .agentName("processor-agent")
                    .put("counter", new EventProcessorConfig(handler))
                    .build();

            EventFeedConfig<?> feed = EventFeedConfig.builder()
                    .instance(source)
                    .name("benchPooledSource")
                    .broadcast(false)
                    .wrapWithNamedEvent(false) // unwrap for minimal overhead
                    .agent("source-agent", new BusySpinIdleStrategy())
                    .build();

            AppConfig appConfig = AppConfig.builder()
                    .addProcessorGroup(processors)
                    .addEventFeed(feed)
                    // Pin threads to specific cores if available (best-effort)
                    .addThread(ThreadConfig.builder().agentName("source-agent").idleStrategy(new BusySpinIdleStrategy()).coreId(0).build())
                    .addThread(ThreadConfig.builder().agentName("processor-agent").idleStrategy(new BusySpinIdleStrategy()).coreId(1).build())
                    .build();

            server = FluxtionServer.bootServer(appConfig, rec -> {});
        }

        @TearDown(Level.Trial)
        public void tearDown() {
            if (server != null) {
                server.stop();
                server = null;
            }
        }
    }

    @Benchmark
    @Threads(Threads.MAX)
    public void publish_through_server(final BenchmarkState state, final Blackhole bh) {
        // publish N events; handler runs inside processor agent. No per-op allocation in publish path.
        for (int i = 0; i < state.opsPerInvocation; i++) {
            state.source.publish(i);
        }
        // Consume something to keep JIT honest
        bh.consume(state.handler.count);
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    @Threads(Threads.MAX)
    public void publish_latencyAvg(final BenchmarkState state, final Blackhole bh) {
        for (int i = 0; i < state.opsPerInvocation; i++) {
            state.source.publish(i);
        }
        bh.consume(state.handler.last);
    }

    /**
     * Launch JMH from IDE
     */
    public static void main(String[] args) throws Exception {
        try {
            org.openjdk.jmh.Main.main(args);
        } catch (RuntimeException e) {
            String msg = String.valueOf(e.getMessage());
            if (msg.contains("META-INF/BenchmarkList")) {
                System.err.println("[INFO] JMH benchmark metadata not found. Ensure annotation processing for tests ran.\n" +
                        "Try: mvn -q test-compile then run again.");
            }
            throw e;
        }
    }
}
