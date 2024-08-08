/*
 * SPDX-FileCopyrightText: © 2024 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 */

package com.fluxtion.server;

import com.fluxtion.agrona.ErrorHandler;
import com.fluxtion.agrona.concurrent.AgentRunner;
import com.fluxtion.agrona.concurrent.IdleStrategy;
import com.fluxtion.agrona.concurrent.SleepingMillisIdleStrategy;
import com.fluxtion.agrona.concurrent.UnsafeBuffer;
import com.fluxtion.agrona.concurrent.status.AtomicCounter;
import com.fluxtion.runtime.StaticEventProcessor;
import com.fluxtion.runtime.annotations.feature.Experimental;
import com.fluxtion.runtime.service.Service;
import com.fluxtion.server.dispatch.EventFlowService;
import com.fluxtion.server.dutycycle.ComposingEventProcessorAgent;
import com.fluxtion.server.dutycycle.ComposingServerAgent;
import com.fluxtion.server.dutycycle.ServerAgent;
import com.fluxtion.server.service.scheduler.DeadWheelScheduler;
import lombok.Value;
import lombok.extern.java.Log;

import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

@Experimental
@Log
public class FluxtionServer {

    private final com.fluxtion.server.dispatch.EventFlowManager flowManager = new com.fluxtion.server.dispatch.EventFlowManager();
    private final ConcurrentHashMap<String, ComposingAgentRunner> composingEventAgents = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ComposingWorkerServiceAgentRunner> composingServerAgents = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Service<?>> registeredServices = new ConcurrentHashMap<>();
    private ErrorHandler errorHandler = m -> log.severe(m.getMessage());

    public void setDefaultErrorHandler(ErrorHandler errorHandler) {
        this.errorHandler = errorHandler;
    }

    public void registerEventMapperFactory(Supplier<com.fluxtion.server.dispatch.EventToInvokeStrategy> eventMapper, com.fluxtion.server.dispatch.CallBackType type) {
        log.info("registerEventMapperFactory:" + eventMapper);
        flowManager.registerEventMapperFactory(eventMapper, type);
    }

    public <T> void registerEventSource(String sourceName, com.fluxtion.server.dispatch.EventSource<T> eventSource) {
        log.info("registerEventSource name:" + sourceName + " eventSource:" + eventSource);
        flowManager.registerEventSource(sourceName, eventSource);
    }

    public void registerService(Service<?>... services) {
        for (Service<?> service : services) {
            String serviceName = service.serviceName();
            log.info("registerService:" + service);
            if (registeredServices.containsKey(serviceName)) {
                throw new IllegalArgumentException("cannot register service name is already assigned:" + serviceName);
            }
            registeredServices.put(serviceName, service);
            Object instance = service.instance();
            if (instance instanceof com.fluxtion.server.dispatch.EventFlowService) {
                ((EventFlowService) instance).setEventFlowManager(flowManager, serviceName);
            }
        }
    }

    public void registerWorkerService(ServerAgent<?> service) {
        String agentGroup = service.getAgentGroup();
        ComposingWorkerServiceAgentRunner composingAgentRunner = composingServerAgents.computeIfAbsent(
                agentGroup,
                ket -> {
                    //build a subscriber group
                    ComposingServerAgent group = new ComposingServerAgent(agentGroup, flowManager, this, new DeadWheelScheduler());
                    //threading to be configured by file
                    IdleStrategy idleStrategy = new SleepingMillisIdleStrategy(100);
                    AtomicCounter errorCounter = new AtomicCounter(new UnsafeBuffer(new byte[4096]), 0);
                    //run subscriber group
                    AgentRunner groupRunner = new AgentRunner(
                            idleStrategy,
                            errorHandler,
                            errorCounter,
                            group);
                    return new ComposingWorkerServiceAgentRunner(group, groupRunner);
                });

        composingAgentRunner.getGroup().registerServer(service);
    }

    public void init() {
        log.info("init");
        registeredServices.values().forEach(svc -> {
            if (!(svc.instance() instanceof com.fluxtion.server.dispatch.LifeCycleEventSource)) {
                svc.init();
            }
        });
        flowManager.init();
    }

    public void start() {
        log.info("start");

        log.info("start registered services");
        registeredServices.values().forEach(svc -> {
            if (!(svc.instance() instanceof com.fluxtion.server.dispatch.LifeCycleEventSource)) {
                svc.start();
            }
        });

        log.info("start flowManager");
        flowManager.start();

        log.info("start service agent workers");
        composingServerAgents.forEach((k, v) -> {
            log.info("starting composing service agent " + k);
            AgentRunner.startOnThread(v.getGroupRunner());
        });

        log.info("start event processor agent workers");
        composingEventAgents.forEach((k, v) -> {
            log.info("starting composing event processor agent " + k);
            AgentRunner.startOnThread(v.getGroupRunner());
        });

    }

    public void addEventProcessor(String groupName, Supplier<StaticEventProcessor> feedConsumer) {
        ComposingAgentRunner composingAgentRunner = composingEventAgents.computeIfAbsent(
                groupName,
                ket -> {
                    //build a subscriber group
                    ComposingEventProcessorAgent group = new ComposingEventProcessorAgent(groupName, flowManager, this, new DeadWheelScheduler(), registeredServices);
                    //threading to be configured by file
                    IdleStrategy idleStrategy = new SleepingMillisIdleStrategy(100);
                    AtomicCounter errorCounter = new AtomicCounter(new UnsafeBuffer(new byte[4096]), 0);
                    //run subscriber group
                    AgentRunner groupRunner = new AgentRunner(
                            idleStrategy,
                            errorHandler,
                            errorCounter,
                            group);
                    return new ComposingAgentRunner(group, groupRunner);
                });

        composingAgentRunner.getGroup().addEventFeedConsumer(feedConsumer);
    }

    public Collection<Service<?>> servicesRegistered() {
        return Collections.unmodifiableCollection(registeredServices.values());
    }

    @Value
    private static class ComposingAgentRunner {
        ComposingEventProcessorAgent group;
        AgentRunner groupRunner;
    }

    @Value
    private static class ComposingWorkerServiceAgentRunner {
        ComposingServerAgent group;
        AgentRunner groupRunner;
    }
}
