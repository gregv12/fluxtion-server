/*
 * SPDX-FileCopyrightText: © 2025 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.fluxtion.server.test;

import com.fluxtion.runtime.node.ObjectEventHandlerNode;
import com.fluxtion.server.service.CallBackType;
import com.fluxtion.server.service.EventSourceKey;
import com.fluxtion.server.service.EventSubscriptionKey;
import lombok.Getter;

public class SubscriptionEventHandler extends ObjectEventHandlerNode {

    @Getter
    private boolean invoked = false;
    private final String qualifier;

    public SubscriptionEventHandler(String qualifier) {
        this.qualifier = qualifier;
    }

    public SubscriptionEventHandler() {
        this(null);
    }

    @Override
    public void start() {
        super.start();
        invoked = false;

        EventSubscriptionKey<Object> subscriptionKey = new EventSubscriptionKey<>(
                new EventSourceKey<>("testEventFeed"),
                CallBackType.ON_EVENT_CALL_BACK
        );

        getContext().getSubscriptionManager().subscribe(subscriptionKey);
    }

    @Override
    protected boolean handleEvent(Object event) {
        invoked = true;
        return super.handleEvent(event);
    }
}
