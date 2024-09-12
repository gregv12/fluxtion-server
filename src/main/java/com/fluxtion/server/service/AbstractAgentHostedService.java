/*
 * SPDX-FileCopyrightText: © 2024 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 */

package com.fluxtion.server.service;

import com.fluxtion.agrona.concurrent.Agent;
import com.fluxtion.runtime.annotations.runtime.ServiceRegistered;
import com.fluxtion.runtime.input.SubscriptionManager;
import com.fluxtion.server.dispatch.*;
import com.fluxtion.server.service.scheduler.SchedulerService;
import lombok.extern.java.Log;

@Log
public abstract class AbstractAgentHostedService<T> implements
        Agent,
        LifeCycleEventSource<T>,
        EventFlowService {

    private final String name;
    private final CallBackType eventCallBack;
    protected EventToQueuePublisher<T> output;
    protected String serviceName;
    protected EventSubscriptionKey<T> subscriptionKey;
    protected SchedulerService scheduler;

    protected AbstractAgentHostedService(String name) {
        this(name, CallBackType.ON_EVENT_CALL_BACK);
    }

    public AbstractAgentHostedService(String name, CallBackType eventCallBack) {
        this.name = name;
        this.eventCallBack = eventCallBack;
    }

    @Override
    public void setEventFlowManager(EventFlowManager eventFlowManager, String serviceName) {
        this.serviceName = serviceName;
        output = eventFlowManager.registerEventSource(serviceName, this);
        subscriptionKey = new EventSubscriptionKey<>(
                new EventSourceKey<>(serviceName),
                eventCallBack,
                name
        );
    }

    @ServiceRegistered
    public void scheduler(SchedulerService scheduler) {
        this.scheduler = scheduler;
    }

    @Override
    public String roleName() {
        return name;
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
