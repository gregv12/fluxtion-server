/*
 * SPDX-FileCopyrightText: © 2024 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 */

package com.fluxtion.server.config;

import com.fluxtion.runtime.annotations.feature.Experimental;
import com.fluxtion.runtime.service.Service;
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

    public ServiceConfig(T service, Class<T> serviceClass, String name) {
        this(service, serviceClass.getCanonicalName(), name);
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

    @SneakyThrows
    @SuppressWarnings("unchecked")
    public Service<T> toService() {
        Class<T> serviceClazz = (Class<T>) (serviceClass == null ? service.getClass() : Class.forName(serviceClass));
        serviceClass = serviceClazz.getCanonicalName();
        return new Service<>(service, serviceClazz, name == null ? serviceClass : name);
    }
}
