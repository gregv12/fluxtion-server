/*
 * SPDX-FileCopyrightText: © 2025 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.fluxtion.server;

import com.fluxtion.runtime.lifecycle.Lifecycle;
import com.fluxtion.runtime.service.Service;
import com.fluxtion.server.config.MongooseServerConfig;
import com.fluxtion.server.dispatch.EventFlowManager;
import com.fluxtion.server.dispatch.EventToQueuePublisher;
import com.fluxtion.server.service.EventFlowService;
import com.fluxtion.server.service.EventSubscriptionKey;
import com.fluxtion.server.service.LifeCycleEventSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests covering lifecycle management behavior and ordering.
 */
public class MongooseServerLifecycleTest {

    private MongooseServer server;
    private TestService service;
    private TestLifeCycleEventSource eventSourceService;

    @BeforeEach
    void setUp() {
        server = new MongooseServer(new MongooseServerConfig());
        service = new TestService();
        eventSourceService = new TestLifeCycleEventSource();
    }

    @Test
    void initStartAndStopOrderingAndExclusions() {
        // regular service
        server.registerService(new Service<>(service, TestService.class, "svc"));
        // life-cycle event source registered as both a service and event source
        server.registerService(new Service<>(eventSourceService, TestLifeCycleEventSource.class, "src"));

        // init
        server.init();
        // regular service is initialized by server lifecycle
        assertTrue(service.initialized, "regular service should be initialized");
        // LifeCycleEventSource should be initialized by EventFlowManager.init(), not by service loop
        assertTrue(eventSourceService.initialized, "event source should be initialized via flowManager");

        // start
        server.start();
        assertTrue(service.started, "regular service should be started");
        assertTrue(service.startCompleted, "startComplete should be called for regular service");
        assertTrue(eventSourceService.started, "event source should be started via flowManager");

        // stop
        server.stop();
        assertTrue(service.stopped, "regular service should be stopped on server.stop()");

        // idempotent stop should not throw
        server.stop();
    }

    /**
     * Guards the service category used by plugins such as JsonFileCache: a service that is an
     * {@link EventFlowService} (to receive the flow manager / service name) and a {@link Lifecycle},
     * but is NOT a {@link LifeCycleEventSource} and does NOT register itself as an event source.
     * <p>
     * Such a service must be initialized and started by the regular service loop. If it were a
     * LifeCycleEventSource it would be skipped by that loop, and because it never registers with the
     * flow manager it would also be missed there — silently never starting (the failure mode that
     * stopped the Javalin webadmin from binding).
     */
    @Test
    void eventFlowServiceThatIsNotEventSource_isStartedByServiceLoop() {
        TestEventFlowServiceLifecycle svc = new TestEventFlowServiceLifecycle();
        server.registerService(new Service<>(svc, TestEventFlowServiceLifecycle.class, "flowSvc"));

        server.init();
        assertTrue(svc.initialized,
                "EventFlowService that is not a LifeCycleEventSource must be initialized by the service loop");

        server.start();
        assertTrue(svc.started,
                "EventFlowService that is not a LifeCycleEventSource must be started by the service loop");

        server.stop();
        assertTrue(svc.stopped, "service should be stopped on server.stop()");
        server.stop();
    }

    // Test fixtures
    public static class TestService implements Lifecycle {
        boolean initialized;
        boolean started;
        boolean stopped;
        boolean tornDown;
        boolean startCompleted;

        @Override
        public void init() {
            initialized = true;
        }

        @Override
        public void start() {
            started = true;
        }

        @Override
        public void stop() {
            stopped = true;
        }

        @Override
        public void tearDown() {
            tornDown = true;
        }

        public void startComplete() {
            startCompleted = true;
        }
    }

    /**
     * EventFlowService + Lifecycle, but NOT a LifeCycleEventSource. Overrides setEventFlowManager
     * to NOT register itself as an event source (mirrors JsonFileCache, which only wants the
     * injected service name). Must be driven by the regular service lifecycle loop.
     */
    public static class TestEventFlowServiceLifecycle implements EventFlowService<String>, Lifecycle {
        boolean initialized;
        boolean started;
        boolean stopped;

        @Override
        public void setEventFlowManager(EventFlowManager eventFlowManager, String serviceName) {
            // intentionally does NOT register as an event source
        }

        @Override
        public void init() {
            initialized = true;
        }

        @Override
        public void start() {
            started = true;
        }

        @Override
        public void stop() {
            stopped = true;
        }

        @Override
        public void tearDown() {
        }

        @Override
        public void setEventToQueuePublisher(EventToQueuePublisher<String> targetQueue) {
        }

        @Override
        public void subscribe(EventSubscriptionKey<String> eventSourceKey) {
        }

        @Override
        public void unSubscribe(EventSubscriptionKey<String> eventSourceKey) {
        }
    }

    public static class TestLifeCycleEventSource implements LifeCycleEventSource<String> {
        boolean initialized;
        boolean started;
        boolean tornDown;
        private EventFlowManager eventFlowManager;

        @Override
        public void init() {
            initialized = true;
        }

        @Override
        public void start() {
            started = true;
        }

        @Override
        public void tearDown() {
            tornDown = true;
        }

        @Override
        public void setEventToQueuePublisher(com.fluxtion.server.dispatch.EventToQueuePublisher<String> targetQueue) {
        }

        @Override
        public void subscribe(EventSubscriptionKey<String> eventSourceKey) {
        }

        @Override
        public void unSubscribe(EventSubscriptionKey<String> eventSourceKey) {
        }
    }
}
