/*
 * SPDX-FileCopyrightText: © 2025 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.fluxtion.server.dispatch;

import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
public class HeartbeatEvent {
    private final long timestamp;

    public HeartbeatEvent() {
        timestamp = System.nanoTime();
    }

    public HeartbeatEvent(long timestamp) {
        this.timestamp = timestamp;
    }
}
