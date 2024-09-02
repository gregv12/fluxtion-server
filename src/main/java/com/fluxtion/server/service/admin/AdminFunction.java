/*
 * SPDX-FileCopyrightText: © 2024 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 */

package com.fluxtion.server.service.admin;

import com.fluxtion.runtime.annotations.feature.Experimental;

import java.util.List;
import java.util.function.Consumer;

@Experimental
public interface AdminFunction<OUT, ERR> {

    void processAdminCommand(List<String> commands, Consumer<OUT> output, Consumer<ERR> errOutput);
}
