/*
 * SPDX-FileCopyrightText: © 2025 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.fluxtion.server.service.scheduler;

import com.fluxtion.runtime.annotations.feature.Experimental;
import com.fluxtion.runtime.annotations.runtime.ServiceRegistered;
import com.fluxtion.runtime.callback.AbstractCallbackNode;

@Experimental
public class ScheduledTriggerNode extends AbstractCallbackNode<Object> {

    private SchedulerService schedulerService;

    public ScheduledTriggerNode() {
        super();
    }

    public ScheduledTriggerNode(int filterId) {
        super(filterId);
    }

    @ServiceRegistered
    public void scheduler(SchedulerService scheduler) {
        this.schedulerService = scheduler;
    }

    public void triggerAfterDelay(long millis) {
        if (schedulerService != null) {
            schedulerService.scheduleAfterDelay(millis, this::fireNewEventCycle);
        }
    }
}
