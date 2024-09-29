/*
 * SPDX-FileCopyrightText: © 2024 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 */

package com.fluxtion.server.dispatch;

import com.fluxtion.runtime.StaticEventProcessor;
import com.fluxtion.runtime.annotations.feature.Experimental;

/**
 * Reads and transforms event flow into application level callbacks on registered {@link StaticEventProcessor}'s
 */
@Experimental
public interface EventToInvokeStrategy {

    void processEvent(Object event);

    void processEvent(Object event, long time);

    void registerProcessor(StaticEventProcessor eventProcessor);

    void deregisterProcessor(StaticEventProcessor eventProcessor);

    int listenerCount();
}
