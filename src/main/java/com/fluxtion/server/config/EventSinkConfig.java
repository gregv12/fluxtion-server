/*
 * SPDX-FileCopyrightText: © 2025 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.fluxtion.server.config;

import com.fluxtion.agrona.concurrent.Agent;
import com.fluxtion.agrona.concurrent.IdleStrategy;
import com.fluxtion.runtime.annotations.feature.Experimental;
import com.fluxtion.runtime.output.MessageSink;
import com.fluxtion.runtime.service.Service;
import com.fluxtion.server.dutycycle.ServiceAgent;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;

import java.util.function.Function;

@Experimental
@Data
@AllArgsConstructor
@NoArgsConstructor
public class EventSinkConfig<S extends MessageSink<?>> {

    private S instance;
    private String name;
    private Function<Object, ?> valueMapper = Function.identity();
    //optional agent configuration
    private String agentName;
    private IdleStrategy idleStrategy;

    public boolean isAgent() {
        return agentName != null;
    }

    @SneakyThrows
    @SuppressWarnings({"unchecked", "all"})
    public Service<S> toService() {
        ((MessageSink<Object>) instance).setValueMapper(valueMapper);
        Service svc = new Service(instance, MessageSink.class, name);
        return svc;
    }

    @SneakyThrows
    @SuppressWarnings({"unchecked", "all"})
    public <A extends Agent> ServiceAgent<A> toServiceAgent() {
        Service svc = toService();
        return new ServiceAgent<>(agentName, idleStrategy, svc, (A) instance);
    }

    // -------- Builder API --------
    public static <S extends MessageSink<?>> Builder<S> builder() {
        return new Builder<>();
    }

    public static final class Builder<S extends MessageSink<?>> {
        private S instance;
        private String name;
        private Function<Object, ?> valueMapper;
        private String agentName;
        private IdleStrategy idleStrategy;

        private Builder() {
        }

        public Builder<S> instance(S instance) {
            this.instance = instance;
            return this;
        }

        public Builder<S> name(String name) {
            this.name = name;
            return this;
        }

        public Builder<S> valueMapper(Function<Object, ?> mapper) {
            this.valueMapper = mapper;
            return this;
        }

        public Builder<S> agent(String agentName, IdleStrategy idleStrategy) {
            this.agentName = agentName;
            this.idleStrategy = idleStrategy;
            return this;
        }

        public EventSinkConfig<S> build() {
            EventSinkConfig<S> cfg = new EventSinkConfig<>();
            cfg.setInstance(instance);
            cfg.setName(name);
            if (valueMapper != null) cfg.setValueMapper(valueMapper);
            cfg.setAgentName(agentName);
            cfg.setIdleStrategy(idleStrategy);
            return cfg;
        }
    }
}
