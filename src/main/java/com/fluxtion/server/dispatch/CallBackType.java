/*
 * SPDX-FileCopyrightText: © 2024 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 */

package com.fluxtion.server.dispatch;

import com.fluxtion.runtime.annotations.feature.Experimental;
import lombok.Value;

@Experimental
public interface CallBackType {

    OnEventCallBack ON_EVENT_CALL_BACK = new OnEventCallBack();

    String name();

    static CallBackType forClass(Class<?> clazz) {
        return new CallBackTypeByClass(clazz);
    }

    @Value
    class CallBackTypeByClass implements CallBackType {

        Class<?> callBackClass;

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
