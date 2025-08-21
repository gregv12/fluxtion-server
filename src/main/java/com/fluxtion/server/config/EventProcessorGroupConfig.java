/*
 * SPDX-FileCopyrightText: © 2025 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.fluxtion.server.config;

import com.fluxtion.agrona.concurrent.IdleStrategy;
import com.fluxtion.runtime.audit.EventLogControlEvent;
import lombok.Data;

import java.util.Map;

@Data
public class EventProcessorGroupConfig {
    private String agentName;
    private IdleStrategy idleStrategy;
    private EventLogControlEvent.LogLevel logLevel;
    private Map<String, EventProcessorConfig<?>> eventHandlers;

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String agentName;
        private IdleStrategy idleStrategy;
        private EventLogControlEvent.LogLevel logLevel;
        private Map<String, EventProcessorConfig<?>> eventHandlers;

        private Builder() {
        }

        public Builder agentName(String agentName) {
            this.agentName = agentName;
            return this;
        }

        public Builder idleStrategy(IdleStrategy idleStrategy) {
            this.idleStrategy = idleStrategy;
            return this;
        }

        public Builder logLevel(EventLogControlEvent.LogLevel logLevel) {
            this.logLevel = logLevel;
            return this;
        }

        public Builder eventHandlers(Map<String, EventProcessorConfig<?>> handlers) {
            this.eventHandlers = handlers;
            return this;
        }

        public Builder put(String name, EventProcessorConfig<?> cfg) {
            if (this.eventHandlers == null) this.eventHandlers = new java.util.HashMap<>();
            this.eventHandlers.put(name, cfg);
            return this;
        }

        public EventProcessorGroupConfig build() {
            EventProcessorGroupConfig cfg = new EventProcessorGroupConfig();
            cfg.setAgentName(agentName);
            cfg.setIdleStrategy(idleStrategy);
            cfg.setLogLevel(logLevel);
            cfg.setEventHandlers(eventHandlers);
            return cfg;
        }
    }
}
