/*
 * SPDX-FileCopyrightText: © 2025 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.fluxtion.server.config;

import com.fluxtion.agrona.concurrent.BusySpinIdleStrategy;
import com.fluxtion.agrona.concurrent.IdleStrategy;
import com.fluxtion.agrona.concurrent.YieldingIdleStrategy;
import com.fluxtion.runtime.EventProcessor;
import lombok.Data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

@Data
public class AppConfig {
    //event handler
    private List<EventProcessorGroupConfig> eventHandlers;

    //event feeds
    private List<EventFeedConfig<?>> eventFeeds;

    //event sink
    private List<EventSinkConfig<?>> eventSinks;

    //services
    private List<ServiceConfig<?>> services;

    //agent thread config
    private List<ThreadConfig> agentThreads;

    private IdleStrategy idleStrategy = new YieldingIdleStrategy();
    private EventProcessorGroupConfig defaultHandlerGroupConfig;

    /**
     * Gets the list of event handlers, initializing if null and adding defaultHandlerGroupConfig if provided.
     *
     * @return the list of EventProcessorGroupConfig
     */
    public List<EventProcessorGroupConfig> getEventHandlers() {
        if (eventHandlers == null) {
            eventHandlers = new ArrayList<>();
        }

        if (defaultHandlerGroupConfig != null && !eventHandlers.contains(defaultHandlerGroupConfig)) {
            eventHandlers.add(defaultHandlerGroupConfig);
        }

        return eventHandlers;
    }

    public <T> AppConfig addEventSource(T eventSource, String name, boolean isBroadcast) {
        if (eventFeeds == null) {
            eventFeeds = new ArrayList<>();
        }

        EventFeedConfig<T> eventFeedConfig = new EventFeedConfig<>();
        eventFeedConfig.setInstance(eventSource);
        eventFeedConfig.setName(name);
        eventFeedConfig.setBroadcast(isBroadcast);

        eventFeeds.add(eventFeedConfig);

        return this;
    }

    /**
     * Return an {@link IdleStrategy}, looking up from config if the supplied strategy is null
     *
     * @param preferredIdeIdleStrategy the preferred idle strategy to use
     * @param agentName                The name of the agent in {@link ThreadConfig}
     * @return An IdleStrategy
     */
    public IdleStrategy lookupIdleStrategyWhenNull(IdleStrategy preferredIdeIdleStrategy, String agentName) {
        if (preferredIdeIdleStrategy == null && agentThreads == null) {
            return idleStrategy;
        } else if (preferredIdeIdleStrategy == null) {
            return agentThreads.stream().filter(cfg -> cfg.getAgentName().equals(agentName))
                    .findFirst()
                    .map(ThreadConfig::getIdleStrategy)
                    .orElse(new YieldingIdleStrategy());
        }
        return preferredIdeIdleStrategy;
    }

    /**
     * @param agentName              The name of the agent in {@link ThreadConfig}
     * @param defaultIdeIdleStrategy the default idle strategy to use if no match found in {@link ThreadConfig}
     * @return An IdleStrategy
     */
    public IdleStrategy getIdleStrategyOrDefault(String agentName, IdleStrategy defaultIdeIdleStrategy) {
        if (defaultIdeIdleStrategy == null && agentThreads == null) {
            return idleStrategy;
        }
        if (agentThreads == null) {
            return defaultIdeIdleStrategy;
        }
        var idleStrategy = agentThreads.stream().filter(cfg -> cfg.getAgentName().equals(agentName))
                .findFirst()
                .map(ThreadConfig::getIdleStrategy)
                .orElse(defaultIdeIdleStrategy);
        return idleStrategy == null ? defaultIdeIdleStrategy : idleStrategy;
    }

    /**
     * Adds an EventProcessor to a default EventProcessorGroupConfig instance.
     * Creates a new EventProcessorGroupConfig if none exists in eventHandlers.
     * If eventHandlers list is null, initializes it first.
     *
     * @param processor the EventProcessor to add
     * @return the EventProcessorGroupConfig with the added processor
     */
    public <T extends EventProcessor<?>> EventProcessorGroupConfig addProcessor(T processor, String name) {
        if (defaultHandlerGroupConfig == null) {
            defaultHandlerGroupConfig = new EventProcessorGroupConfig();
            defaultHandlerGroupConfig.setAgentName("defaultHandlerGroup");
            defaultHandlerGroupConfig.setEventHandlers(new HashMap<>());
            defaultHandlerGroupConfig.setIdleStrategy(new BusySpinIdleStrategy());
        }

        EventProcessorConfig<T> processorConfig = new EventProcessorConfig<>();
        processorConfig.setEventHandler(processor);
        defaultHandlerGroupConfig.getEventHandlers().put(name, processorConfig);

        return defaultHandlerGroupConfig;
    }

    // ---- Improved service registration API ----

    /**
     * Add a simple service by instance and name. The service interface is inferred from the instance class.
     */
    public <T> AppConfig addService(T service, String name) {
        if (services == null) {
            services = new ArrayList<>();
        }
        @SuppressWarnings("unchecked") Class<T> clazz = (Class<T>) service.getClass();
        ServiceConfig<T> cfg = new ServiceConfig<>(service, clazz, name);
        services.add(cfg);
        return this;
    }

    /**
     * Add a service by instance, explicit service interface class, and name.
     */
    public <T> AppConfig addService(T service, Class<T> serviceClass, String name) {
        if (services == null) {
            services = new ArrayList<>();
        }
        ServiceConfig<T> cfg = new ServiceConfig<>(service, serviceClass, name);
        services.add(cfg);
        return this;
    }

    /**
     * Add a worker (agent-backed) service supplying the agent group and optional idle strategy.
     */
    public <T> AppConfig addWorkerService(T service, Class<T> serviceClass, String name, String agentGroup, IdleStrategy idleStrategy) {
        if (services == null) {
            services = new ArrayList<>();
        }
        ServiceConfig<T> cfg = new ServiceConfig<>(service, serviceClass, name);
        cfg.setAgentGroup(agentGroup);
        cfg.setIdleStrategy(idleStrategy);
        services.add(cfg);
        return this;
    }

    /**
     * Add a worker (agent-backed) service inferring service class.
     */
    public <T> AppConfig addWorkerService(T service, String name, String agentGroup, IdleStrategy idleStrategy) {
        if (services == null) {
            services = new ArrayList<>();
        }
        @SuppressWarnings("unchecked") Class<T> clazz = (Class<T>) service.getClass();
        ServiceConfig<T> cfg = new ServiceConfig<>(service, clazz, name);
        cfg.setAgentGroup(agentGroup);
        cfg.setIdleStrategy(idleStrategy);
        services.add(cfg);
        return this;
    }

    // -------- Builder API --------
    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private final List<EventProcessorGroupConfig> eventHandlers = new ArrayList<>();
        private final List<EventFeedConfig<?>> eventFeeds = new ArrayList<>();
        private final List<EventSinkConfig<?>> eventSinks = new ArrayList<>();
        private final List<ServiceConfig<?>> services = new ArrayList<>();
        private final List<ThreadConfig> agentThreads = new ArrayList<>();
        private IdleStrategy idleStrategy;

        private Builder() {}
        public Builder idleStrategy(IdleStrategy idleStrategy) { this.idleStrategy = idleStrategy; return this; }
        public Builder addGroup(EventProcessorGroupConfig group) { this.eventHandlers.add(group); return this; }
        public Builder addEventFeed(EventFeedConfig<?> feed) { this.eventFeeds.add(feed); return this; }
        public Builder addEventSink(EventSinkConfig<?> sink) { this.eventSinks.add(sink); return this; }
        public Builder addService(ServiceConfig<?> svc) { this.services.add(svc); return this; }
        public Builder addThread(ThreadConfig thread) { this.agentThreads.add(thread); return this; }
        public AppConfig build() {
            AppConfig cfg = new AppConfig();
            if (!eventHandlers.isEmpty()) cfg.setEventHandlers(new ArrayList<>(eventHandlers));
            if (!eventFeeds.isEmpty()) cfg.setEventFeeds(new ArrayList<>(eventFeeds));
            if (!eventSinks.isEmpty()) cfg.setEventSinks(new ArrayList<>(eventSinks));
            if (!services.isEmpty()) cfg.setServices(new ArrayList<>(services));
            if (!agentThreads.isEmpty()) cfg.setAgentThreads(new ArrayList<>(agentThreads));
            if (idleStrategy != null) cfg.setIdleStrategy(idleStrategy);
            return cfg;
        }
    }
}