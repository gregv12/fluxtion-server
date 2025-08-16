/*
 * SPDX-FileCopyrightText: © 2025 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.fluxtion.server;

import com.fluxtion.agrona.ErrorHandler;
import com.fluxtion.agrona.concurrent.AgentRunner;
import com.fluxtion.agrona.concurrent.DynamicCompositeAgent;
import com.fluxtion.agrona.concurrent.IdleStrategy;
import com.fluxtion.agrona.concurrent.UnsafeBuffer;
import com.fluxtion.agrona.concurrent.status.AtomicCounter;
import com.fluxtion.runtime.StaticEventProcessor;
import com.fluxtion.runtime.annotations.feature.Experimental;
import com.fluxtion.runtime.annotations.runtime.ServiceRegistered;
import com.fluxtion.runtime.audit.EventLogControlEvent;
import com.fluxtion.runtime.audit.LogRecordListener;
import com.fluxtion.runtime.service.Service;
import com.fluxtion.runtime.service.ServiceRegistryNode;
import com.fluxtion.server.config.*;
import com.fluxtion.server.dispatch.EventFlowService;
import com.fluxtion.server.dutycycle.*;
import com.fluxtion.server.service.admin.AdminCommandRegistry;
import com.fluxtion.server.service.scheduler.DeadWheelScheduler;
import com.fluxtion.server.service.servercontrol.FluxtionServerController;
import lombok.SneakyThrows;
import lombok.Value;
import lombok.extern.java.Log;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileReader;
import java.io.Reader;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

@Experimental
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
        FluxtionServer fluxtionServer = new FluxtionServer(appConfig);
        fluxtionServer.setDefaultErrorHandler(new GlobalErrorHandler());

        //root server controller
        fluxtionServer.registerService(new Service<>(fluxtionServer, FluxtionServerController.class, FluxtionServerController.SERVICE_NAME));

        //event sources
        if (appConfig.getEventFeeds() != null) {
            appConfig.getEventFeeds().forEach(server -> {
                if (server.isAgent()) {
                    fluxtionServer.registerWorkerService(server.toServiceAgent());
                } else {
                    fluxtionServer.registerService(server.toService());
                }
            });
        }

        //event sinks eventSinks
        if (appConfig.getEventSinks() != null) {
            appConfig.getEventSinks().forEach(server -> {
                if (server.isAgent()) {
                    fluxtionServer.registerWorkerService(server.toServiceAgent());
                } else {
                    fluxtionServer.registerService(server.toService());
                }
            });
        }

        //service
        if (appConfig.getServices() != null) {
            for (ServiceConfig<?> serviceConfig : appConfig.getServices()) {
                if (serviceConfig.isAgent()) {
                    fluxtionServer.registerWorkerService(serviceConfig.toServiceAgent());
                } else {
                    fluxtionServer.registerService(serviceConfig.toService());
                }
            }
        }

        //add market maker processors
        if (appConfig.getEventHandlers() != null) {
            appConfig.getEventHandlers().forEach(cfg -> {
                final EventLogControlEvent.LogLevel defaultLogLevel = cfg.getLogLevel() == null ? EventLogControlEvent.LogLevel.INFO : cfg.getLogLevel();
                String groupName = cfg.getAgentName();
                IdleStrategy idleStrategy1 = cfg.getIdleStrategy();
                IdleStrategy idleStrategy = appConfig.lookupIdleStrategyWhenNull(idleStrategy1, cfg.getAgentName());
                cfg.getEventHandlers().entrySet().forEach(handlerEntry -> {
                    String name = handlerEntry.getKey();
                    try {
                        fluxtionServer.addEventProcessor(
                                name,
                                groupName,
                                idleStrategy,
                                () -> {
                                    log.info("adding eventProcessor:" + name + " to group:" + groupName + ", idleStrategy:" + idleStrategy);
                                    EventProcessorConfig<?> eventProcessorConfig = handlerEntry.getValue();
                                    var eventProcessor = eventProcessorConfig.getEventHandler() == null
                                            ? eventProcessorConfig.getEventHandlerBuilder().get()
                                            : eventProcessorConfig.getEventHandler();
                                    var logLevel = eventProcessorConfig.getLogLevel() == null ? defaultLogLevel : eventProcessorConfig.getLogLevel();
                                    @SuppressWarnings("unckecked")
                                    ConfigMap configMap = eventProcessorConfig.getConfig();

                                    eventProcessor.setAuditLogProcessor(logRecordListener);
                                    eventProcessor.setAuditLogLevel(logLevel);
                                    eventProcessor.init();

                                    eventProcessor.consumeServiceIfExported(ConfigListener.class, l -> l.initialConfig(configMap));
                                    return eventProcessor;
                                });
                    } catch (Exception e) {
                        log.warning("could not add eventProcessor:" + name + " to group:" + groupName + " error:" + e.getMessage());
                    }
                });
            });
        }

        //start
        fluxtionServer.init();
        fluxtionServer.start();

        //success
        return fluxtionServer;
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
                throw new IllegalArgumentException("cannot register service name is already assigned:" + serviceName);
            }
            registeredServices.put(serviceName, service);
            Object instance = service.instance();
            //TODO set service name if not an EventFlow service
            if (instance instanceof com.fluxtion.server.dispatch.EventFlowService) {
                ((EventFlowService) instance).setEventFlowManager(flowManager, serviceName);
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
        log.info("init");
        registeredServices.values().forEach(svc -> {
            if (!(svc.instance() instanceof com.fluxtion.server.dispatch.LifeCycleEventSource)) {
                svc.init();
            }
        });

        flowManager.init();

        registeredServices.values().forEach(svc -> {
            if (!(registeredAgentServices.contains(svc))) {
                serviceRegistry.nodeRegistered(svc.instance(), svc.serviceName());
                servicesRegistered().forEach(serviceRegistry::registerService);
            }
        });

    }

    @SneakyThrows
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

        log.info("start agent hosted services");
        composingServiceAgents.forEach((k, v) -> {
            log.info("starting composing service agent " + k);
            AgentRunner.startOnThread(v.getGroupRunner());
        });

        boolean waiting = true;
        log.info("waiting for agent hosted services to start");
        while (waiting) {
            waiting = composingServiceAgents.values().stream()
                    .anyMatch(f -> f.group.status() != DynamicCompositeAgent.Status.ACTIVE);
            Thread.sleep(10);
            log.finer("checking all service agents are started");
        }

        log.info("start event processor agent workers");
        composingEventProcessorAgents.forEach((k, v) -> {
            log.info("starting composing event processor agent " + k);
            AgentRunner.startOnThread(v.getGroupRunner());
        });

        waiting = true;
        log.info("waiting for event processor agents to start");
        while (waiting) {
            waiting = composingEventProcessorAgents.values().stream()
                    .anyMatch(f -> {
                        DynamicCompositeAgent.Status status = f.group.status();
                        return status != DynamicCompositeAgent.Status.ACTIVE;
                    });
            Thread.sleep(10);
            log.finer("checking all processor agents are started");
        }

        log.info("calling startup complete on services");
        for (Service<?> service : registeredServices.values()) {
            if (!registeredAgentServices.contains(service)) {
                service.startComplete();
            }
        }

        log.info("calling startup complete on agent hosted services");
        composingServiceAgents.values()
                .stream()
                .map(ComposingWorkerServiceAgentRunner::getGroup)
                .forEach(ComposingServiceAgent::startComplete);

        started = true;
        log.info("start complete");
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
        log.info("stopping server");

        if (!started) {
            log.info("server not started, nothing to stop");
            return;
        }

        log.info("stopping event processor agents");
        composingEventProcessorAgents.forEach((k, v) -> {
            log.info("stopping composing event processor agent " + k);
            AgentRunner groupRunner = v.getGroupRunner();
            if (groupRunner.thread() != null) {
                groupRunner.close();
            }
        });

        log.info("stopping agent hosted services");
        composingServiceAgents.forEach((k, v) -> {
            log.info("stopping composing service agent " + k);
            AgentRunner groupRunner = v.getGroupRunner();
            if (groupRunner.thread() != null) {
                groupRunner.close();
            }
        });

        log.info("stopping flowManager");
        // No explicit stop method in flowManager, but we can stop all services

        log.info("stopping registered services");
        for (Service<?> service : registeredServices.values()) {
            if (!(service.instance() instanceof com.fluxtion.server.dispatch.LifeCycleEventSource)) {
                service.stop();
            }
        }

        started = false;
        log.info("server stopped");
    }

    @ServiceRegistered
    public void adminClient(AdminCommandRegistry adminCommandRegistry, String name) {
        log.info("adminCommandRegistry registered:" + name);
    }

    @Value
    private static class ComposingEventProcessorAgentRunner {
        ComposingEventProcessorAgent group;
        AgentRunner groupRunner;
    }

    @Value
    private static class ComposingWorkerServiceAgentRunner {
        ComposingServiceAgent group;
        AgentRunner groupRunner;
    }
}
