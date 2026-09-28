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
package org.monflabs.galtajs.rt.interpreter;

import org.monflabs.galtajs.rt.JSRuntimeContext;

/**
 * The function BODY's own frame - only ever created when the function's
 * parameter list "hasParameterExpressions" (a default value, a destructuring
 * pattern, or a rest parameter: anything beyond a flat list of plain
 * identifiers - see {@code ASTFunction.isSimpleParameterList()}, used as the
 * proxy for this spec concept throughout the interpreter, e.g. by
 * InterpretedFunctionRuntimeContext's own mapped-arguments-object decision).
 *
 * Per FunctionDeclarationInstantiation (ECMA-262 9.2.10) steps 26-27, such a
 * function needs TWO separate lexical environments: an outer one owning the
 * parameter bindings (the existing {@link InterpretedFunctionRuntimeContext},
 * unchanged - this is the "env" of the spec algorithm) and this inner one
 * ("varEnv") owning the body's own var/let/const/function bindings. The
 * separation matters because a closure created while evaluating a parameter
 * default expression only ever sees the outer frame (this inner one doesn't
 * exist yet at that point), so it cannot observe a later var/let/const
 * declared in the body - and conversely a body-declared var of the same name
 * as a free variable captured by such a closure is a genuinely separate
 * binding, not a reassignment of anything the closure captured.
 *
 * Architecturally this is a function-scoped sibling of
 * {@link InterpretedBlockRuntimeContext} - same this/yielder/newTarget/
 * strictMode delegation to the parent frame (inherited), same reliance on
 * ASTIdentifier's name-based slow path for any read/write that isn't
 * statically resolved (see ScopeResolutionOptimizer, which poisons every
 * identifier resolving into a hasParameterExpressions function precisely so
 * no stale scopeHops annotation - computed under the old "one frame per
 * function call" model - is ever published for it). The one behavioral
 * difference from an ordinary block: {@link InterpretedBlockRuntimeContext}
 * delegates getVarDeclContext() further up to the nearest enclosing
 * function/program frame (so a nested `{ var x; }` still hoists x to the
 * function) - but THIS frame IS that hoisting target for the function body,
 * so it must not delegate past itself.
 *
 * Only ever constructed by BuiltinFunctionInterpreter.bindParametersAndVars,
 * and only when hasParameterExpressions is true. The common case (a simple
 * parameter list - the vast majority of calls) never allocates one and keeps
 * running body statements directly against the single
 * InterpretedFunctionRuntimeContext frame, exactly as before this class
 * existed.
 */
public class InterpretedFunctionBodyRuntimeContext extends InterpretedBlockRuntimeContext {

	public InterpretedFunctionBodyRuntimeContext(JSInterpretedRuntimeContext parent) {
		super(parent);
	}

	@Override
	public JSRuntimeContext getVarDeclContext() {
		return this;
	}
}
