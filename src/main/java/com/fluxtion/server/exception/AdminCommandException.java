/*
 * SPDX-FileCopyrightText: © 2025 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.fluxtion.server.exception;

/**
 * Wraps exceptions related to admin command publication/processing.
 */
public class AdminCommandException extends FluxtionServerException {
    public AdminCommandException(String message) {
        super(message);
    }

    public AdminCommandException(String message, Throwable cause) {
        super(message, cause);
    }

    public AdminCommandException(Throwable cause) {
        super(cause);
    }
}
