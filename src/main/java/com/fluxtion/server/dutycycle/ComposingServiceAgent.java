/*
 * SPDX-FileCopyrightText: © 2025 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.fluxtion.server.dutycycle;

import com.fluxtion.agrona.concurrent.DynamicCompositeAgent;
import com.fluxtion.agrona.concurrent.OneToOneConcurrentArrayQueue;
import com.fluxtion.runtime.annotations.feature.Experimental;
import com.fluxtion.runtime.service.Service;
import com.fluxtion.runtime.service.ServiceRegistryNode;
import com.fluxtion.server.FluxtionServer;
import com.fluxtion.server.dispatch.EventFlowManager;
import com.fluxtion.server.internal.ServiceInjector;
import com.fluxtion.server.service.scheduler.DeadWheelScheduler;
import com.fluxtion.server.service.scheduler.SchedulerService;
import lombok.extern.java.Log;

import java.util.concurrent.atomic.AtomicBoolean;


/**
 * Composite agent that manages a group of worker service agents for Fluxtion Server.
 * <p>
 * Responsibilities:
 * <ul>
 *   <li>Accepts registrations of ServiceAgent instances destined to run on this agent group</li>
 *   <li>Initializes and starts their exported services, injecting server-registered services and the scheduler</li>
 *   <li>Adds their Agent delegates to the underlying DynamicCompositeAgent at the appropriate time</li>
 *   <li>Signals startComplete to services once the agent group is fully active</li>
 * </ul>
 */
@Experimental
@Log
public class ComposingServiceAgent extends DynamicCompositeAgent {

    private final EventFlowManager eventFlowManager;
    private final FluxtionServer fluxtionServer;
    private final DeadWheelScheduler scheduler;
    private final Service<SchedulerService> schedulerService;
    private final OneToOneConcurrentArrayQueue<ServiceAgent<?>> toStartList = new OneToOneConcurrentArrayQueue<>(128);
    private final OneToOneConcurrentArrayQueue<ServiceAgent<?>> toAddList = new OneToOneConcurrentArrayQueue<>(128);
    private final OneToOneConcurrentArrayQueue<ServiceAgent<?>> toCallStartupCompleteList = new OneToOneConcurrentArrayQueue<>(128);
    private final ServiceRegistryNode serviceRegistry = new ServiceRegistryNode();
    private final AtomicBoolean startUpComplete = new AtomicBoolean(false);

    public ComposingServiceAgent(String roleName,
                                 EventFlowManager eventFlowManager,
                                 FluxtionServer fluxtionServer,
                                 DeadWheelScheduler scheduler) {
        super(roleName, scheduler);
        this.eventFlowManager = eventFlowManager;
        this.fluxtionServer = fluxtionServer;
        this.scheduler = scheduler;
        this.schedulerService = new Service<>(scheduler, SchedulerService.class);
    }

    public <T> void registerServer(ServiceAgent<T> server) {
        toStartList.add(server);
        toCallStartupCompleteList.add(server);
        log.info("registerServer toCallStartupCompleteList size:" + toCallStartupCompleteList.size());
    }

    @Override
    public void onStart() {
        log.info("onStart toStartList size:" + toStartList.size());
        checkForAdded();
        super.onStart();
    }

    @Override
    public int doWork() throws Exception {
        checkForAdded();
        return super.doWork();
    }

    public void startComplete() {
        log.info("startComplete toCallStartupCompleteList size:" + toCallStartupCompleteList.size());
        startUpComplete.set(true);
    }

    @Override
    public void onClose() {
        log.info("onClose");
        super.onClose();
    }

    private void checkForAdded() {
        if (!toStartList.isEmpty()) {
            toStartList.drain(serviceAgent -> {
                toAddList.add(serviceAgent);
                Service<?> exportedService = serviceAgent.exportedService();
                exportedService.init();
                serviceRegistry.init();
                serviceRegistry.nodeRegistered(exportedService.instance(), exportedService.serviceName());
                serviceRegistry.registerService(schedulerService);
                fluxtionServer.servicesRegistered().forEach(serviceRegistry::registerService);
                // Inject dependencies into the agent-hosted service instance (scheduler + server services)
                java.util.List<com.fluxtion.runtime.service.Service<?>> inj = new java.util.ArrayList<>(fluxtionServer.servicesRegistered());
                inj.add(schedulerService);
                ServiceInjector.inject(exportedService.instance(), inj);
                fluxtionServer.registerAgentService(exportedService);
                exportedService.start();
            });
        }

        if (!toAddList.isEmpty() && status() == Status.ACTIVE && tryAdd(toAddList.peek().delegate())) {
            toAddList.poll();
        }

        if (startUpComplete.get() & !toCallStartupCompleteList.isEmpty()) {
            toCallStartupCompleteList.drain(s -> {
                s.exportedService().startComplete();
            });
        }
    }
}
