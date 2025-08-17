/*
 * SPDX-FileCopyrightText: © 2025 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.fluxtion.server.internal;

import com.fluxtion.agrona.concurrent.AgentRunner;
import com.fluxtion.server.dutycycle.ComposingServiceAgent;

public record ComposingWorkerServiceAgentRunner(ComposingServiceAgent group, AgentRunner groupRunner) {
}
