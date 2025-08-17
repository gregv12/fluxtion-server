/*
 * SPDX-FileCopyrightText: © 2025 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.fluxtion.server.service.extension;

import com.fluxtion.agrona.concurrent.Agent;
import com.fluxtion.server.dispatch.CallBackType;
import com.fluxtion.server.dispatch.EventToInvokeStrategy;
import lombok.extern.java.Log;

import java.util.function.Supplier;

/**
 * Convenience base class for event-source services that also run as an Agrona {@link com.fluxtion.agrona.concurrent.Agent}.
 * <p>
 * Use this when your source needs its own agent thread and lifecycle callbacks. It inherits
 * the event-flow integration from {@link AbstractEventSourceService} and exposes the service
 * name via {@link #roleName()} for agent diagnostics.
 *
 * @param <T> event type emitted by this source
 */
@Log
public abstract class AbstractAgentHostedEventSourceService<T> extends AbstractEventSourceService<T>
        implements
        Agent {

    protected AbstractAgentHostedEventSourceService(String name) {
        this(name, CallBackType.ON_EVENT_CALL_BACK);
    }

    public AbstractAgentHostedEventSourceService(String name, CallBackType eventToInvokeType) {
        this(name, eventToInvokeType, null);
    }

    public AbstractAgentHostedEventSourceService(
            String name,
            CallBackType eventToInvokeType,
            Supplier<EventToInvokeStrategy> eventToInokeStrategySupplier) {
        super(name, eventToInvokeType, eventToInokeStrategySupplier);
    }

    @Override
    public String roleName() {
        return name;
    }
}
