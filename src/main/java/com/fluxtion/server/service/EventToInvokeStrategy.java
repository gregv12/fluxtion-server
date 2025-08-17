/*
 * SPDX-FileCopyrightText: © 2025 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.fluxtion.server.service;

import com.fluxtion.runtime.StaticEventProcessor;

/**
 * Defines a strategy for processing events and dispatching them to {@link StaticEventProcessor} instances.
 * Implementations of this interface manage the registration and deregistration of processors,
 * as well as invoking the appropriate processing logic for incoming events.
 */
public interface EventToInvokeStrategy {

    void processEvent(Object event);

    void processEvent(Object event, long time);

    void registerProcessor(StaticEventProcessor eventProcessor);

    void deregisterProcessor(StaticEventProcessor eventProcessor);

    int listenerCount();
}
