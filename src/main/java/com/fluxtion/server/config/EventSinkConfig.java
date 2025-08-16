/*
 * SPDX-FileCopyrightText: © 2024 Gregory Higgins <greg.higgins@v12technology.com>
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
public class EventSinkConfig<T extends MessageSink<T>> {

    private T instance;
    private String name;
    private Function<? super T, ?> valueMapper = Function.identity();
    //optional agent configuration
    private String agentName;
    private IdleStrategy idleStrategy;

    public boolean isAgent() {
        return agentName != null;
    }

    @SneakyThrows
    @SuppressWarnings({"unchecked", "all"})
    public Service<T> toService() {
        instance.setValueMapper(valueMapper);
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
    public static <T extends MessageSink<T>> Builder<T> builder() { return new Builder<>(); }

    public static final class Builder<T extends MessageSink<T>> {
        private T instance;
        private String name;
        private Function<? super T, ?> valueMapper;
        private String agentName;
        private IdleStrategy idleStrategy;

        private Builder() {}
        public Builder<T> instance(T instance) { this.instance = instance; return this; }
        public Builder<T> name(String name) { this.name = name; return this; }
        public Builder<T> valueMapper(Function<? super T, ?> mapper) { this.valueMapper = mapper; return this; }
        public Builder<T> agent(String agentName, IdleStrategy idleStrategy) { this.agentName = agentName; this.idleStrategy = idleStrategy; return this; }
        public EventSinkConfig<T> build() {
            EventSinkConfig<T> cfg = new EventSinkConfig<>();
            cfg.setInstance(instance);
            cfg.setName(name);
            if (valueMapper != null) cfg.setValueMapper(valueMapper);
            cfg.setAgentName(agentName);
            cfg.setIdleStrategy(idleStrategy);
            return cfg;
        }
    }
}
