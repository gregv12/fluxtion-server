/*
 * SPDX-FileCopyrightText: © 2025 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.fluxtion.server.service.servercontrol;

import com.fluxtion.agrona.concurrent.IdleStrategy;
import com.fluxtion.runtime.StaticEventProcessor;
import com.fluxtion.runtime.service.Service;
import com.fluxtion.server.dutycycle.NamedEventProcessor;

import java.util.Collection;
import java.util.Map;
import java.util.function.Supplier;

/**
 * The FluxtionServerController interface provides control over the lifecycle and management
 * of event processors and services within a Fluxtion server instance. It allows for the
 * addition and removal of event processors, starting and stopping services, and querying
 * registered components.
 */
public interface FluxtionServerController {

    String SERVICE_NAME = "com.fluxtion.server.service.servercontrol.FluxtionServerController";

    void addEventProcessor(
            String processorName,
            String groupName,
            IdleStrategy idleStrategy,
            Supplier<StaticEventProcessor> feedConsumer) throws IllegalArgumentException;

    void stopService(String serviceName);

    Map<String, Service<?>> registeredServices();

    void startService(String serviceName);

    Map<String, Collection<NamedEventProcessor>> registeredProcessors();

    void stopProcessor(String groupName, String processorName);
}
