/*
 * SPDX-FileCopyrightText: © 2025 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.fluxtion.server.dutycycle;

import com.fluxtion.agrona.concurrent.Agent;
import com.fluxtion.agrona.concurrent.IdleStrategy;
import com.fluxtion.runtime.service.Service;

/**
 * @param agentGroup      unique identifier
 * @param idleStrategy    thread management
 * @param exportedService proxy - exported service
 * @param delegate        adds to EP agent
 */
public record ServiceAgent<T>(String agentGroup, IdleStrategy idleStrategy, Service<T> exportedService,
                              Agent delegate) {

}
