/*
 * SPDX-FileCopyrightText: © 2025 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.fluxtion.server.config;

import com.fluxtion.runtime.EventProcessor;
import com.fluxtion.runtime.audit.EventLogControlEvent;
import com.fluxtion.runtime.node.ObjectEventHandlerNode;
import com.fluxtion.server.internal.ConfigAwareEventProcessor;
import lombok.AccessLevel;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

@Data
public class EventProcessorConfig<T extends EventProcessor<?>> {
    private T eventHandler;
    private ObjectEventHandlerNode customHandler;
    private Supplier<T> eventHandlerBuilder;
    private EventLogControlEvent.LogLevel logLevel;
    private Map<String, Object> configMap = new HashMap<>();

    public EventProcessorConfig(ObjectEventHandlerNode customHandler) {
        this.customHandler = customHandler;
    }

    public EventProcessorConfig() {
    }

    @SuppressWarnings({"unchecked"})
    public T getEventHandler() {
        if (eventHandler == null && customHandler != null) {
            ConfigAwareEventProcessor wrappingProcessor = new ConfigAwareEventProcessor(customHandler);
            eventHandler = (T) wrappingProcessor;
        }
        return eventHandler;
    }

    public ConfigMap getConfig() {
        return new ConfigMap(getConfigMap());
    }

    // -------- Builder API --------
    public static <T extends EventProcessor<?>> Builder<T> builder() {
        return new Builder<>();
    }

    public static final class Builder<T extends EventProcessor<?>> {
        private T eventHandler;
        private ObjectEventHandlerNode customHandler;
        private Supplier<T> eventHandlerBuilder;
        private EventLogControlEvent.LogLevel logLevel;
        private final Map<String, Object> config = new HashMap<>();

        private Builder() {
        }

        public Builder<T> handler(T handler) {
            this.eventHandler = handler;
            return this;
        }

        public Builder<T> customHandler(ObjectEventHandlerNode node) {
            this.customHandler = node;
            return this;
        }

        public Builder<T> handlerBuilder(Supplier<T> builder) {
            this.eventHandlerBuilder = builder;
            return this;
        }

        public Builder<T> logLevel(EventLogControlEvent.LogLevel level) {
            this.logLevel = level;
            return this;
        }

        public Builder<T> putConfig(String key, Object value) {
            this.config.put(key, value);
            return this;
        }

        public EventProcessorConfig<T> build() {
            EventProcessorConfig<T> cfg = new EventProcessorConfig<>();
            cfg.setEventHandler(eventHandler);
            cfg.setCustomHandler(customHandler);
            cfg.setEventHandlerBuilder(eventHandlerBuilder);
            cfg.setLogLevel(logLevel);
            cfg.getConfigMap().putAll(config);
            return cfg;
        }
    }
}
