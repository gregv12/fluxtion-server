/*
 * SPDX-FileCopyrightText: © 2024 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 */

package com.fluxtion.server.service.servercontrol;

import com.fluxtion.agrona.concurrent.IdleStrategy;
import com.fluxtion.runtime.StaticEventProcessor;

import java.util.function.Supplier;

public interface FluxtionServerController {

    String SERVICE_NAME = "com.fluxtion.server.service.servercontrol.FluxtionServerController";

    void addEventProcessor(
            String groupName,
            IdleStrategy idleStrategy,
            Supplier<StaticEventProcessor> feedConsumer);
}
