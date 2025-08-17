/*
 * SPDX-FileCopyrightText: © 2025 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.fluxtion.server.service;

import com.fluxtion.runtime.annotations.feature.Experimental;

/**
 * Represents a callback type used within the Fluxtion framework for defining and managing callback functionality.
 * This interface provides a mechanism to define and categorize different types of callbacks.
 * <p>
 * Key features of this interface:
 * 1. Provides a static method to create a callback type for a given class.
 * 2. Includes an inner class and record implementations for specific callback type scenarios.
 * <p>
 * Callback Types:
 * - OnEventCallBack: A predefined implementation representing an event callback.
 * - CallBackTypeByClass: A callback type determined based on a class reference.
 * <p>
 * Usage:
 * The `CallBackType` interface acts as a marker or abstraction for specific callback type instances.
 * It enables flexibility in defining custom callback types while leveraging the prebuilt ones.
 * <p>
 * Note:
 * This interface is marked as experimental and could be subject to future changes.
 */
@Experimental
public interface CallBackType {

    OnEventCallBack ON_EVENT_CALL_BACK = new OnEventCallBack();

    String name();

    static CallBackType forClass(Class<?> clazz) {
        return new CallBackTypeByClass(clazz);
    }

    record CallBackTypeByClass(Class<?> callBackClass) implements CallBackType {

        @Override
        public String name() {
            return callBackClass.getCanonicalName();
        }
    }

    class OnEventCallBack implements CallBackType {

        private OnEventCallBack() {
        }

        @Override
        public String name() {
            return "onEventCallBack";
        }
    }

}
