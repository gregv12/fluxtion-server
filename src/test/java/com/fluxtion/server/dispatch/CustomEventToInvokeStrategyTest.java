/*
 * SPDX-FileCopyrightText: © 2025 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.fluxtion.server.dispatch;

import com.fluxtion.agrona.concurrent.Agent;
import com.fluxtion.runtime.StaticEventProcessor;
import com.fluxtion.server.service.CallBackType;
import com.fluxtion.server.service.EventSource;
import com.fluxtion.server.service.EventSourceKey;
import com.fluxtion.server.service.EventSubscriptionKey;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Demonstrates building a custom EventToInvokeStrategy by extending
 * AbstractEventToInvocationStrategy. This custom strategy:
 * - Only accepts processors that implement the MarkerProcessor interface (isValidTarget filter)
 * - On dispatch, forwards only String events and uppercases them before invoking onEvent
 * - Ensures ProcessorContext is set correctly when invoking target processors
 */
public class CustomEventToInvokeStrategyTest {

    /** Marker interface to be used by the strategy to filter valid targets. Now includes a strongly-typed callback. */
    interface MarkerProcessor {
        void onString(String s);
    }

    /** Test StaticEventProcessor that records received events and asserts ProcessorContext correctness. */
    static class RecordingProcessor implements StaticEventProcessor, MarkerProcessor {
        final List<Object> received = new ArrayList<>();
        StaticEventProcessor seenCurrentProcessor;

        @Override
        public void onString(String s) {
            // ProcessorContext should point to this processor during dispatch
            seenCurrentProcessor = ProcessorContext.currentProcessor();
            received.add(s);
        }

        @Override
        public void onEvent(Object event) {
            // not used by this strategy
        }

        @Override
        public String toString() {
            return "RecordingProcessor{" + Integer.toHexString(System.identityHashCode(this)) + "}";
        }
    }

    /** A processor that should be rejected by isValidTarget. */
    static class NonMarkedProcessor implements StaticEventProcessor {
        final List<Object> received = new ArrayList<>();
        @Override
        public void onEvent(Object event) {
            received.add(event);
        }
    }

    /** Custom strategy implementation showing filtering and transformation, invoking a strongly-typed callback. */
    static class UppercaseStringStrategy extends AbstractEventToInvocationStrategy {
        @Override
        protected void dispatchEvent(Object event, StaticEventProcessor eventProcessor) {
            if (event instanceof String s && eventProcessor instanceof MarkerProcessor marker) {
                marker.onString(s.toUpperCase());
            }
            // ignore non-String events or non-marker processors
        }

        @Override
        protected boolean isValidTarget(StaticEventProcessor eventProcessor) {
            return eventProcessor instanceof MarkerProcessor;
        }
    }

    @Test
    void customStrategy_filtersTargets_transformsEvents_and_setsProcessorContext() throws Exception {
        // Arrange the flow and register the custom strategy for ON_EVENT_CALL_BACK
        EventFlowManager flow = new EventFlowManager();
        flow.registerEventMapperFactory(UppercaseStringStrategy::new, CallBackType.ON_EVENT_CALL_BACK);

        // Create an event source and subscribe a consumer via mapping agent
        String sourceName = "testSource";
        TestEventSource source = new TestEventSource();
        com.fluxtion.server.dispatch.EventToQueuePublisher<Object> publisher = flow.registerEventSource(sourceName, source);

        Agent subscriber = new TestAgent();
        var mappingAgent = flow.getMappingAgent(new EventSourceKey<>(sourceName), CallBackType.ON_EVENT_CALL_BACK, subscriber);

        // Register processors: one accepted, one rejected
        RecordingProcessor accepted = new RecordingProcessor();
        NonMarkedProcessor rejected = new NonMarkedProcessor();
        mappingAgent.registerProcessor(accepted);
        mappingAgent.registerProcessor(rejected);

        assertEquals(1, mappingAgent.listenerCount(), "Only the marked processor should be registered");

        // Act: publish a String and a non-String event through the source publisher
        publisher.publish("hello");
        publisher.publish(123);
        // Drive the agent to drain the queue
        int work1 = mappingAgent.doWork();
        int work2 = mappingAgent.doWork();
        assertTrue(work1 + work2 >= 2, "Expected the agent to process at least two enqueued items");

        // Assert: accepted processor received the transformed String and saw itself in ProcessorContext
        assertEquals(List.of("HELLO"), accepted.received, "String should be uppercased and delivered");
        assertSame(accepted, accepted.seenCurrentProcessor, "ProcessorContext should point to the target during dispatch");

        // Rejected processor must not be called at all
        assertTrue(rejected.received.isEmpty(), "Rejected processor should never receive events");
    }

    // Minimal test doubles for EventSource and Agent to wire a mapping agent
    static class TestEventSource implements EventSource<Object> {
        final List<EventSubscriptionKey<Object>> subscriptions = new ArrayList<>();
        @Override
        public void subscribe(EventSubscriptionKey<Object> eventSourceKey) {
            subscriptions.add(eventSourceKey);
        }
        @Override
        public void unSubscribe(EventSubscriptionKey<Object> eventSourceKey) {
            subscriptions.remove(eventSourceKey);
        }
        @Override
        public void setEventToQueuePublisher(com.fluxtion.server.dispatch.EventToQueuePublisher<Object> targetQueue) {
            // not used directly in this test
        }
    }

    static class TestAgent implements Agent {
        @Override
        public int doWork() { return 0; }
        @Override
        public String roleName() { return "test-agent"; }
    }
}
