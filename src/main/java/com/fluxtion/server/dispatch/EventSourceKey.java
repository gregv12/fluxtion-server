/*
 * SPDX-FileCopyrightText: © 2024 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 */

package com.fluxtion.server.dispatch;

import lombok.Value;

@Value
public class EventSourceKey<T> {
    String sourceName;

    /**
     * Fluent: create a key from a source name.
     */
    public static <T> EventSourceKey<T> of(String sourceName) {
        return new EventSourceKey<>(sourceName);
    }
}
