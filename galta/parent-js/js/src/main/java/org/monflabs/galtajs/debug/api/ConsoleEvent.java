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

import java.util.List;

/**
 * A script called a {@code console} function.
 *
 * @param type the console method name: {@code log}, {@code warn}, {@code error}, ...
 * @param arguments the call's arguments, GaltaJS values
 * @param location where the call happened, or null
 * @param context the context the call ran in
 */
public record ConsoleEvent(String type, List<Object> arguments, Location location, ExecutionContext context) {
}
