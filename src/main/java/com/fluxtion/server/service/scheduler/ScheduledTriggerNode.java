/*
 * SPDX-FileCopyrightText: © 2024 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 */

package com.fluxtion.server.service.scheduler;

import com.fluxtion.runtime.annotations.feature.Experimental;
import com.fluxtion.runtime.annotations.runtime.ServiceRegistered;
import com.fluxtion.runtime.callback.CallBackNode;

@Experimental
public class ScheduledTriggerNode extends CallBackNode {

    private SchedulerService schedulerService;

    @ServiceRegistered
    public void scheduler(SchedulerService schedulerService) {
        this.schedulerService = schedulerService;
    }

    public void triggerAfterDelay(long millis) {
        if (schedulerService != null) {
            schedulerService.scheduleAfterDelay(millis, this::triggerGraphCycle);
        }
    }
}
