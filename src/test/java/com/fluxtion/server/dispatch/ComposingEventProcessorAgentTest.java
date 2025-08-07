/*
 * SPDX-FileCopyrightText: © 2025 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.fluxtion.server.dispatch;

import com.fluxtion.server.test.TestHeartBeatFeed;
import org.junit.jupiter.api.Test;

public class ComposingEventProcessorAgentTest {

    @Test
    public void test() {
        EventFlowManager eventFlowManager = new EventFlowManager();
        eventFlowManager.registerEventSource("testEventSource", new TestHeartBeatFeed("testEventSource"));

        eventFlowManager.init();

    }
}
