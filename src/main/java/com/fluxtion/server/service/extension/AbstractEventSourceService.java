/*
 * SPDX-FileCopyrightText: © 2025 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.fluxtion.server.service.extension;

import com.fluxtion.runtime.StaticEventProcessor;
import com.fluxtion.runtime.annotations.runtime.ServiceRegistered;
import com.fluxtion.runtime.input.NamedFeed;
import com.fluxtion.runtime.input.SubscriptionManager;
import com.fluxtion.runtime.node.EventSubscription;
import com.fluxtion.server.dispatch.EventFlowManager;
import com.fluxtion.server.dispatch.EventToQueuePublisher;
import com.fluxtion.server.service.*;
import com.fluxtion.server.service.scheduler.SchedulerService;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.java.Log;

import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Base class for event source services that participate in Fluxtion's event flow.
 * <p>
 * Responsibilities:
 * <ul>
 *   <li>Register the service as an {@link EventSource} with the event flow manager</li>
 *   <li>Manage subscription keys and subscription lifecycle for processors</li>
 *   <li>Expose knobs for event wrapping, slow-consumer handling, and data mapping</li>
 * </ul>
 * <p>
 * Subclasses typically:
 * <ul>
 *   <li>Construct with a unique service name and desired callback type</li>
 *   <li>Publish data via the configured {@link EventToQueuePublisher} obtained in {@link #setEventFlowManager}</li>
 *   <li>Call {@link #subscribe()} when a processor should begin receiving events</li>
 * </ul>
 *
 * @param <T> event type emitted by this source
 */
@Log
public abstract class AbstractEventSourceService<T>
        implements
        NamedFeed,
        LifeCycleEventSource<T>,
        EventFlowService {

    @Getter
    @Setter
    protected String name;
    private final CallBackType eventToInvokeType;
    private final Supplier<EventToInvokeStrategy> eventToInokeStrategySupplier;
    protected EventToQueuePublisher<T> output;
    protected String serviceName;
    protected EventSubscriptionKey<T> subscriptionKey;
    protected SchedulerService scheduler;
    private EventWrapStrategy eventWrapStrategy = EventWrapStrategy.SUBSCRIPTION_NOWRAP;
    private EventSource.SlowConsumerStrategy slowConsumerStrategy = SlowConsumerStrategy.BACKOFF;
    @Getter(AccessLevel.PROTECTED)
    private Function<T, ?> dataMapper = Function.identity();

    protected AbstractEventSourceService(String name) {
        this(name, CallBackType.ON_EVENT_CALL_BACK);
    }

    public AbstractEventSourceService(String name, CallBackType eventToInvokeType) {
        this(name, eventToInvokeType, null);
    }

    public AbstractEventSourceService(
            String name,
            CallBackType eventToInvokeType,
            Supplier<EventToInvokeStrategy> eventToInokeStrategySupplier) {
        this.name = name;
        this.eventToInvokeType = eventToInvokeType;
        this.eventToInokeStrategySupplier = eventToInokeStrategySupplier;
    }

    @Override
    public void setEventFlowManager(EventFlowManager eventFlowManager, String serviceName) {
        this.serviceName = serviceName;
        output = eventFlowManager.registerEventSource(serviceName, this);
        output.setEventWrapStrategy(eventWrapStrategy);
        output.setDataMapper(dataMapper);
        subscriptionKey = new EventSubscriptionKey<>(
                new EventSourceKey<>(serviceName),
                eventToInvokeType
        );

        if (eventToInokeStrategySupplier != null) {
            eventFlowManager.registerEventMapperFactory(eventToInokeStrategySupplier, eventToInvokeType);
        }
    }

    @ServiceRegistered
    public void scheduler(SchedulerService scheduler) {
        this.scheduler = scheduler;
    }

    @Override
    public void init() {

    }

    public void subscribe() {
        log.info("subscribe request");
        var current = com.fluxtion.server.dispatch.ProcessorContext.currentProcessor();
        if (current == null) {
            log.warning("subscribe called with no current processor in context; skipping subscription for service '" + serviceName + "'");
            return;
        }
        SubscriptionManager subscriptionManager = current.getSubscriptionManager();
        subscriptionManager.subscribe(subscriptionKey);
    }

    @Override
    public void tearDown() {

    }

    @Override
    public void registerSubscriber(StaticEventProcessor subscriber) {
        if (eventWrapStrategy == EventWrapStrategy.BROADCAST_NOWRAP || eventWrapStrategy == EventWrapStrategy.BROADCAST_NAMED_EVENT) {
            subscribe();
        }
    }

    @Override
    public void subscribe(StaticEventProcessor subscriber, EventSubscription<?> eventSubscription) {
        subscribe();
    }

    @Override
    public void unSubscribe(StaticEventProcessor subscriber, EventSubscription<?> eventSubscription) {
        subscriber.getSubscriptionManager().unSubscribe(subscriptionKey);
    }

    @Override
    public void removeAllSubscriptions(StaticEventProcessor subscriber) {
        //do nothing
    }

    @Override
    public void setEventWrapStrategy(EventWrapStrategy eventWrapStrategy) {
        this.eventWrapStrategy = eventWrapStrategy;
        if (output != null) {
            output.setEventWrapStrategy(eventWrapStrategy);
        }
    }

    @Override
    public void setSlowConsumerStrategy(SlowConsumerStrategy slowConsumerStrategy) {
        this.slowConsumerStrategy = slowConsumerStrategy;
    }

    @Override
    public void setDataMapper(Function<T, ?> dataMapper) {
        this.dataMapper = dataMapper;
        if (output != null) {
            output.setDataMapper(dataMapper);
        }
    }
}
