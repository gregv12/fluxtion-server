/*
 * SPDX-FileCopyrightText: © 2024 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 */

package com.fluxtion.runtime.server.service.scheduler;

import com.fluxtion.runtime.annotations.feature.Experimental;

@Experimental
public interface SchedulerService {

    long scheduleAtTime(long expireTIme, Runnable expiryAction);

    long scheduleAfterDelay(long waitTime, Runnable expiryAction);

    long milliTime();

    long microTime();

    long nanoTime();
}
