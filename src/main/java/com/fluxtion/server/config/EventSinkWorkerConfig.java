/*
 * SPDX-FileCopyrightText: © 2024 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.fluxtion.server.config;

import com.fluxtion.agrona.concurrent.Agent;
import com.fluxtion.agrona.concurrent.IdleStrategy;
import com.fluxtion.agrona.concurrent.YieldingIdleStrategy;
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
public class EventSinkWorkerConfig<T extends MessageSink<T> & Agent> {

    private T instance;
    private String name;
    private Function<? super T, ?> valueMapper = Function.identity();
    //do work thread management
    private String agentGroup;
    private IdleStrategy idleStrategy = new YieldingIdleStrategy();

    @SneakyThrows
    @SuppressWarnings({"unchecked", "all"})
    public ServiceAgent<T> toServiceAgent() {
        instance.setValueMapper(valueMapper);
        Service svc = new Service(instance, MessageSink.class, name);
        return new ServiceAgent<>(agentGroup, idleStrategy, svc, instance);
    }
}
