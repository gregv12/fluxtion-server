/*
 * SPDX-FileCopyrightText: © 2024 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 */

package com.fluxtion.server.dutycycle;

import com.fluxtion.agrona.concurrent.Agent;
import com.fluxtion.runtime.annotations.feature.Experimental;
import com.fluxtion.runtime.service.Service;
import lombok.Value;

@Experimental
@Value
public class ServiceAgent<T> {

    //unique identifier
    String agentGroup;
    //proxy - exported service
    Service<T> exportedService;
    //adds to EP agent
    Agent delegate;
}
