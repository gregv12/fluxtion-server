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
import com.fluxtion.runtime.annotations.runtime.ServiceRegistered;
import com.fluxtion.runtime.audit.LogRecordListener;
import com.fluxtion.runtime.service.Service;
import com.fluxtion.runtime.service.ServiceRegistryNode;
import com.fluxtion.server.config.AppConfig;
import com.fluxtion.server.dispatch.CallBackType;
import com.fluxtion.server.dispatch.EventFlowService;
import com.fluxtion.server.dispatch.EventSource;
import com.fluxtion.server.dispatch.EventToInvokeStrategy;
import com.fluxtion.server.dutycycle.ComposingEventProcessorAgent;
import com.fluxtion.server.dutycycle.ComposingServiceAgent;
import com.fluxtion.server.dutycycle.NamedEventProcessor;
import com.fluxtion.server.dutycycle.ServiceAgent;
import com.fluxtion.server.internal.ComposingEventProcessorAgentRunner;
import com.fluxtion.server.internal.ComposingWorkerServiceAgentRunner;
import com.fluxtion.server.internal.LifecycleManager;
import com.fluxtion.server.internal.ServiceInjector;
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


/**
 * FluxtionServer is the central runtime that wires together event sources, event processors,
 * event sinks, and application services, and manages their lifecycle and threading model.
 * <p>
 * High-level architecture:
 * <ul>
 *   <li><b>Services</b>: Application components registered with the server. They may be standard
 *       services (executed in the server lifecycle) or <b>agent-hosted</b> worker services
 *       (executed on dedicated agent threads). Services can depend on each other via dependency
 *       injection. Services that participate directly in event routing can implement an event-flow
 *       contract to interact with the event flow manager.</li>
 *   <li><b>Agent-hosted services</b>: Worker services executed on their own {@code AgentRunner}.
 *       Each worker service is associated with an agent group and an {@code IdleStrategy}.
 *       Idle strategies can be provided directly or resolved from configuration using the group name.</li>
 *   <li><b>Event sources (feeds)</b>: Producers of events registered with the server. The event flow
 *       manager routes events from sources to interested processors, using mapping/dispatch strategies
 *       that can be customized.</li>
 *   <li><b>Event processors</b>: Instances grouped by a logical <i>processor group</i>. Each group runs
 *       on its own {@code AgentRunner} with a configurable {@code IdleStrategy}. Processors are registered
 *       by name within a group, and audit logging hooks can be attached. Groups provide isolation and
 *       parallelism.</li>
 *   <li><b>Event sinks</b>: Consumers of processed events. Sinks are typically modeled as services
 *       (standard or agent-hosted) and participate in the event flow by receiving output from processors
 *       or other services.</li>
 * </ul>
 * <p>
 * Lifecycle and management:
 * <ul>
 *   <li>{@link #init()} performs initialization: registers services, prepares agent groups, and wires the
 *       event flow manager and service registry.</li>
 *   <li>{@link #start()} launches agent groups (for processors and worker services) using {@code AgentRunner}
 *       with the resolved idle strategies, then starts services and marks the server as running.</li>
 *   <li>{@link #stop()} stops all agent groups and services and releases resources.</li>
 *   <li>At runtime you can query and control components (e.g., {@link #registeredProcessors()},
 *       {@link #stopProcessor(String, String)}, {@link #startService(String)}, {@link #stopService(String)}).</li>
 * </ul>
 * <p>
 * Configuration and bootstrapping:
 * <ul>
 *   <li>Servers can be bootstrapped from an {@link AppConfig}, a {@link java.io.Reader}, or from a YAML
 *       file path provided via the {@link #CONFIG_FILE_PROPERTY} system property.</li>
 *   <li>Threading policies are controlled via idle strategies that can be specified per agent group or
 *       fall back to a global default. These are resolved with helper methods in {@link AppConfig}.</li>
 *   <li>Custom event-to-callback mapping strategies can be registered to tailor routing behavior.</li>
 * </ul>
 * <p>
 * Error handling and observability:
 * <ul>
 *   <li>A default {@link com.fluxtion.agrona.ErrorHandler} can be supplied via {@link #setDefaultErrorHandler(ErrorHandler)}
 *       and is used by agent runners.</li>
 *   <li>Event processors can be bridged to a {@link LogRecordListener} for audit logging.</li>
 * </ul>
 * <p>
 * Typical usage pattern:
 * <ol>
 *   <li>Construct or load an {@link AppConfig}.</li>
 *   <li>Boot the server via one of the {@code bootServer(...)} overloads.</li>
 *   <li>Optionally register additional services, event sources, and event processors programmatically.</li>
 *   <li>Call {@link #init()} and {@link #start()} (when booting from config helpers, these may be invoked for you).</li>
 *   <li>Manage components at runtime and finally {@link #stop()} the server.</li>
 * </ol>
 */
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
    private final LifecycleManager lifecycleManager = new LifecycleManager(this);

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

    public void registerEventMapperFactory(Supplier<EventToInvokeStrategy> eventMapper, CallBackType type) {
        log.info("registerEventMapperFactory:" + eventMapper);
        flowManager.registerEventMapperFactory(eventMapper, type);
    }

    public <T> void registerEventSource(String sourceName, EventSource<T> eventSource) {
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
            ServiceInjector.inject(instance, registeredServices.values());
            // Also inject the newly registered service into existing services (single-target injection)
            for (Service<?> existing : registeredServices.values()) {
                Object existingInstance = existing.instance();
                if (existingInstance != instance) {
                    ServiceInjector.inject(existingInstance, java.util.Collections.singleton(service));
                }
            }
        }
    }

    public void registerAgentService(Service<?>... services) {
        registerService(services);
        registeredAgentServices.addAll(Arrays.asList(services));
    }

    /**
     * Register an agent-hosted (worker) service that runs on a dedicated agent thread.
     * <p>
     * The supplied {@link ServiceAgent} advertises an agent group name and may provide an
     * {@link IdleStrategy}. If the service's idle strategy is {@code null}, an appropriate
     * strategy is resolved from configuration using the agent group via
     * {@link AppConfig#lookupIdleStrategyWhenNull(IdleStrategy, String)}.
     * <p>
     * Services registered under the same agent group are executed within a shared
     * {@code AgentRunner}. If no runner exists for the group, it is created on demand.
     * The service is then registered with the group's composite agent.
     *
     * @param service the worker service to register and host on its agent group
     */
    public void registerWorkerService(ServiceAgent<?> service) {
        String agentGroup = service.agentGroup();
        IdleStrategy idleStrategy = appConfig.lookupIdleStrategyWhenNull(service.idleStrategy(), service.agentGroup());
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

        composingAgentRunner.group().registerServer(service);
    }

    /**
     * Add a named event processor to a processor group, creating the group on demand.
     * <p>
     * Each processor group executes on its own {@code AgentRunner} with a configurable
     * {@link IdleStrategy}. If a specific strategy is not supplied, a suitable strategy
     * is resolved from configuration via {@link AppConfig#getIdleStrategyOrDefault(String, IdleStrategy)}.
     * <p>
     * The processor name must be unique within its group; attempting to register a duplicate
     * name results in an {@link IllegalArgumentException}. When added, the processor is wrapped
     * as a {@link NamedEventProcessor} and configured with the current {@link LogRecordListener}
     * for audit logging. If the server is already started and the group's thread is not yet
     * running, the group is started.
     *
     * @param processorName unique name of the processor within the group
     * @param groupName     the logical processor group to host the processor
     * @param idleStrategy  optional idle strategy override for the group (may be {@code null})
     * @param feedConsumer  supplier creating the {@link StaticEventProcessor} instance
     * @throws IllegalArgumentException if a processor with {@code processorName} already exists in the group
     */
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

        if (composingEventProcessorAgentRunner.group().isProcessorRegistered(processorName)) {
            throw new IllegalArgumentException("cannot add event processor name is already assigned:" + processorName);
        }

        composingEventProcessorAgentRunner.group().addNamedEventProcessor(() -> {
            StaticEventProcessor eventProcessor = feedConsumer.get();
            eventProcessor.setAuditLogProcessor(logRecordListener);
            if (started) {
                log.info("init event processor in already started server processor:'" + eventProcessor + "'");
//                eventProcessor.setAuditLogLevel(EventLogControlEvent.LogLevel.INFO);
            }
            return new NamedEventProcessor(processorName, eventProcessor);
        });

        if (started && composingEventProcessorAgentRunner.groupRunner().thread() == null) {
            log.info("staring event processor group:'" + groupName + "' for running server");
            AgentRunner.startOnThread(composingEventProcessorAgentRunner.groupRunner());
        }
    }

    @Override
    public Map<String, Collection<NamedEventProcessor>> registeredProcessors() {
        HashMap<String, Collection<NamedEventProcessor>> result = new HashMap<>();
        composingEventProcessorAgents.entrySet().forEach(entry -> {
            result.put(entry.getKey(), entry.getValue().group().registeredEventProcessors());
        });
        return result;
    }

    @Override
    public void stopProcessor(String groupName, String processorName) {
        log.info("stopProcessor:" + processorName + " in group:" + groupName);
        var processorAgent = composingEventProcessorAgents.get(groupName);
        if (processorAgent != null) {
            processorAgent.group().removeEventProcessorByName(processorName);
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
        ConcurrentHashMap<String, LifecycleManager.GroupRunner> serviceGroups = new ConcurrentHashMap<>();
        composingServiceAgents.forEach((k, v) -> serviceGroups.put(k, new LifecycleManager.GroupRunner() {
            @Override
            public AgentRunner getGroupRunner() {
                return v.groupRunner();
            }

            @Override
            public DynamicCompositeAgent getGroup() {
                return v.group();
            }

            @Override
            public void startCompleteIfSupported() {
                v.group().startComplete();
            }
        }));
        ConcurrentHashMap<String, LifecycleManager.GroupRunner> processorGroups = new ConcurrentHashMap<>();
        composingEventProcessorAgents.forEach((k, v) -> processorGroups.put(k, new LifecycleManager.GroupRunner() {
            @Override
            public AgentRunner getGroupRunner() {
                return v.groupRunner();
            }

            @Override
            public DynamicCompositeAgent getGroup() {
                return v.group();
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

    private ConcurrentHashMap<String, LifecycleManager.GroupRunner> toGroupRunnerMap(java.util.concurrent.ConcurrentHashMap<String, ? extends Object> source) {
        ConcurrentHashMap<String, LifecycleManager.GroupRunner> map = new ConcurrentHashMap<>();
        source.forEach((k, v) -> {
            if (v instanceof ComposingEventProcessorAgentRunner cep) {
                map.put(k, new LifecycleManager.GroupRunner() {
                    @Override
                    public AgentRunner getGroupRunner() {
                        return cep.groupRunner();
                    }

                    @Override
                    public DynamicCompositeAgent getGroup() {
                        return cep.group();
                    }
                });
            } else if (v instanceof ComposingWorkerServiceAgentRunner cws) {
                map.put(k, new LifecycleManager.GroupRunner() {
                    @Override
                    public AgentRunner getGroupRunner() {
                        return cws.groupRunner();
                    }

                    @Override
                    public DynamicCompositeAgent getGroup() {
                        return cws.group();
                    }

                    @Override
                    public void startCompleteIfSupported() {
                        cws.group().startComplete();
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
