/*
 * SPDX-FileCopyrightText: © 2024 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 */

package com.fluxtion.server.service;

import com.fluxtion.runtime.annotations.runtime.ServiceRegistered;
import com.fluxtion.runtime.input.SubscriptionManager;
import com.fluxtion.server.dispatch.*;
import com.fluxtion.server.service.scheduler.SchedulerService;
import lombok.extern.java.Log;

import java.util.function.Supplier;

@Log
public abstract class AbstractEventSourceService<T> implements
        LifeCycleEventSource<T>,
        EventFlowService {

    protected final String name;
    private final CallBackType eventToInvokeType;
    private final Supplier<EventToInvokeStrategy> eventToInokeStrategySupplier;
    protected EventToQueuePublisher<T> output;
    protected String serviceName;
    protected EventSubscriptionKey<T> subscriptionKey;
    protected SchedulerService scheduler;

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
}
