/*
 * SPDX-FileCopyrightText: © 2024 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 */

package com.fluxtion.server.dispatch;

import com.fluxtion.runtime.StaticEventProcessor;
import com.fluxtion.runtime.annotations.feature.Experimental;

@Experimental
public class EventToOnEventInvokeStrategy extends AbstractEventToInvocationStrategy {
    @Override
    protected void dispatchEvent(Object event, StaticEventProcessor eventProcessor) {
        eventProcessor.onEvent(event);
    }

    @Override
    protected boolean isValidTarget(StaticEventProcessor eventProcessor) {
        return true;
    }
}
