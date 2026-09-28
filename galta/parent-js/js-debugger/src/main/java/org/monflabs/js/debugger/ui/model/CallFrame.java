/*
 * Copyright (c) 2019-2026 Philippe Riand
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.monflabs.js.debugger.ui.model;

import java.util.List;

/**
 * One frame of a paused call stack.
 * @param callFrameId the server's id, valid only for this pause
 * @param functionName the function's name, or empty for an anonymous one
 * @param url the script's url
 * @param scriptId the script's id
 * @param line the current line, zero based
 * @param column the current column, zero based
 * @param scopeChain the scopes visible here, innermost first
 * @param thisObject the value of {@code this}
 */
public record CallFrame(String callFrameId, String functionName, String url, String scriptId,
                        int line, int column, List<Scope> scopeChain, RemoteValue thisObject) {
    /** A label for the call-stack list. */
    public String label() {
        final String name = functionName == null || functionName.isEmpty() ? "(anonymous)" : functionName;
        final int slash = url == null ? -1 : url.lastIndexOf('/');
        final String file = url == null ? "?" : (slash < 0 ? url : url.substring(slash + 1));
        return name + "  " + file + ":" + (line + 1);
    }
}
