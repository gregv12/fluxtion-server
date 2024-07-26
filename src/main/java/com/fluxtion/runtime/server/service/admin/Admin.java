/*
 * SPDX-FileCopyrightText: © 2024 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 */

package com.fluxtion.runtime.server.service.admin;

import com.fluxtion.runtime.annotations.feature.Experimental;

import java.util.List;
import java.util.function.Consumer;

@Experimental
public interface Admin {

    void registerCommand(String name, Consumer<List<String>> command);

    void registerCommand(String name, AdminFunction command);
}
