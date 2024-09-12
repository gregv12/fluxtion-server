/*
 * SPDX-FileCopyrightText: © 2024 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 */

package com.fluxtion.server.config;

import com.fluxtion.agrona.concurrent.Agent;
import com.fluxtion.agrona.concurrent.IdleStrategy;
import com.fluxtion.agrona.concurrent.YieldingIdleStrategy;
import com.fluxtion.runtime.annotations.feature.Experimental;
import com.fluxtion.runtime.service.Service;
import com.fluxtion.server.dutycycle.ServiceAgent;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;

@Experimental
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ServiceWorkerConfig<T extends Agent> {

    private T instance;
    private String serviceClass;
    private String name;
    //do work thread management
    private String agentGroup;
    private IdleStrategy idleStrategy = new YieldingIdleStrategy();

    @SneakyThrows
    @SuppressWarnings("unchecked")
    public ServiceAgent<T> toServiceAgent() {
        Class<T> serviceClazz = (Class<T>) (serviceClass == null ? instance.getClass() : Class.forName(serviceClass));
        serviceClass = serviceClazz.getCanonicalName();
        Service<T> svc = new Service<>(instance, serviceClazz, name == null ? serviceClass : name);
        return new ServiceAgent<>(agentGroup, idleStrategy, svc, instance);
    }
}
