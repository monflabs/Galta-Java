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
package org.monflabs.galtajs.transpiler.context;

import org.monflabs.galtajs.node.ASTVarContainer.VariableDef;

/**
 * Codegen-time view inserted directly in front of a function's own
 * {@code functionContext} - used while transpiling either that function's own
 * BODY statements ({@code ASTFunction.hasNonStrictDirectEvalInOwnBody()}) or
 * its own parameter-list default-value/destructuring expressions
 * ({@code hasNonStrictDirectEvalInOwnParams()}) - for exactly the (rare) case
 * where one of those found a literal, syntactically-visible, non-strict
 * direct {@code eval(...)} call in that same site (not inside a nested
 * function/class).
 *
 * A non-strict direct eval can declare a new {@code var} that lands directly
 * in the CALLING function's own VariableEnvironment (EvalDeclarationInstantiation,
 * ECMA-262 19.2.1.3 step 5) - genuinely shadowing a same-named OUTER binding
 * for the rest of that invocation, even though the calling function's own
 * source never declares that name itself (see
 * StandardLibrary.BaseEvalContext.createVariable's own doc and
 * VarAccessor.isOwnScope()'s doc for the companion fix on the eval
 * declaration's own write side). GaltaJS's transpiler resolves every free
 * identifier READ to a fixed compile-time slot - so a read of such a name,
 * reached by walking OUT of the eval-containing function into whatever
 * enclosing scope happens to already declare it, would statically hard-wire
 * onto that OUTER slot forever, never seeing the eval-created shadow. See
 * KnownGaps.md's "a read AFTER a same-function direct-eval doesn't see the
 * eval's shadowing var" entry for the full writeup (including the V8/Node
 * repro showing these particular ES5-era test262 assertions aren't satisfied
 * by modern engines either).
 *
 * This class changes NOTHING about how a name actually DECLARED in this
 * function resolves - {@link #getOwnVariable(String)} is a pure pass-through
 * to the real {@code functionContext}. Its only effect is
 * {@link #isEvalShadowBoundary()} returning true, a marker
 * {@code ASTIdentifier.getIdentifierReadAccessor()}'s context-chain walk
 * checks on every context visited (the same way it already checks
 * {@code getWithJavaName()} for a `with`-fallback) - once a read's static
 * resolution is found to have walked THROUGH this boundary (i.e. the name
 * isn't declared in this function itself) on its way to some outer slot, the
 * final accessor is wrapped in a runtime check
 * ({@code JSTranspiledUnit.evalShadowRead}) that first asks this function's
 * OWN runtime context (via the existing, already-used-elsewhere
 * {@code getLocalVariableEntry(...)}) whether an eval actually created such a
 * shadow at runtime, falling through to the ORIGINAL static value when it
 * didn't (the overwhelming common case, and the ONLY case whenever this
 * function has no literal eval at all - functions without one never get this
 * context allocated in the first place, so their codegen is byte-for-byte
 * unchanged from before this class existed).
 */
public final class TranspilerEvalShadowContext extends JSTranspilerGeneratorContext {

	private final JSTranspilerGeneratorContext functionContext;

	public TranspilerEvalShadowContext(JSTranspilerGeneratorContext functionContext) {
		super(functionContext);
		this.functionContext = functionContext;
	}

	@Override
	public VariableDef getOwnVariable(String name) {
		return functionContext.getOwnVariable(name);
	}

	@Override
	public boolean isEvalShadowBoundary() {
		return true;
	}
}
