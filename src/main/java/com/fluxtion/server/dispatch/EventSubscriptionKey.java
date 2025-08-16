/*
 * SPDX-FileCopyrightText: © 2024 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 */

package com.fluxtion.server.dispatch;

import lombok.Value;

/**
 * Immutable key describing a subscription: which EventSource to subscribe to and
 * which callback type to deliver on.
 *
 * Adds a fluent API for easier construction while keeping existing constructors
 * for backward compatibility.
 */
@Value
public class EventSubscriptionKey<T> {
    com.fluxtion.server.dispatch.EventSourceKey<T> eventSourceKey;
    com.fluxtion.server.dispatch.CallBackType callBackType;

    // Existing constructors (backward compatible)
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
     * qualifier is ignored (kept for non-breaking backward compatibility).
     */
    public EventSubscriptionKey(EventSourceKey<T> eventSourceKey, CallBackType callBackType, Object qualifier) {
        this.eventSourceKey = eventSourceKey;
        this.callBackType = callBackType;
    }

    // -------- Fluent API --------

    /**
     * Fluent: Create an onEvent subscription to the named source.
     */
    public static <T> EventSubscriptionKey<T> onEvent(String sourceName) {
        return new EventSubscriptionKey<>(EventSourceKey.<T>of(sourceName), CallBackType.ON_EVENT_CALL_BACK);
    }

    /**
     * Fluent: Create a subscription to the named source with a specific callback type.
     */
    public static <T> EventSubscriptionKey<T> of(String sourceName, CallBackType callBackType) {
        return new EventSubscriptionKey<>(EventSourceKey.<T>of(sourceName), callBackType);
    }

    /**
     * Fluent: Create a subscription for an existing source key and callback type.
     */
    public static <T> EventSubscriptionKey<T> of(EventSourceKey<T> eventSourceKey, CallBackType callBackType) {
        return new EventSubscriptionKey<>(eventSourceKey, callBackType);
    }

    /**
     * Start a fluent builder for a subscription to the named source.
     */
    public static <T> Builder<T> fromSource(String sourceName) {
        return new Builder<>(EventSourceKey.<T>of(sourceName));
    }

    /**
     * Start a fluent builder for a subscription from an existing EventSourceKey.
     */
    public static <T> Builder<T> builder(EventSourceKey<T> sourceKey) {
        return new Builder<>(sourceKey);
    }

    /**
     * Fluent builder for EventSubscriptionKey.
     */
    public static final class Builder<T> {
        private final EventSourceKey<T> eventSourceKey;
        private CallBackType callBackType = CallBackType.ON_EVENT_CALL_BACK; // sensible default

        private Builder(EventSourceKey<T> eventSourceKey) {
            this.eventSourceKey = eventSourceKey;
        }

        /**
         * Set callback type explicitly.
         */
        public Builder<T> callback(CallBackType type) {
            this.callBackType = type;
            return this;
        }

        /**
         * Set callback type from a callback class.
         */
        public Builder<T> callback(Class<?> callbackClass) {
            this.callBackType = CallBackType.forClass(callbackClass);
            return this;
        }

        /**
         * Convenience to declare the standard onEvent callback.
         */
        public Builder<T> onEvent() {
            this.callBackType = CallBackType.ON_EVENT_CALL_BACK;
            return this;
        }

        /**
         * Build the immutable EventSubscriptionKey.
         */
        public EventSubscriptionKey<T> build() {
            return new EventSubscriptionKey<>(eventSourceKey, callBackType);
        }
    }
}
