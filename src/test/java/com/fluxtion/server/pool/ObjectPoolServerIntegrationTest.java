/*
 * SPDX-FileCopyrightText: © 2025 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.fluxtion.server.pool;

import com.fluxtion.agrona.concurrent.BusySpinIdleStrategy;
import com.fluxtion.runtime.annotations.runtime.ServiceRegistered;
import com.fluxtion.server.FluxtionServer;
import com.fluxtion.server.config.AppConfig;
import com.fluxtion.server.dispatch.EventToOnEventInvokeStrategy;
import com.fluxtion.server.example.objectpool.PoolEventSourceServerExample;
import com.fluxtion.server.service.CallBackType;
import com.fluxtion.server.service.EventSource;
import com.fluxtion.server.service.EventSourceKey;
import com.fluxtion.server.service.EventSubscriptionKey;
import com.fluxtion.server.service.extension.AbstractEventSourceService;
import com.fluxtion.server.service.pool.ObjectPool;
import com.fluxtion.server.service.pool.ObjectPoolsRegistry;
import com.fluxtion.server.service.pool.PoolAware;
import com.fluxtion.server.service.pool.impl.PoolTracker;
import com.fluxtion.server.service.pool.impl.Pools;
import lombok.Getter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Boots a FluxtionServer and validates that pooled objects are returned to the pool
 * once processing is complete across the full publish -> queue -> processor pipeline.
 */
public class ObjectPoolServerIntegrationTest {

    static class PooledMessage implements PoolAware {
        final PoolTracker<PooledMessage> tracker = new PoolTracker<>();
        String value;

        @Override
        public PoolTracker<PooledMessage> getPoolTracker() {
            return tracker;
        }

        @Override
        public String toString() {
            return "PooledMessage{" + value + '}';
        }
    }

    /**
     * Simple EventSource that publishes via injected EventToQueuePublisher.
     */
    static class TestPooledEventSource implements EventSource<PooledMessage> {
        private com.fluxtion.server.dispatch.EventToQueuePublisher<PooledMessage> publisher;
        private EventWrapStrategy wrapStrategy = EventWrapStrategy.SUBSCRIPTION_NOWRAP;

        @Override
        public void subscribe(EventSubscriptionKey<PooledMessage> eventSourceKey) { /* no-op for test */ }

        @Override
        public void unSubscribe(EventSubscriptionKey<PooledMessage> eventSourceKey) { /* no-op */ }

        @Override
        public void setEventToQueuePublisher(com.fluxtion.server.dispatch.EventToQueuePublisher<PooledMessage> targetQueue) {
            this.publisher = targetQueue;
            this.publisher.setEventWrapStrategy(wrapStrategy);
        }

        @Override
        public void setEventWrapStrategy(EventWrapStrategy eventWrapStrategy) {
            this.wrapStrategy = eventWrapStrategy;
            if (publisher != null) {
                publisher.setEventWrapStrategy(eventWrapStrategy);
            }
        }

        public void publish(PooledMessage msg) {
            publisher.publish(msg);
        }
    }

    /**
     * Processor that subscribes on start to the source using ON_EVENT mapping.
     */
    static class SubscribingProcessor implements com.fluxtion.runtime.StaticEventProcessor, com.fluxtion.runtime.lifecycle.Lifecycle {
        private com.fluxtion.runtime.input.EventFeed<EventSubscriptionKey<?>> feed;

        @Override
        public void onEvent(Object event) { /* no-op */ }

        @Override
        public void init() {
        }

        @Override
        public void start() {
            // subscribe to our source when started
            EventSubscriptionKey<PooledMessage> key = new EventSubscriptionKey<>(EventSourceKey.of("poolSource"), CallBackType.ON_EVENT_CALL_BACK);
            feed.subscribe(this, key);
        }

        @Override
        public void stop() {
        }

        @Override
        public void tearDown() {
        }

        @Override
        public void startComplete() {
        }

        @Override
        public void registerService(com.fluxtion.runtime.service.Service<?> service) {
        }

        @Override
        public void addEventFeed(com.fluxtion.runtime.input.EventFeed eventFeed) {
            this.feed = eventFeed;
        }
    }

    private FluxtionServer server;

    @AfterEach
    void cleanup() {
        if (server != null) {
            try {
                server.stop();
            } catch (Throwable ignored) {
            }
        }
    }

    @Test
    public void testServerBootAndPoolReturnNowrap() throws Exception {
        Pools.SHARED.remove(PooledMessage.class);
        ObjectPool<PooledMessage> pool = Pools.SHARED.getOrCreate(PooledMessage.class, PooledMessage::new, pm -> pm.value = null);

        // Build config with mapping for ON_EVENT
        AppConfig cfg = AppConfig.builder()
                .idleStrategy(new BusySpinIdleStrategy())
                .onEventInvokeStrategy(EventToOnEventInvokeStrategy::new)
                .build();

        server = new FluxtionServer(cfg);

        // Register event source and processor
        TestPooledEventSource source = new TestPooledEventSource();
        server.registerEventSource("poolSource", source);
        server.addEventProcessor("proc", "groupA", new BusySpinIdleStrategy(), SubscribingProcessor::new);

        server.init();
        server.start();

        // Acquire message and publish
        PooledMessage msg = pool.acquire();
        msg.value = "serverNowrap";
        source.publish(msg);

        // await up to ~500ms for async processing to complete and return to pool
        long deadline = System.currentTimeMillis() + 500;
        while (pool.availableCount() == 0 && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
        }
        assertEquals(1, pool.availableCount(), "message should be returned to pool after processing");
    }

    @Test
    public void testServerBootAndPoolReturnNamedEvent() throws Exception {
        Pools.SHARED.remove(PooledMessage.class);
        ObjectPool<PooledMessage> pool = Pools.SHARED.getOrCreate(PooledMessage.class, PooledMessage::new, pm -> pm.value = null);

        // Build config with mapping for ON_EVENT
        AppConfig cfg = AppConfig.builder()
                .idleStrategy(new BusySpinIdleStrategy())
                .onEventInvokeStrategy(EventToOnEventInvokeStrategy::new)
                .build();

        server = new FluxtionServer(cfg);

        // Register event source configured to wrap with named event
        TestPooledEventSource source = new TestPooledEventSource();
        source.setEventWrapStrategy(EventSource.EventWrapStrategy.SUBSCRIPTION_NAMED_EVENT);
        server.registerEventSource("poolSource", source);
        server.addEventProcessor("proc", "groupB", new BusySpinIdleStrategy(), SubscribingProcessor::new);

        server.init();
        server.start();

        // Acquire message and publish
        PooledMessage msg = pool.acquire();
        msg.value = "serverNamed";
        source.publish(msg);

        // await up to ~500ms for async processing to complete and return to pool
        long deadline = System.currentTimeMillis() + 500;
        while (pool.availableCount() == 0 && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
        }
        assertEquals(1, pool.availableCount(), "message should be returned to pool after processing (named event)");
    }

    @Test
    public void testServerNowrap_tryWithResources() throws Exception {
        Pools.SHARED.remove(PooledMessage.class);
        ObjectPool<PooledMessage> pool = Pools.SHARED.getOrCreate(PooledMessage.class, PooledMessage::new, pm -> pm.value = null);

        AppConfig cfg = AppConfig.builder()
                .idleStrategy(new BusySpinIdleStrategy())
                .onEventInvokeStrategy(EventToOnEventInvokeStrategy::new)
                .build();

        server = new FluxtionServer(cfg);
        TestPooledEventSource source = new TestPooledEventSource();
        server.registerEventSource("poolSource", source);
        server.addEventProcessor("proc", "groupC", new BusySpinIdleStrategy(), SubscribingProcessor::new);
        server.init();
        server.start();

        PooledMessage msg = pool.acquire();
        msg.value = "twr-nowrap";
        source.publish(msg);

        long deadline = System.currentTimeMillis() + 500;
        while (pool.availableCount() == 0 && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
        }
        assertEquals(1, pool.availableCount(), "message should be returned using try-with-resources (nowrap)");
    }


    public static class PooledEventSource extends AbstractEventSourceService<PooledMessage> {
        @Getter
        private ObjectPool<PooledMessage> pool;

        public PooledEventSource() {
            super("pooledSource");
        }

        public void publish(PooledMessage msg) {
            if (output != null) {
                output.publish(msg);
            }
        }

        @ServiceRegistered
        public void setObjectPoolsRegistry(ObjectPoolsRegistry objectPoolsRegistry, String name) {
            this.pool = objectPoolsRegistry.getOrCreate(
                    PooledMessage.class,
                    PooledMessage::new,
                    pm -> pm.value = null);
        }
    }


    @Test
    public void testServerNamedEvent_tryWithResources() throws Exception {
        PooledEventSource source = new PooledEventSource();

        AppConfig cfg = new AppConfig()
                .addProcessor("thread-p1", new PoolEventSourceServerExample.MyHandler(), "processor")
                .addEventSource(source, "pooledSource", true);
        server = FluxtionServer.bootServer(cfg, (l) -> {});


        ObjectPool<PooledMessage> pool = source.getPool();
        PooledMessage msg = pool.acquire();
        msg.value = "twr-named";
        source.publish(msg);

        long deadline = System.currentTimeMillis() + 500;
        while (pool.availableCount() == 0 && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
        }
        assertEquals(1, pool.availableCount(), "message should be returned using try-with-resources (named)");
    }
}
