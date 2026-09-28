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
package org.monflabs.galtajs.debug.api;

/**
 * One property of an object, as seen by a debugger.
 *
 * @param name the property's name
 * @param key the property key: a String or a symbol GaltaJS value
 * @param value the value, a GaltaJS value, or null when only a getter/setter is present
 * @param getter the getter function, a GaltaJS value, or null
 * @param setter the setter function, a GaltaJS value, or null
 * @param writable whether a plain assignment may change the value
 * @param enumerable whether the property shows up in a for-in/Object.keys walk
 * @param configurable whether the property may be deleted/redefined
 * @param isOwn whether the property is the object's own (vs. inherited)
 * @param wasThrown whether reading the getter threw (value then carries the thrown value)
 */
public record DebugProperty(String name, Object key, Object value, Object getter, Object setter,
		boolean writable, boolean enumerable, boolean configurable, boolean isOwn, boolean wasThrown) {
}
