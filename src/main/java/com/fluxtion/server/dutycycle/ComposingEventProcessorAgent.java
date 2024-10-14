/*
 * SPDX-FileCopyrightText: © 2024 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 */

package com.fluxtion.server.dutycycle;

import com.fluxtion.agrona.concurrent.DynamicCompositeAgent;
import com.fluxtion.agrona.concurrent.OneToOneConcurrentArrayQueue;
import com.fluxtion.runtime.StaticEventProcessor;
import com.fluxtion.runtime.annotations.feature.Experimental;
import com.fluxtion.runtime.input.EventFeed;
import com.fluxtion.runtime.lifecycle.Lifecycle;
import com.fluxtion.runtime.service.Service;
import com.fluxtion.server.FluxtionServer;
import com.fluxtion.server.dispatch.EventFlowManager;
import com.fluxtion.server.dispatch.EventSubscriptionKey;
import com.fluxtion.server.service.scheduler.DeadWheelScheduler;
import com.fluxtion.server.service.scheduler.SchedulerService;
import lombok.extern.java.Log;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 *
 */
@Experimental
@Log
public class ComposingEventProcessorAgent extends DynamicCompositeAgent implements EventFeed<com.fluxtion.server.dispatch.EventSubscriptionKey<?>> {

    private final com.fluxtion.server.dispatch.EventFlowManager eventFlowManager;
    private final ConcurrentHashMap<String, Service<?>> registeredServices;
    private final ConcurrentHashMap<String, NamedEventProcessor> registeredEventProcessors = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<com.fluxtion.server.dispatch.EventSubscriptionKey<?>, EventQueueToEventProcessor> queueProcessorMap = new ConcurrentHashMap<>();
    private final OneToOneConcurrentArrayQueue<Supplier<NamedEventProcessor>> toStartList = new OneToOneConcurrentArrayQueue<>(128);
    private final List<EventQueueToEventProcessor> queueReadersToAdd = new ArrayList<>();
    private final FluxtionServer fluxtionServer;
    private final DeadWheelScheduler scheduler;
    private final Service<com.fluxtion.server.service.scheduler.SchedulerService> schedulerService;

    public ComposingEventProcessorAgent(String roleName,
                                        com.fluxtion.server.dispatch.EventFlowManager eventFlowManager,
                                        FluxtionServer fluxtionServer,
                                        DeadWheelScheduler scheduler,
                                        ConcurrentHashMap<String, Service<?>> registeredServices) {
        super(roleName, scheduler);
        this.eventFlowManager = eventFlowManager;
        this.fluxtionServer = fluxtionServer;
        this.scheduler = scheduler;
        this.registeredServices = registeredServices;
        this.schedulerService = new Service<>(scheduler, SchedulerService.class);
    }

    public void addEventFeedConsumer(Supplier<NamedEventProcessor> initFunction) {
        toStartList.add(initFunction);
    }

    @Override
    public void onStart() {
        log.info("onStart " + roleName());
        checkForAdded();
        super.onStart();
    }

    @Override
    public int doWork() throws Exception {
        checkForAdded();
        return super.doWork();
    }

    @Override
    public void onClose() {
        log.info("onClose " + roleName());
        super.onClose();
    }

    @Override
    public void registerSubscriber(StaticEventProcessor subscriber) {
        log.info("registerSubscriber:" + subscriber + " " + roleName());
    }

    @Override
    public void subscribe(StaticEventProcessor subscriber, com.fluxtion.server.dispatch.EventSubscriptionKey<?> subscriptionKey) {
        Objects.requireNonNull(subscriber, "subscriber is null");
        Objects.requireNonNull(subscriptionKey, "subscriptionKey is null");
        log.info("subscribe subscriptionKey:" + subscriptionKey + " subscriber:" + subscriber);

        EventQueueToEventProcessor eventQueueToEventProcessor = queueProcessorMap.get(subscriptionKey);

        if (eventQueueToEventProcessor == null) {
            eventQueueToEventProcessor = eventFlowManager.getMappingAgent(subscriptionKey, this);
            queueProcessorMap.put(subscriptionKey, eventQueueToEventProcessor);
            queueReadersToAdd.add(eventQueueToEventProcessor);
            log.info("added new subscribe subscriptionKey:" + subscriptionKey + " subscriber:" + subscriber);
        }

        eventQueueToEventProcessor.registerProcessor(subscriber);
        eventFlowManager.subscribe(subscriptionKey);
    }

    @Override
    public void unSubscribe(StaticEventProcessor subscriber, EventSubscriptionKey<?> subscriptionKey) {
        if (queueProcessorMap.containsKey(subscriptionKey)) {
            EventQueueToEventProcessor eventQueueToEventProcessor = queueProcessorMap.get(subscriptionKey);
            if (eventQueueToEventProcessor.deregisterProcessor(subscriber) == 0) {
                log.info("EventQueueToEventProcessor listener count = 0, removing subscription:" + subscriptionKey);
                queueProcessorMap.remove(subscriptionKey);
                eventFlowManager.unSubscribe(subscriptionKey);
            }
        }
    }

    @Override
    public void removeAllSubscriptions(StaticEventProcessor subscriber) {

    }

    public Collection<NamedEventProcessor> registeredEventProcessors() {
        return registeredEventProcessors.values();
    }

    private void checkForAdded() {
        toStartList.drain(init -> {
            NamedEventProcessor namedEventProcessor = init.get();
            StaticEventProcessor eventProcessor = namedEventProcessor.eventProcessor();
            registeredEventProcessors.put(namedEventProcessor.name(), namedEventProcessor);
            com.fluxtion.server.dispatch.EventFlowManager.setCurrentProcessor(eventProcessor);
            eventProcessor.registerService(schedulerService);
            registeredServices.values().forEach(eventProcessor::registerService);
            eventProcessor.addEventFeed(this);
            if (eventProcessor instanceof Lifecycle) {
                ((Lifecycle) eventProcessor).start();
                ((Lifecycle) eventProcessor).startComplete();
            }
            EventFlowManager.removeCurrentProcessor();
        });

        if (!queueReadersToAdd.isEmpty()) {
            if (status() == Status.ACTIVE && tryAdd(queueReadersToAdd.get(0))) {
                queueReadersToAdd.remove(0);
            }
        }
    }
}
