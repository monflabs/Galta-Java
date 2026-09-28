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
 * One scope in a frame's scope chain.
 * @param type the kind: {@code "local"}, {@code "closure"}, {@code "global"}, ...
 * @param name a name for a named scope (a closure's function), or null
 * @param object the scope object, whose properties are the variables
 */
public record Scope(String type, String name, RemoteValue object) {
    /** A label for the sidebar: the name when there is one, else the capitalised type. */
    public String label() {
        if (name != null && !name.isEmpty()) {
            return capitalize(type) + ": " + name;
        }
        return capitalize(type);
    }

    private static String capitalize(final String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
