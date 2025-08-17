/*
 * SPDX-FileCopyrightText: © 2025 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.fluxtion.server.dispatch;

import com.fluxtion.agrona.concurrent.Agent;
import com.fluxtion.agrona.concurrent.ManyToOneConcurrentArrayQueue;
import com.fluxtion.agrona.concurrent.OneToOneConcurrentArrayQueue;
import com.fluxtion.server.dutycycle.EventQueueToEventProcessor;
import com.fluxtion.server.dutycycle.EventQueueToEventProcessorAgent;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;


/**
 * Manages mapping between:
 * <ul>
 *     <li>{@link com.fluxtion.server.dispatch.EventSource} - pushed events into a queue</li>
 *     <li>{@link com.fluxtion.server.dutycycle.EventQueueToEventProcessor} -  reads from a queue and handles multiplexing to registered {@link com.fluxtion.runtime.StaticEventProcessor}</li>
 *     <li>{@link EventToInvokeStrategy} - processed an event and map events to callbacks on the {@link com.fluxtion.runtime.StaticEventProcessor}</li>
 * </ul>
 */
public class EventFlowManager {

    private final ConcurrentHashMap<com.fluxtion.server.dispatch.EventSourceKey<?>, EventSource_QueuePublisher<?>> eventSourceToQueueMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<EventSinkKey<?>, ManyToOneConcurrentArrayQueue<?>> eventSinkToQueueMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<com.fluxtion.server.dispatch.CallBackType, Supplier<EventToInvokeStrategy>> eventToInvokerFactoryMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<EventSourceKey_Subscriber<?>, OneToOneConcurrentArrayQueue<Object>> subscriberKeyToQueueMap = new ConcurrentHashMap<>();

    public EventFlowManager() {
        eventToInvokerFactoryMap.put(CallBackType.ON_EVENT_CALL_BACK, EventToOnEventInvokeStrategy::new);
    }

    public void init() {
        forEachLifeCycleEventSource(com.fluxtion.server.dispatch.LifeCycleEventSource::init);
    }

    public void start() {
        forEachLifeCycleEventSource(com.fluxtion.server.dispatch.LifeCycleEventSource::start);
    }

    @SuppressWarnings("unchecked")
    public <T> ManyToOneConcurrentArrayQueue<T> registerEventSink(EventSourceKey<T> sinkKey, Object sinkReader) {
        Objects.requireNonNull(sinkKey, "sinkKey must be non-null");
        EventSinkKey<T> eventSinkKey = new EventSinkKey<>(sinkKey, sinkReader);
        return (ManyToOneConcurrentArrayQueue<T>) eventSinkToQueueMap.computeIfAbsent(
                eventSinkKey,
                key -> new ManyToOneConcurrentArrayQueue<T>(1024));
    }

    @SuppressWarnings("unchecked")
    public void subscribe(EventSubscriptionKey<?> subscriptionKey) {
        Objects.requireNonNull(subscriptionKey, "subscriptionKey must be non-null");

        EventSource_QueuePublisher<?> eventSourceQueuePublisher = eventSourceToQueueMap.get(subscriptionKey.eventSourceKey());
        Objects.requireNonNull(eventSourceQueuePublisher, "no EventSource registered for EventSourceKey:" + subscriptionKey);
        eventSourceQueuePublisher.eventSource().subscribe((EventSubscriptionKey) subscriptionKey);
    }

    @SuppressWarnings("unchecked")
    public void unSubscribe(EventSubscriptionKey<?> subscriptionKey) {
        Objects.requireNonNull(subscriptionKey, "subscriptionKey must be non-null");

        EventSource_QueuePublisher<?> eventSourceQueuePublisher = eventSourceToQueueMap.get(subscriptionKey.eventSourceKey());
        Objects.requireNonNull(eventSourceQueuePublisher, "no EventSource registered for EventSourceKey:" + subscriptionKey);
        eventSourceQueuePublisher.eventSource().unSubscribe((EventSubscriptionKey) subscriptionKey);
    }

    @SuppressWarnings("unchecked")
    public <T> com.fluxtion.server.dispatch.EventToQueuePublisher<T> registerEventSource(String sourceName, com.fluxtion.server.dispatch.EventSource<T> eventSource) {
        Objects.requireNonNull(eventSource, "eventSource must be non-null");

        EventSource_QueuePublisher<?> eventSourceQueuePublisher = eventSourceToQueueMap.computeIfAbsent(
                new com.fluxtion.server.dispatch.EventSourceKey<>(sourceName),
                eventSourceKey -> new EventSource_QueuePublisher<>(new com.fluxtion.server.dispatch.EventToQueuePublisher<>(sourceName), eventSource));

        com.fluxtion.server.dispatch.EventToQueuePublisher<T> queuePublisher = (com.fluxtion.server.dispatch.EventToQueuePublisher<T>) eventSourceQueuePublisher.queuePublisher();
        eventSource.setEventToQueuePublisher(queuePublisher);
        return queuePublisher;
    }

    public void registerEventMapperFactory(Supplier<EventToInvokeStrategy> eventMapper, com.fluxtion.server.dispatch.CallBackType type) {
        Objects.requireNonNull(eventMapper, "eventMapper must be non-null");
        Objects.requireNonNull(type, "type must be non-null");

        eventToInvokerFactoryMap.put(type, eventMapper);
    }

    public void registerEventMapperFactory(Supplier<EventToInvokeStrategy> eventMapper, Class<?> type) {
        Objects.requireNonNull(eventMapper, "eventMapper must be non-null");
        Objects.requireNonNull(type, "Callback class type must be non-null");

        registerEventMapperFactory(eventMapper, com.fluxtion.server.dispatch.CallBackType.forClass(type));
    }

    public <T> com.fluxtion.server.dutycycle.EventQueueToEventProcessor getMappingAgent(EventSourceKey<T> eventSourceKey, CallBackType type, Agent subscriber) {
        Objects.requireNonNull(eventSourceKey, "eventSourceKey must be non-null");
        Objects.requireNonNull(type, "type must be non-null");
        Objects.requireNonNull(subscriber, "subscriber must be non-null");

        Supplier<EventToInvokeStrategy> eventMapperSupplier = eventToInvokerFactoryMap.get(type);
        Objects.requireNonNull(eventMapperSupplier, "no EventMapper registered for type:" + type);

        EventSource_QueuePublisher<T> sourcePublisher = getEventSourceQueuePublisherOrThrow(eventSourceKey);

        // create or re-use a target queue
        EventSourceKey_Subscriber<T> keySubscriber = new EventSourceKey_Subscriber<>(eventSourceKey, subscriber);
        OneToOneConcurrentArrayQueue<Object> eventQueue = getOrCreateSubscriberQueue(keySubscriber);

        // add as a target to the source
        String name = buildSubscriptionName(subscriber, eventSourceKey, type);
        sourcePublisher.queuePublisher().addTargetQueue(eventQueue, name);

        Runnable unsubscribe = createUnsubscribeAction(sourcePublisher, name, keySubscriber);

        return new EventQueueToEventProcessorAgent(eventQueue, eventMapperSupplier.get(), name)
                .withUnsubscribeAction(unsubscribe);
    }

    public <T> EventQueueToEventProcessor getMappingAgent(EventSubscriptionKey<T> subscriptionKey, Agent subscriber) {
        return getMappingAgent(subscriptionKey.eventSourceKey(), subscriptionKey.callBackType(), subscriber);
    }

    public void appendQueueInformation(Appendable appendable) {
        if (eventSourceToQueueMap.isEmpty()) {
            safeAppend(appendable, "No event readers registered");
            return;
        }
        eventSourceToQueueMap.forEach((key, value) -> appendQueueDetails(appendable, key.sourceName(), value.queuePublisher()));
    }

    private void forEachLifeCycleEventSource(java.util.function.Consumer<com.fluxtion.server.dispatch.LifeCycleEventSource> action) {
        eventSourceToQueueMap.values().stream()
                .map(EventSource_QueuePublisher::eventSource)
                .filter(com.fluxtion.server.dispatch.LifeCycleEventSource.class::isInstance)
                .map(com.fluxtion.server.dispatch.LifeCycleEventSource.class::cast)
                .forEach(action);
    }

    private <T> EventSource_QueuePublisher<T> getEventSourceQueuePublisherOrThrow(EventSourceKey<T> eventSourceKey) {
        @SuppressWarnings("unchecked")
        EventSource_QueuePublisher<T> publisher = (EventSource_QueuePublisher<T>) eventSourceToQueueMap.get(eventSourceKey);
        return Objects.requireNonNull(publisher, "no EventSource registered for EventSourceKey:" + eventSourceKey);
    }

    private <T> OneToOneConcurrentArrayQueue<Object> getOrCreateSubscriberQueue(EventSourceKey_Subscriber<T> keySubscriber) {
        return subscriberKeyToQueueMap.computeIfAbsent(keySubscriber, key -> new OneToOneConcurrentArrayQueue<>(1024));
    }

    private static String buildSubscriptionName(Agent subscriber, EventSourceKey<?> eventSourceKey, CallBackType type) {
        return subscriber.roleName() + "/" + eventSourceKey.sourceName() + "/" + type.name();
    }

    private Runnable createUnsubscribeAction(EventSource_QueuePublisher<?> sourcePublisher, String name, EventSourceKey_Subscriber<?> keySubscriber) {
        return () -> {
            sourcePublisher.queuePublisher().removeTargetQueueByName(name);
            subscriberKeyToQueueMap.remove(keySubscriber);
        };
    }

    private static void safeAppend(Appendable appendable, String text) {
        try {
            appendable.append(text);
        } catch (IOException ex) {
            System.err.println("problem logging event queues, exception:" + ex);
        }
    }

    private static void appendQueueDetails(Appendable appendable, String sourceName, com.fluxtion.server.dispatch.EventToQueuePublisher<?> queue) {
        try {
            appendable.append("eventSource:").append(sourceName)
                    .append("\n\treadQueues:\n");
            for (com.fluxtion.server.dispatch.EventToQueuePublisher.NamedQueue q : queue.getTargetQueues()) {
                appendable.append("\t\t").append(q.name()).append(" -> ").append(q.targetQueue().toString()).append("\n");
            }
        } catch (IOException ex) {
            System.err.println("problem logging event queues, exception:" + ex);
        }
    }

    private record EventSource_QueuePublisher<T>(EventToQueuePublisher<T> queuePublisher, EventSource<T> eventSource) {
    }

    private record EventSourceKey_Subscriber<T>(EventSourceKey<T> eventSourceKey, Object subscriber) {
    }

    private record EventSinkKey<T>(EventSourceKey<T> eventSourceKey, Object subscriber) {
    }
}
