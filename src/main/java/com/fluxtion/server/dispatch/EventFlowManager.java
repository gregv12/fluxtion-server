/*
 * SPDX-FileCopyrightText: © 2025 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.fluxtion.server.dispatch;

import com.fluxtion.agrona.concurrent.Agent;
import com.fluxtion.agrona.concurrent.ManyToOneConcurrentArrayQueue;
import com.fluxtion.agrona.concurrent.OneToOneConcurrentArrayQueue;
import com.fluxtion.runtime.StaticEventProcessor;
import com.fluxtion.runtime.annotations.feature.Experimental;
import com.fluxtion.server.dutycycle.EventQueueToEventProcessor;
import com.fluxtion.server.dutycycle.EventQueueToEventProcessorAgent;
import lombok.Value;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;


/**
 * Manages mapping between:
 * <ul>
 *     <li>{@link com.fluxtion.server.dispatch.EventSource} - pushed events into a queue</li>
 *     <li>{@link com.fluxtion.server.dutycycle.EventQueueToEventProcessor} -  reads from a queue and handles multiplexing to registered {@link com.fluxtion.runtime.StaticEventProcessor}</li>
 *     <li>{@link EventToInvokeStrategy} - processed an event and map events to callbacks on the {@link com.fluxtion.runtime.StaticEventProcessor}</li>
 * </ul>
 */
@Experimental
public class EventFlowManager {

    private final ConcurrentHashMap<com.fluxtion.server.dispatch.EventSourceKey<?>, EventSource_QueuePublisher<?>> eventSourceToQueueMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<EventSinkKey<?>, ManyToOneConcurrentArrayQueue<?>> eventSinkToQueueMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<com.fluxtion.server.dispatch.CallBackType, Supplier<EventToInvokeStrategy>> eventToInvokerFactoryMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<EventSourceKey_Subscriber<?>, OneToOneConcurrentArrayQueue<?>> subscriberKeyToQueueMap = new ConcurrentHashMap<>();
    private final static ThreadLocal<AtomicReference<StaticEventProcessor>> currentProcessor = new ThreadLocal<>();

    public static void setCurrentProcessor(StaticEventProcessor eventProcessor) {
        AtomicReference<StaticEventProcessor> ref = currentProcessor.get();
        if (ref == null) {
            ref = new AtomicReference<>(eventProcessor);
            currentProcessor.set(ref);
        }
        ref.set(eventProcessor);
    }

    public static void removeCurrentProcessor() {
        AtomicReference<StaticEventProcessor> ref = currentProcessor.get();
        if (ref != null) {
            ref.set(null);
        }
    }

    public static StaticEventProcessor currentProcessor() {
        AtomicReference<StaticEventProcessor> ref = currentProcessor.get();
        StaticEventProcessor processor = null;
        if (ref != null) {
            processor = ref.get();
        }
        return processor;
    }

    public EventFlowManager() {
        eventToInvokerFactoryMap.put(CallBackType.ON_EVENT_CALL_BACK, EventToOnEventInvokeStrategy::new);
    }

    public void init() {
        eventSourceToQueueMap.values().stream()
                .map(EventSource_QueuePublisher::getEventSource)
                .filter(com.fluxtion.server.dispatch.LifeCycleEventSource.class::isInstance)
                .map(com.fluxtion.server.dispatch.LifeCycleEventSource.class::cast)
                .forEach(com.fluxtion.server.dispatch.LifeCycleEventSource::init);
    }

    public void start() {
        eventSourceToQueueMap.values().stream()
                .map(EventSource_QueuePublisher::getEventSource)
                .filter(com.fluxtion.server.dispatch.LifeCycleEventSource.class::isInstance)
                .map(com.fluxtion.server.dispatch.LifeCycleEventSource.class::cast)
                .forEach(LifeCycleEventSource::start);
    }

    @SuppressWarnings("unchecked")
    public <T> ManyToOneConcurrentArrayQueue<T> registerEventSink(EventSourceKey<T> sinkKey, Object sinkReader) {
        Objects.requireNonNull(sinkKey, "sinkKey must be non-null");
        EventSinkKey<T> eventSinkKey = new EventSinkKey<>(sinkKey, sinkReader);
        return (ManyToOneConcurrentArrayQueue<T>) eventSinkToQueueMap.computeIfAbsent(
                eventSinkKey,
                key -> new ManyToOneConcurrentArrayQueue<T>(500));
    }

    @SuppressWarnings("unchecked")
    public void subscribe(EventSubscriptionKey<?> subscriptionKey) {
        Objects.requireNonNull(subscriptionKey, "subscriptionKey must be non-null");

        EventSource_QueuePublisher<?> eventSourceQueuePublisher = eventSourceToQueueMap.get(subscriptionKey.getEventSourceKey());
        Objects.requireNonNull(eventSourceQueuePublisher, "no EventSource registered for EventSourceKey:" + subscriptionKey);
        eventSourceQueuePublisher.getEventSource().subscribe((EventSubscriptionKey) subscriptionKey);
    }

    @SuppressWarnings("unchecked")
    public void unSubscribe(EventSubscriptionKey<?> subscriptionKey) {
        Objects.requireNonNull(subscriptionKey, "subscriptionKey must be non-null");

        EventSource_QueuePublisher<?> eventSourceQueuePublisher = eventSourceToQueueMap.get(subscriptionKey.getEventSourceKey());
        Objects.requireNonNull(eventSourceQueuePublisher, "no EventSource registered for EventSourceKey:" + subscriptionKey);
        eventSourceQueuePublisher.getEventSource().unSubscribe((EventSubscriptionKey) subscriptionKey);
    }

    @SuppressWarnings("unchecked")
    public <T> com.fluxtion.server.dispatch.EventToQueuePublisher<T> registerEventSource(String sourceName, com.fluxtion.server.dispatch.EventSource<T> eventSource) {
        Objects.requireNonNull(eventSource, "eventSource must be non-null");

        EventSource_QueuePublisher<?> eventSourceQueuePublisher = eventSourceToQueueMap.computeIfAbsent(
                new com.fluxtion.server.dispatch.EventSourceKey<>(sourceName),
                eventSourceKey -> new EventSource_QueuePublisher<>(new com.fluxtion.server.dispatch.EventToQueuePublisher<>(sourceName), eventSource));

        com.fluxtion.server.dispatch.EventToQueuePublisher<T> queuePublisher = (com.fluxtion.server.dispatch.EventToQueuePublisher<T>) eventSourceQueuePublisher.getQueuePublisher();
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

    @SuppressWarnings({"rawtypes", "unchecked"})
    public <T> com.fluxtion.server.dutycycle.EventQueueToEventProcessor getMappingAgent(EventSourceKey<T> eventSourceKey, CallBackType type, Agent subscriber) {
        Objects.requireNonNull(eventSourceKey, "eventSourceKey must be non-null");
        Objects.requireNonNull(type, "type must be non-null");
        Objects.requireNonNull(subscriber, "subscriber must be non-null");

        Supplier<EventToInvokeStrategy> eventMapperSupplier = eventToInvokerFactoryMap.get(type);
        Objects.requireNonNull(eventMapperSupplier, "no EventMapper registered for type:" + type);


        EventSource_QueuePublisher<T> eventSourceQueuePublisher = (EventSource_QueuePublisher<T>) eventSourceToQueueMap.get(eventSourceKey);
        Objects.requireNonNull(eventSourceQueuePublisher, "no EventSource registered for EventSourceKey:" + eventSourceKey);

        //create or re-use a target queue
        EventSourceKey_Subscriber<T> keySubscriber = new EventSourceKey_Subscriber<>(eventSourceKey, subscriber);
        OneToOneConcurrentArrayQueue eventQueue = subscriberKeyToQueueMap.computeIfAbsent(
                keySubscriber,
                key -> new OneToOneConcurrentArrayQueue<>(500));

        //add as a target to the source
        String name = subscriber.roleName() + "/" + eventSourceKey.getSourceName() + "/" + type.name();
        eventSourceQueuePublisher.getQueuePublisher().addTargetQueue(eventQueue, name);

        return new EventQueueToEventProcessorAgent(eventQueue, eventMapperSupplier.get(), name);
    }

    public <T> EventQueueToEventProcessor getMappingAgent(EventSubscriptionKey<T> subscriptionKey, Agent subscriber) {
        return getMappingAgent(subscriptionKey.getEventSourceKey(), subscriptionKey.getCallBackType(), subscriber);
    }

    public void appendQueueInformation(Appendable appendable) {
        if (eventSourceToQueueMap.isEmpty()) {
            try {
                appendable.append("No event readers registered");
            } catch (IOException ex) {
                System.err.println("problem logging event queues, exception:" + ex);
            }
            return;
        }
        eventSourceToQueueMap
                .forEach((key, value) -> {
                    try {
                        com.fluxtion.server.dispatch.EventToQueuePublisher<?> queue = value.getQueuePublisher();
                        appendable.append("eventSource:").append(key.getSourceName())
                                .append("\n\treadQueues:\n");
                        for (com.fluxtion.server.dispatch.EventToQueuePublisher.NamedQueue q : queue.getTargetQueues()) {
                            appendable.append("\t\t").append(q.getName()).append(" -> ").append(q.getTargetQueue().toString()).append("\n");
                        }
                    } catch (IOException ex) {
                        System.err.println("problem logging event queues, exception:" + ex);
                    }
                });
    }

    @Value
    private static class EventSource_QueuePublisher<T> {
        EventToQueuePublisher<T> queuePublisher;
        EventSource<T> eventSource;
    }

    @Value
    private static class EventSourceKey_Subscriber<T> {
        EventSourceKey<T> eventSourceKey;
        Object subscriber;
    }

    @Value
    private static class EventSinkKey<T> {
        EventSourceKey<T> eventSourceKey;
        Object subscriber;
    }
}
