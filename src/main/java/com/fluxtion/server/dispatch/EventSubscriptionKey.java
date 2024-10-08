/*
 * SPDX-FileCopyrightText: © 2024 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 */

package com.fluxtion.server.dispatch;

import com.fluxtion.runtime.annotations.feature.Experimental;
import lombok.Value;

@Experimental
@Value
public class EventSubscriptionKey<T> {
    com.fluxtion.server.dispatch.EventSourceKey<T> eventSourceKey;
    com.fluxtion.server.dispatch.CallBackType callBackType;
    Object subscriptionQualifier;

    public EventSubscriptionKey(EventSourceKey<T> eventSourceKey,
                                Class<?> callBackClass,
                                Object subscriptionQualifier) {
        this.eventSourceKey = eventSourceKey;
        this.callBackType = CallBackType.forClass(callBackClass);
        this.subscriptionQualifier = subscriptionQualifier;
    }

    public EventSubscriptionKey(EventSourceKey<T> eventSourceKey, CallBackType callBackType, Object subscriptionQualifier) {
        this.eventSourceKey = eventSourceKey;
        this.callBackType = callBackType;
        this.subscriptionQualifier = subscriptionQualifier;
    }
}
