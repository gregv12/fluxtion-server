/*
 * SPDX-FileCopyrightText: © 2024 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.fluxtion.server.service.scheduler;

import com.fluxtion.runtime.annotations.builder.AssignToField;
import com.fluxtion.runtime.annotations.feature.Experimental;
import com.fluxtion.runtime.annotations.runtime.ServiceRegistered;
import com.fluxtion.runtime.callback.CallBackNode;
import com.fluxtion.runtime.callback.InstanceCallbackEvent;

@Experimental
public class ScheduledTriggerNode extends CallBackNode {

    private SchedulerService schedulerService;

    public ScheduledTriggerNode(@AssignToField("event") InstanceCallbackEvent event) {
        super(event);
    }

    public ScheduledTriggerNode() {
    }

    @ServiceRegistered
    public void scheduler(SchedulerService schedulerService) {
        this.schedulerService = schedulerService;
    }

    public void triggerAfterDelay(long millis) {
        if (schedulerService != null) {
            schedulerService.scheduleAfterDelay(millis, this::fireCallback);
        }
    }
}
