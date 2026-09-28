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
 * A source position within a script.
 *
 * <p>{@code line}/{@code column} follow GaltaJS's own, native 1-based
 * convention (see {@link org.monflabs.galtajs.node.ASTNode#getBeginLine()}/
 * {@link org.monflabs.galtajs.node.ASTNode#getBeginCol()}) - not CDP's
 * 0-based one. Translation to/from CDP's convention happens at the CDP
 * bridge boundary, not here, so the facade stays engine-native.
 *
 * @param script the script this location is in
 * @param line the line, 1-based
 * @param column the column, 1-based
 */
public record Location(DebugScript script, int line, int column) {
}
