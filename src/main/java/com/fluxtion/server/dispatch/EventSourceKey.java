/*
 * SPDX-FileCopyrightText: © 2024 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 */

package com.fluxtion.server.dispatch;

import com.fluxtion.runtime.annotations.feature.Experimental;
import lombok.Value;

@Experimental
@Value
public class EventSourceKey<T> {
    String sourceName;
}
