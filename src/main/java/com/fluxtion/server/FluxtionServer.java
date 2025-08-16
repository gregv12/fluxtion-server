/*
 * SPDX-FileCopyrightText: © 2025 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.fluxtion.server;

import com.fluxtion.agrona.ErrorHandler;
import com.fluxtion.agrona.concurrent.AgentRunner;
import com.fluxtion.agrona.concurrent.IdleStrategy;
import com.fluxtion.agrona.concurrent.UnsafeBuffer;
import com.fluxtion.agrona.concurrent.status.AtomicCounter;
import com.fluxtion.runtime.StaticEventProcessor;
import com.fluxtion.runtime.annotations.runtime.ServiceRegistered;
import com.fluxtion.runtime.audit.LogRecordListener;
import com.fluxtion.runtime.service.Service;
import com.fluxtion.runtime.service.ServiceRegistryNode;
import com.fluxtion.server.config.AppConfig;
import com.fluxtion.server.dispatch.EventFlowService;
import com.fluxtion.server.dutycycle.ComposingEventProcessorAgent;
import com.fluxtion.server.dutycycle.ComposingServiceAgent;
import com.fluxtion.server.dutycycle.NamedEventProcessor;
import com.fluxtion.server.dutycycle.ServiceAgent;
import com.fluxtion.server.internal.ComposingEventProcessorAgentRunner;
import com.fluxtion.server.internal.ComposingWorkerServiceAgentRunner;
import com.fluxtion.server.service.admin.AdminCommandRegistry;
import com.fluxtion.server.service.scheduler.DeadWheelScheduler;
import com.fluxtion.server.service.servercontrol.FluxtionServerController;
import lombok.SneakyThrows;
import lombok.extern.java.Log;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileReader;
import java.io.Reader;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

@Log
public class FluxtionServer implements FluxtionServerController {

    public static final String CONFIG_FILE_PROPERTY = "fluxtionserver.config.file";
    private static LogRecordListener logRecordListener;
    private final AppConfig appConfig;
    private final com.fluxtion.server.dispatch.EventFlowManager flowManager = new com.fluxtion.server.dispatch.EventFlowManager();
    private final ConcurrentHashMap<String, ComposingEventProcessorAgentRunner> composingEventProcessorAgents = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ComposingWorkerServiceAgentRunner> composingServiceAgents = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Service<?>> registeredServices = new ConcurrentHashMap<>();
    private final Set<Service<?>> registeredAgentServices = ConcurrentHashMap.newKeySet();
    private ErrorHandler errorHandler = m -> log.severe(m.getMessage());
    private final ServiceRegistryNode serviceRegistry = new ServiceRegistryNode();
    private volatile boolean started = false;
    private final com.fluxtion.server.internal.LifecycleManager lifecycleManager = new com.fluxtion.server.internal.LifecycleManager(this);

    public FluxtionServer(AppConfig appConfig) {
        this.appConfig = appConfig;
    }

    public static FluxtionServer bootServer(Reader reader, LogRecordListener logRecordListener) {
        log.info("booting server loading config from reader");
        LoaderOptions loaderOptions = new LoaderOptions();
        loaderOptions.setTagInspector(tag -> true);
        Yaml yaml = new Yaml(loaderOptions);
        AppConfig appConfig = yaml.loadAs(reader, AppConfig.class);
        log.info("successfully loaded config from reader");

        return bootServer(appConfig, logRecordListener);
    }

    @SneakyThrows
    public static FluxtionServer bootServer(LogRecordListener logRecordListener) {
        String configFileName = System.getProperty(CONFIG_FILE_PROPERTY);
        Objects.requireNonNull(configFileName, "fluxtion config file must be specified by system property: " + CONFIG_FILE_PROPERTY);
        File configFile = new File(configFileName);
        log.info("booting fluxtion server with config file:" + configFile + " specified by system property:" + CONFIG_FILE_PROPERTY);
        try (FileReader reader = new FileReader(configFileName)) {
            return bootServer(reader, logRecordListener);
        }
    }

    public static FluxtionServer bootServer(AppConfig appConfig, LogRecordListener logRecordListener) {
        FluxtionServer.logRecordListener = logRecordListener;
        log.info("booting fluxtion server");
        log.fine("config:" + appConfig);
        return com.fluxtion.server.internal.ServerConfigurator.bootFromConfig(appConfig, logRecordListener);
    }

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
                throw new com.fluxtion.server.exception.ServiceRegistrationException("cannot register service name is already assigned:" + serviceName);
            }
            registeredServices.put(serviceName, service);
            Object instance = service.instance();
            //TODO set service name if not an EventFlow service
            if (instance instanceof com.fluxtion.server.dispatch.EventFlowService) {
                ((EventFlowService) instance).setEventFlowManager(flowManager, serviceName);
            }
            // Dependency injection: inject other registered services into this instance
            com.fluxtion.server.service.ServiceInjector.inject(instance, registeredServices.values());
            // Also inject the newly registered service into existing services (single-target injection)
            for (Service<?> existing : registeredServices.values()) {
                Object existingInstance = existing.instance();
                if (existingInstance != instance) {
                    com.fluxtion.server.service.ServiceInjector.inject(existingInstance, java.util.Collections.singleton(service));
                }
            }
        }
    }

    public void registerAgentService(Service<?>... services) {
        registerService(services);
        registeredAgentServices.addAll(Arrays.asList(services));
    }

    public void registerWorkerService(ServiceAgent<?> service) {
        String agentGroup = service.getAgentGroup();
        IdleStrategy idleStrategy = appConfig.lookupIdleStrategyWhenNull(service.getIdleStrategy(), service.getAgentGroup());
        log.info("registerWorkerService:" + service + " agentGroup:" + agentGroup + " idleStrategy:" + idleStrategy);
        ComposingWorkerServiceAgentRunner composingAgentRunner = composingServiceAgents.computeIfAbsent(
                agentGroup,
                ket -> {
                    //build a subscriber group
                    ComposingServiceAgent group = new ComposingServiceAgent(agentGroup, flowManager, this, new DeadWheelScheduler());
                    //threading to be configured by file
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

    public void addEventProcessor(
            String processorName,
            String groupName,
            IdleStrategy idleStrategy,
            Supplier<StaticEventProcessor> feedConsumer) throws IllegalArgumentException {
        IdleStrategy idleStrategyOverride = appConfig.getIdleStrategyOrDefault(groupName, idleStrategy);
        ComposingEventProcessorAgentRunner composingEventProcessorAgentRunner = composingEventProcessorAgents.computeIfAbsent(
                groupName,
                ket -> {
                    //build a subscriber group
                    ComposingEventProcessorAgent group = new ComposingEventProcessorAgent(groupName, flowManager, this, new DeadWheelScheduler(), registeredServices);
                    //threading to be configured by file
                    AtomicCounter errorCounter = new AtomicCounter(new UnsafeBuffer(new byte[4096]), 0);
                    //run subscriber group
                    AgentRunner groupRunner = new AgentRunner(
                            idleStrategyOverride,
                            errorHandler,
                            errorCounter,
                            group);
                    return new ComposingEventProcessorAgentRunner(group, groupRunner);
                });

        if (composingEventProcessorAgentRunner.getGroup().isProcessorRegistered(processorName)) {
            throw new IllegalArgumentException("cannot add event processor name is already assigned:" + processorName);
        }

        composingEventProcessorAgentRunner.getGroup().addNamedEventProcessor(() -> {
            StaticEventProcessor eventProcessor = feedConsumer.get();
            eventProcessor.setAuditLogProcessor(logRecordListener);
            if (started) {
                log.info("init event processor in already started server processor:'" + eventProcessor + "'");
//                eventProcessor.setAuditLogLevel(EventLogControlEvent.LogLevel.INFO);
            }
            return new NamedEventProcessor(processorName, eventProcessor);
        });

        if (started && composingEventProcessorAgentRunner.getGroupRunner().thread() == null) {
            log.info("staring event processor group:'" + groupName + "' for running server");
            AgentRunner.startOnThread(composingEventProcessorAgentRunner.getGroupRunner());
        }
    }

    @Override
    public Map<String, Collection<NamedEventProcessor>> registeredProcessors() {
        HashMap<String, Collection<NamedEventProcessor>> result = new HashMap<>();
        composingEventProcessorAgents.entrySet().forEach(entry -> {
            result.put(entry.getKey(), entry.getValue().getGroup().registeredEventProcessors());
        });
        return result;
    }

    @Override
    public void stopProcessor(String groupName, String processorName) {
        log.info("stopProcessor:" + processorName + " in group:" + groupName);
        var processorAgent = composingEventProcessorAgents.get(groupName);
        if (processorAgent != null) {
            processorAgent.getGroup().removeEventProcessorByName(processorName);
        }
    }

    @Override
    public void startService(String serviceName) {
        log.info("start service:" + serviceName);
        if (registeredServices.containsKey(serviceName)) {
            registeredServices.get(serviceName).start();
        }
    }

    @Override
    public void stopService(String serviceName) {
        log.info("stop service:" + serviceName);
        //check if registered and started
        if (registeredServices.containsKey(serviceName)) {
            registeredServices.get(serviceName).stop();
        }
    }

    @Override
    public Map<String, Service<?>> registeredServices() {
        return registeredServices;
    }

    public void init() {
        lifecycleManager.init(registeredServices, registeredAgentServices, flowManager, serviceRegistry);
    }

    @SneakyThrows
    public void start() {
        java.util.concurrent.ConcurrentHashMap<String, com.fluxtion.server.internal.LifecycleManager.GroupRunner> serviceGroups = new java.util.concurrent.ConcurrentHashMap<>();
        composingServiceAgents.forEach((k, v) -> serviceGroups.put(k, new com.fluxtion.server.internal.LifecycleManager.GroupRunner() {
            @Override
            public com.fluxtion.agrona.concurrent.AgentRunner getGroupRunner() {
                return v.getGroupRunner();
            }

            @Override
            public com.fluxtion.agrona.concurrent.DynamicCompositeAgent getGroup() {
                return v.getGroup();
            }

            @Override
            public void startCompleteIfSupported() {
                v.getGroup().startComplete();
            }
        }));
        java.util.concurrent.ConcurrentHashMap<String, com.fluxtion.server.internal.LifecycleManager.GroupRunner> processorGroups = new java.util.concurrent.ConcurrentHashMap<>();
        composingEventProcessorAgents.forEach((k, v) -> processorGroups.put(k, new com.fluxtion.server.internal.LifecycleManager.GroupRunner() {
            @Override
            public com.fluxtion.agrona.concurrent.AgentRunner getGroupRunner() {
                return v.getGroupRunner();
            }

            @Override
            public com.fluxtion.agrona.concurrent.DynamicCompositeAgent getGroup() {
                return v.getGroup();
            }
        }));
        lifecycleManager.start(registeredServices, serviceGroups, processorGroups, flowManager, registeredAgentServices);
        started = true;
    }

    public Collection<Service<?>> servicesRegistered() {
        return Collections.unmodifiableCollection(registeredServices.values());
    }

    /**
     * Stops the server and all its components.
     * This method stops all event processor agents, agent hosted services, the flowManager, and all registered services.
     * It should be called when the server is no longer needed to free up resources.
     */
    public void stop() {
        lifecycleManager.stop(started, toGroupRunnerMap(composingEventProcessorAgents), toGroupRunnerMap(composingServiceAgents), registeredServices);
        started = false;
    }

    private java.util.concurrent.ConcurrentHashMap<String, com.fluxtion.server.internal.LifecycleManager.GroupRunner> toGroupRunnerMap(java.util.concurrent.ConcurrentHashMap<String, ? extends Object> source) {
        java.util.concurrent.ConcurrentHashMap<String, com.fluxtion.server.internal.LifecycleManager.GroupRunner> map = new java.util.concurrent.ConcurrentHashMap<>();
        source.forEach((k, v) -> {
            if (v instanceof ComposingEventProcessorAgentRunner cep) {
                map.put(k, new com.fluxtion.server.internal.LifecycleManager.GroupRunner() {
                    @Override
                    public com.fluxtion.agrona.concurrent.AgentRunner getGroupRunner() {
                        return cep.getGroupRunner();
                    }

                    @Override
                    public com.fluxtion.agrona.concurrent.DynamicCompositeAgent getGroup() {
                        return cep.getGroup();
                    }
                });
            } else if (v instanceof ComposingWorkerServiceAgentRunner cws) {
                map.put(k, new com.fluxtion.server.internal.LifecycleManager.GroupRunner() {
                    @Override
                    public com.fluxtion.agrona.concurrent.AgentRunner getGroupRunner() {
                        return cws.getGroupRunner();
                    }

                    @Override
                    public com.fluxtion.agrona.concurrent.DynamicCompositeAgent getGroup() {
                        return cws.getGroup();
                    }

                    @Override
                    public void startCompleteIfSupported() {
                        cws.getGroup().startComplete();
                    }
                });
            }
        });
        return map;
    }

    @ServiceRegistered
    public void adminClient(AdminCommandRegistry adminCommandRegistry, String name) {
        log.info("adminCommandRegistry registered:" + name);
    }
}
