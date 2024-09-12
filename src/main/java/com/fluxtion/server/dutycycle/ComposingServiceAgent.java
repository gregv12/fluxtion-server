/*
 * SPDX-FileCopyrightText: © 2024 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 */

package com.fluxtion.server.dutycycle;

import com.fluxtion.agrona.concurrent.DynamicCompositeAgent;
import com.fluxtion.agrona.concurrent.OneToOneConcurrentArrayQueue;
import com.fluxtion.runtime.annotations.feature.Experimental;
import com.fluxtion.runtime.service.Service;
import com.fluxtion.runtime.service.ServiceRegistryNode;
import com.fluxtion.server.FluxtionServer;
import com.fluxtion.server.dispatch.EventFlowManager;
import com.fluxtion.server.service.scheduler.DeadWheelScheduler;
import com.fluxtion.server.service.scheduler.SchedulerService;
import lombok.extern.java.Log;


/**
 *
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
    private final ServiceRegistryNode serviceRegistry = new ServiceRegistryNode();

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
        log.info("registerServer toStartList size:" + toStartList.size());
    }

    @Override
    public void onStart() {
        log.info("onStart toStartList size:" + toStartList.size());
        toStartList.drain(serviceAgent -> {
            toAddList.add(serviceAgent);
            Service<?> exportedService = serviceAgent.getExportedService();
            exportedService.init();
            serviceRegistry.init();
            serviceRegistry.nodeRegistered(exportedService.instance(), exportedService.serviceName());
            serviceRegistry.registerService(schedulerService);
            fluxtionServer.servicesRegistered().forEach(serviceRegistry::registerService);
            fluxtionServer.registerAgentService(exportedService);
            exportedService.start();
        });
        super.onStart();
    }

    @Override
    public int doWork() throws Exception {
        toAddList.drain(serviceAgent -> {
            log.info("doWork adding:" + serviceAgent);
            tryAdd(serviceAgent.getDelegate());
        });
        return super.doWork();
    }

    @Override
    public void onClose() {
        log.info("onClose");
        super.onClose();
    }
}
