/*
 * SPDX-FileCopyrightText: © 2025 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.fluxtion.server;

import com.fluxtion.agrona.concurrent.AgentRunner;
import com.fluxtion.server.dutycycle.ComposingEventProcessorAgent;
import lombok.Value;

@Value
class ComposingEventProcessorAgentRunner {
    ComposingEventProcessorAgent group;
    AgentRunner groupRunner;
}
