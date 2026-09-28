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

/**
 * One line of the debugger console.
 * @param kind what produced it
 * @param text the text
 */
public record ConsoleEntry(Kind kind, String text) {
    /** Where a console line came from. */
    public enum Kind {
        /** A {@code console.log} and its kin, or other standard output. */
        LOG,
        /** An error - {@code console.error}, or an uncaught exception. */
        ERROR,
        /** An expression the user typed into the console prompt. */
        EVAL_INPUT,
        /** The result of an evaluated expression. */
        EVAL_RESULT,
        /** A status note from the debugger itself. */
        STATUS
    }
}
