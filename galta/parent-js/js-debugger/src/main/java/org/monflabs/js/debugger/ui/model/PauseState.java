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
 * The engine is paused: why, where, and on what.
 * @param reason the CDP reason: {@code "other"}, {@code "step"}, {@code "exception"}, {@code "debugCommand"}
 * @param frames the call stack, innermost first
 * @param hitBreakpointIds the server ids of the breakpoints hit, if any
 * @param exception the thrown value, when the reason is an exception, else null
 */
public record PauseState(String reason, List<CallFrame> frames, List<String> hitBreakpointIds, RemoteValue exception) {
}
