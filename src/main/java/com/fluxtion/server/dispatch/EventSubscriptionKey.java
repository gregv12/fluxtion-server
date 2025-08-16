/*
 * SPDX-FileCopyrightText: © 2024 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 */

package com.fluxtion.server.dispatch;

import lombok.Value;

@Value
public class EventSubscriptionKey<T> {
    com.fluxtion.server.dispatch.EventSourceKey<T> eventSourceKey;
    com.fluxtion.server.dispatch.CallBackType callBackType;

    public EventSubscriptionKey(EventSourceKey<T> eventSourceKey,
                                Class<?> callBackClass) {
        this.eventSourceKey = eventSourceKey;
        this.callBackType = CallBackType.forClass(callBackClass);
    }

    public EventSubscriptionKey(EventSourceKey<T> eventSourceKey, CallBackType callBackType) {
        this.eventSourceKey = eventSourceKey;
        this.callBackType = callBackType;
    }

    /**
     * Create a subscription key for a specific event source and call back type.
     * @param eventSourceKey
     * @param callBackType
     * @param qualifier - IGNORED for non breaking backward compatibility
     */
    public EventSubscriptionKey(EventSourceKey<T> eventSourceKey, CallBackType callBackType, Object qualifier) {
        this.eventSourceKey = eventSourceKey;
        this.callBackType = callBackType;
    }
}
