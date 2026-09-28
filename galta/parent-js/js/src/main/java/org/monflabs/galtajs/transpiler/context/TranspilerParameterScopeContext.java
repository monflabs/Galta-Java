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
 * Codegen-time view of a function's own {@code functionContext} used ONLY
 * while transpiling expressions that live IN the parameter list itself
 * (default values, destructuring targets/defaults, computed property keys)
 * - never for the function body.
 *
 * Per spec (9.2.10 FunctionDeclarationInstantiation steps 26-27), a function
 * whose parameter list "hasParameterExpressions" (anything beyond a flat
 * list of plain identifiers - see {@code ASTFunction.isSimpleParameterList()})
 * gets a separate parameter Environment Record ("env") and body/var
 * Environment Record ("varEnv", a CHILD of env). A closure created while
 * evaluating a parameter expression captures "env" as its
 * [[Environment]] - so it can never see varEnv's own bindings (the body's
 * own var/let/const/function declarations), even though varEnv is
 * conceptually "inside" the same function. If such a closure references a
 * free identifier that happens to share a name with one of the body's own
 * declarations, it must resolve through whatever encloses the FUNCTION
 * instead (exactly as if the body's declaration didn't exist at all, from
 * that closure's point of view).
 *
 * GaltaJS's transpiler instead resolves every declared name (parameters
 * AND body vars/lets/consts/functions alike) into ONE flat per-function
 * array (`ASTVarContainer.VariableDefContainer`/`functionContext`'s own
 * `variables` map, populated wholesale by
 * `ASTVarContainer.transpilerDeclareStatement`) - so a parameter-list
 * closure referencing a same-named body declaration wrongly resolves to
 * the function's own (not-yet-existing, from that closure's perspective)
 * local slot instead of continuing outward. Confirmed via test262
 * `language/expressions/function/scope-paramsbody-var-open.js` and
 * siblings (see KnownGaps.md's "parameter-vs-body variable environment"
 * entry).
 *
 * This class provides a NARROW, filtered view for exactly that one
 * resolution path: `getOwnVariable(name)` only exposes names declared
 * BEFORE any body statement was processed (`ASTFunction.
 * preambleVariableCount`'s own slots - `arguments`, the self-reference
 * binding, and the actual parameter-bound names, all with a lower array
 * index than every body-declared name, by construction - see
 * preambleVariableCount's own field doc). A body-declared name is treated
 * as invisible here, so `ASTIdentifier`'s own resolution walk (which calls
 * `ctx.getOwnVariable(id)`, then `ctx=ctx.getParent()` on a miss) continues
 * straight past this function entirely once told to (this context's own
 * `getParent()` is `functionContext.getParent()` - the SAME chain
 * `functionContext` itself would use next, once this filtered layer no
 * longer intercepts anything for that name).
 *
 * Every OTHER concern - the Java "_ctx" variable name in scope (which a
 * class body with private members pins away from the ambient default, see
 * `TranspilerGeneratorFunctionContext`'s own doc), the constant pool, the
 * disposables-list boundary, etc. - must still behave EXACTLY as if code
 * were being generated directly against `functionContext` (we're still
 * emitting into the very same Java method), so those either delegate
 * straight to `functionContext` (when `functionContext` itself pins a
 * value independent of its own parent - only `getContextJavaName()` and
 * `getDisposablesListVar()` currently do this) or fall through this
 * context's own default (parent-walking) implementation, which - since
 * `getParent()` here is `functionContext.getParent()` - reaches the exact
 * same destination `functionContext`'s own unoverridden defaults would
 * (e.g. the constant pool / identifier pool / transpiler map, none of
 * which `functionContext` stores itself - it delegates those to its own
 * parent too).
 *
 * Only ever constructed by `ASTFunction.transpileParameterBindingPrologue`,
 * and only when the function `hasParameterExpressions()` (`!isSimpleParameterList()`)
 * AND has at least one body-declared name (`getVariables().size() >
 * preambleVariableCount`) - the common case (simple parameter list, or a
 * parameter list with no body-level declarations to hide) never allocates
 * one and keeps resolving parameter-list expressions directly against
 * `functionContext`, exactly as before this class existed.
 */
public final class TranspilerParameterScopeContext extends JSTranspilerGeneratorContext {

	private final JSTranspilerGeneratorContext functionContext;
	private final int preambleVariableCount;

	public TranspilerParameterScopeContext(JSTranspilerGeneratorContext functionContext, int preambleVariableCount) {
		super(functionContext.getParent());
		this.functionContext = functionContext;
		this.preambleVariableCount = preambleVariableCount;
	}

	@Override
	public VariableDef getOwnVariable(String name) {
		VariableDef v = functionContext.getOwnVariable(name);
		return v!=null && v.getJavaVariableIndex()<preambleVariableCount ? v : null;
	}

	@Override
	public String getContextJavaName() {
		return functionContext.getContextJavaName();
	}

	@Override
	public String getDisposablesListVar() {
		return functionContext.getDisposablesListVar();
	}
}
