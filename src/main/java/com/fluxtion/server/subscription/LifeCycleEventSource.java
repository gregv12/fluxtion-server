/*
 * SPDX-FileCopyrightText: © 2024 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 */

package com.fluxtion.server.subscription;

import com.fluxtion.runtime.annotations.feature.Experimental;
import com.fluxtion.runtime.lifecycle.Lifecycle;

@Experimental
public interface LifeCycleEventSource<T> extends EventSource<T>, Lifecycle {

    @Override
    default void subscribe(EventSubscriptionKey<T> eventSourceKey) {

    }

    @Override
    default void unSubscribe(EventSubscriptionKey<T> eventSourceKey) {

    }

    @Override
    default void setEventToQueuePublisher(EventToQueuePublisher<T> targetQueue) {

    }
}
