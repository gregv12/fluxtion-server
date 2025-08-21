/*
 * SPDX-FileCopyrightText: © 2025 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.fluxtion.server.internal;

import com.fluxtion.agrona.concurrent.AgentRunner;
import com.fluxtion.server.dutycycle.ComposingServiceAgent;

/**
 * Lightweight holder pairing a {@link com.fluxtion.server.dutycycle.ComposingServiceAgent}
 * with its executing {@link com.fluxtion.agrona.concurrent.AgentRunner}.
 * <p>
 * Used by FluxtionServer to track worker service agent groups and their runners
 * for lifecycle management (start/stop).
 *
 * @param group       the composing worker service agent group
 * @param groupRunner the agent runner executing the group
 */
public record ComposingWorkerServiceAgentRunner(ComposingServiceAgent group, AgentRunner groupRunner) {
}
