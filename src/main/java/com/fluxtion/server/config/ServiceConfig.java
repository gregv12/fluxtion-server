/*
 * SPDX-FileCopyrightText: © 2024 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
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
import lombok.experimental.Accessors;

@Experimental
@Data
@AllArgsConstructor
@NoArgsConstructor
@Accessors(chain = true, fluent = true)
public class ServiceConfig<T> {

    private T service;
    private String serviceClass;
    private String name;
    //optional agent configuration
    private String agentGroup;
    private IdleStrategy idleStrategy = new YieldingIdleStrategy();

    public ServiceConfig(T service, Class<T> serviceClass, String name) {
        this(service, serviceClass.getCanonicalName(), name, null, null);
    }

    public boolean isAgent() {
        return agentGroup != null;
    }

    public void setService(T service) {
        this.service = service;
    }

    public void setServiceClass(String serviceClass) {
        this.serviceClass = serviceClass;
    }

    public void setName(String name) {
        this.name = name;
    }

    public T getService() {
        return service;
    }

    public String getServiceClass() {
        return serviceClass;
    }

    public String getName() {
        return name;
    }

    public String getAgentGroup() {
        return agentGroup;
    }

    public void setAgentGroup(String agentGroup) {
        this.agentGroup = agentGroup;
    }

    public IdleStrategy getIdleStrategy() {
        return idleStrategy;
    }

    public void setIdleStrategy(IdleStrategy idleStrategy) {
        this.idleStrategy = idleStrategy;
    }

    @SneakyThrows
    @SuppressWarnings("unchecked")
    public Service<T> toService() {
        Class<T> serviceClazz = (Class<T>) (serviceClass == null ? service.getClass() : Class.forName(serviceClass));
        serviceClass = serviceClazz.getCanonicalName();
        return new Service<>(service, serviceClazz, name == null ? serviceClass : name);
    }

    @SneakyThrows
    @SuppressWarnings({"unchecked", "all"})
    public <A extends Agent> ServiceAgent<A> toServiceAgent() {
        Service svc = toService();
        return new ServiceAgent<>(agentGroup, idleStrategy, svc, (A) service);
    }
}
