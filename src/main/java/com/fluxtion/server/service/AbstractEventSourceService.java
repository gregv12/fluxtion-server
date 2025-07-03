/*
 * SPDX-FileCopyrightText: © 2025 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.fluxtion.server.service;

import com.fluxtion.runtime.StaticEventProcessor;
import com.fluxtion.runtime.annotations.runtime.ServiceRegistered;
import com.fluxtion.runtime.input.NamedFeed;
import com.fluxtion.runtime.input.SubscriptionManager;
import com.fluxtion.runtime.node.EventSubscription;
import com.fluxtion.server.dispatch.*;
import com.fluxtion.server.service.scheduler.SchedulerService;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.java.Log;

import java.util.function.Function;
import java.util.function.Supplier;

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
                eventToInvokeType,
                name
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
        SubscriptionManager subscriptionManager = EventFlowManager.currentProcessor().getSubscriptionManager();
        subscriptionManager.subscribe(subscriptionKey);
    }

    @Override
    public void tearDown() {

    }

    @Override
    public void registerSubscriber(StaticEventProcessor subscriber) {
        if (eventWrapStrategy == EventWrapStrategy.BROADCAST_NOWRAP | eventWrapStrategy == EventWrapStrategy.BROADCAST_NAMED_EVENT) {
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
    }

    @Override
    public void setSlowConsumerStrategy(SlowConsumerStrategy slowConsumerStrategy) {
        this.slowConsumerStrategy = slowConsumerStrategy;
    }

    @Override
    public void setDataMapper(Function<T, ?> dataMapper) {
        this.dataMapper = dataMapper;
    }
}
