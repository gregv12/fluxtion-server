/*
 * SPDX-FileCopyrightText: © 2024 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 */

package com.fluxtion.server.service.admin;

import com.fluxtion.runtime.annotations.feature.Experimental;

import java.util.List;

@Experimental
public interface AdminCommandRegistry {

    <OUT, ERR> void registerCommand(String name, AdminFunction<OUT, ERR> command);

    void processAdminCommandRequest(AdminCommandRequest command);

    List<String> commandList();
}
