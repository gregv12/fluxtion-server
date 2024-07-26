/*
 * SPDX-FileCopyrightText: © 2024 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 */

package com.fluxtion.runtime.server.service.admin;

import com.fluxtion.runtime.annotations.feature.Experimental;

import java.io.PrintStream;
import java.util.List;

@Experimental
public interface AdminFunction {

    void processAdminCommand(List<String> commands, PrintStream output, PrintStream errOutput);
}
