/*
 * SPDX-FileCopyrightText: © 2025 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.fluxtion.server.dispatch;

import com.fluxtion.runtime.annotations.OnEventHandler;
import com.fluxtion.runtime.audit.EventLogNode;

public class HeartBeatNode extends EventLogNode {

    @OnEventHandler
    public boolean heartBeat(HeartbeatEvent time) {
        long deltaMicros = (System.nanoTime() - time.getTimestamp()) / 1_000_000;
        auditLog.info("eventCbDeltaMicros", deltaMicros)
                .debug("debugMessage", deltaMicros);
        System.out.println("received heartbeat deltaMicros " + deltaMicros);
        return true;
    }

}
