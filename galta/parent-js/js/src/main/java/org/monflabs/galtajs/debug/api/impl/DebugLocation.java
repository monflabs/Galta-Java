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
package org.monflabs.galtajs.debug.api.impl;

/**
 * A source position plus the flags {@link DebugHook} needs to decide
 * whether to pause - mode-neutral so the same {@link DebugHook} contract
 * serves both interpreted (backed by an {@code ASTDebugHook} node) and
 * transpiled (backed only by literal line/col arguments baked into generated
 * Java - see {@code JSTranspiledUnit.debugStatement()}) call sites.
 *
 * @param line 1-based, matching {@link org.monflabs.galtajs.node.ASTNode#getBeginLine()}'s convention
 * @param col 1-based
 * @param statementLevel true only for a genuine statement boundary - see the
 *        interpreted-mode {@code ASTDebugHook.isStatementLevel()}'s own
 *        doc for why this matters (a line-based breakpoint must not fire
 *        twice for a statement and a sub-expression on the same line).
 *        Transpiled call sites are always statement-level (v1 does not
 *        instrument sub-expressions in transpiled code at all).
 * @param debuggerStatement true for the literal {@code debugger;} statement
 */
public record DebugLocation(int line, int col, boolean statementLevel, boolean debuggerStatement) {
}
