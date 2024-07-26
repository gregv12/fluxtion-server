/*
 * SPDX-FileCopyrightText: © 2024 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 */

package com.fluxtion.runtime.server.subscription;

import com.fluxtion.runtime.annotations.feature.Experimental;

@Experimental
public interface EventFlowService {

    void setEventFlowManager(EventFlowManager eventFlowManager, String serviceName);
}
