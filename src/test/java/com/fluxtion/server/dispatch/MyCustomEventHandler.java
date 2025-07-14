/*
 * SPDX-FileCopyrightText: © 2025 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.fluxtion.server.dispatch;

import com.fluxtion.runtime.node.ObjectEventHandlerNode;

public class MyCustomEventHandler extends ObjectEventHandlerNode {
    @Override
    protected boolean handleEvent(Object event) {
        System.out.println("MyProcessor received event " + event);
        return super.handleEvent(event);
    }
}
