/*
 * SPDX-FileCopyrightText: © 2025 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.fluxtion.server.service;

import com.fluxtion.server.dispatch.EventFlowManager;

/**
 * Represents a service interface for managing event flows within a system.
 * Classes implementing this interface are responsible for registering and
 * managing event flow configurations using an {@link EventFlowManager}.
 * This interface allows for dynamic assignment of an event flow manager
 * with an associated service name.
 * <p>
 * The {@code @Experimental} annotation indicates that this interface
 * is subject to changes as it is a feature under active development
 * and its API is not considered stable.
 */
public interface EventFlowService {

    void setEventFlowManager(EventFlowManager eventFlowManager, String serviceName);
}
