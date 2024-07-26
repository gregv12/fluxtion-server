/*
 * SPDX-FileCopyrightText: © 2024 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 */

package com.fluxtion.runtime.server.subscription;

import com.fluxtion.runtime.annotations.feature.Experimental;
import lombok.Value;

@Experimental
@Value
public class EventSubscriptionKey<T> {
    EventSourceKey<T> eventSourceKey;
    CallBackType callBackType;
    Object subscriptionQualifier;

    public EventSubscriptionKey(EventSourceKey<T> eventSourceKey,
                                Class<?> callBackClass,
                                Object subscriptionQualifier) {
        this.eventSourceKey = eventSourceKey;
        this.callBackType = CallBackType.forClass(callBackClass);
        this.subscriptionQualifier = subscriptionQualifier;
    }
}
