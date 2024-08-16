/*
 * SPDX-FileCopyrightText: © 2024 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 */

package com.fluxtion.server.config;

public interface ConfigListener {

    boolean initialConfig(ConfigMap config);

    default boolean configChanged(ConfigMap config) {
        return false;
    }
}
