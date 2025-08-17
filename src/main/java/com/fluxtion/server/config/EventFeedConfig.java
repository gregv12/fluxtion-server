/*
 * SPDX-FileCopyrightText: © 2025 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.fluxtion.server.config;

import com.fluxtion.agrona.concurrent.Agent;
import com.fluxtion.agrona.concurrent.IdleStrategy;
import com.fluxtion.runtime.annotations.feature.Experimental;
import com.fluxtion.runtime.input.NamedFeed;
import com.fluxtion.runtime.service.Service;
import com.fluxtion.server.dutycycle.ServiceAgent;
import com.fluxtion.server.service.EventSource;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;

import java.util.function.Function;

@Experimental
@Data
@AllArgsConstructor
@NoArgsConstructor
public class EventFeedConfig<T> {

    private T instance;
    private String name;
    private boolean broadcast = false;
    private boolean wrapWithNamedEvent = false;
    //event feed management
    private EventSource.EventWrapStrategy eventWrapStrategy = EventSource.EventWrapStrategy.SUBSCRIPTION_NAMED_EVENT;
    private EventSource.SlowConsumerStrategy slowConsumerStrategy = EventSource.SlowConsumerStrategy.BACKOFF;
    private Function<T, ?> valueMapper = Function.identity();
    //optional agent configurationx
    private String agentName;
    private IdleStrategy idleStrategy;

    public boolean isAgent() {
        return agentName != null;
    }

    @SneakyThrows
    public Service<NamedFeed> toService() {
        if (instance instanceof EventSource<?> eventSource) {
            if (wrapWithNamedEvent & broadcast) {
                eventWrapStrategy = EventSource.EventWrapStrategy.BROADCAST_NAMED_EVENT;
            } else if (!wrapWithNamedEvent & broadcast) {
                eventWrapStrategy = EventSource.EventWrapStrategy.BROADCAST_NOWRAP;
            } else if (wrapWithNamedEvent & !broadcast) {
                eventWrapStrategy = EventSource.EventWrapStrategy.SUBSCRIPTION_NAMED_EVENT;
            } else {
                eventWrapStrategy = EventSource.EventWrapStrategy.SUBSCRIPTION_NOWRAP;
            }
            @SuppressWarnings("unchecked") EventSource<T> eventSource_t = (EventSource<T>) eventSource;
            eventSource_t.setEventWrapStrategy(eventWrapStrategy);
            eventSource_t.setSlowConsumerStrategy(slowConsumerStrategy);
            eventSource_t.setDataMapper(valueMapper);
        }
        Service<NamedFeed> svc = new Service<>((NamedFeed) instance, NamedFeed.class, name);
        return svc;
    }

    @SneakyThrows
    public ServiceAgent<NamedFeed> toServiceAgent() {
        Service<NamedFeed> svc = toService();
        if (!(instance instanceof Agent a)) {
            throw new IllegalArgumentException("Configured instance is not an Agent: " + instance);
        }
        return new ServiceAgent<>(agentName, idleStrategy, svc, a);
    }

    // -------- Builder API --------
    public static <T> Builder<T> builder() {
        return new Builder<>();
    }

    public static final class Builder<T> {
        private T instance;
        private String name;
        private boolean broadcast;
        private boolean wrapWithNamedEvent;
        private EventSource.SlowConsumerStrategy slowConsumerStrategy;
        private Function<T, ?> valueMapper;
        private String agentName;
        private IdleStrategy idleStrategy;

        private Builder() {
        }

        public Builder<T> instance(T instance) {
            this.instance = instance;
            return this;
        }

        public Builder<T> name(String name) {
            this.name = name;
            return this;
        }

        public Builder<T> broadcast(boolean broadcast) {
            this.broadcast = broadcast;
            return this;
        }

        public Builder<T> wrapWithNamedEvent(boolean wrap) {
            this.wrapWithNamedEvent = wrap;
            return this;
        }

        public Builder<T> slowConsumerStrategy(EventSource.SlowConsumerStrategy strategy) {
            this.slowConsumerStrategy = strategy;
            return this;
        }

        public Builder<T> valueMapper(Function<T, ?> mapper) {
            this.valueMapper = mapper;
            return this;
        }

        public Builder<T> agent(String agentName, IdleStrategy idleStrategy) {
            this.agentName = agentName;
            this.idleStrategy = idleStrategy;
            return this;
        }

        public EventFeedConfig<T> build() {
            EventFeedConfig<T> cfg = new EventFeedConfig<>();
            cfg.setInstance(instance);
            cfg.setName(name);
            cfg.setBroadcast(broadcast);
            cfg.setWrapWithNamedEvent(wrapWithNamedEvent);
            if (slowConsumerStrategy != null) cfg.setSlowConsumerStrategy(slowConsumerStrategy);
            if (valueMapper != null) cfg.setValueMapper(valueMapper);
            cfg.setAgentName(agentName);
            cfg.setIdleStrategy(idleStrategy);
            return cfg;
        }
    }
}
