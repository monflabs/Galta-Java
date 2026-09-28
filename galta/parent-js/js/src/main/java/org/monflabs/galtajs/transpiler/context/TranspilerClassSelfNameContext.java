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
 * Codegen-time view inserted into the parent chain used while transpiling a
 * NAMED class's own body (constructor/methods/getters/setters/field
 * initializers/static blocks - never the ClassHeritage expression, which
 * keeps resolving through the un-wrapped enclosing context, see
 * ASTClassDecl.transpileJavaExpression) - redirects any reference to the
 * class's OWN name to a genuinely separate binding (see ASTClassDecl.init(),
 * `selfBindingVarDef`) instead of whatever OUTER binding of the same name
 * happens to already exist (e.g. a class DECLARATION's own statement-only
 * VAR_TYPE.LET binding, set up in the SAME enclosing container - see
 * KnownGaps.md "a class's own name isn't bound in its own separate,
 * immutable inner scope").
 *
 * `selfBindingVarDef` is a VariableDef registered under a synthetic,
 * never-user-reachable name (so it never collides with anything, and is
 * never found by ordinary name-keyed lookups against the SAME container) -
 * but into the SAME enclosing container/array as every other local in that
 * scope, so it's a genuinely separate Java array SLOT that gets captured by
 * every class-element body exactly the same (ordinary Java local-class
 * closure capture) way the outer array already is - no new runtime-context-
 * chain object needed (contrast with the private-name-only classScope_N/
 * TranspiledClassPrivateScopeRuntimeContext mechanism, which genuinely does
 * need one, since a private name has no compile-time slot at all).
 *
 * getOwnVariable is the ONLY thing overridden: `getContextJavaName()` /
 * `getDisposablesListVar()` and everything else must still behave exactly
 * as if code were generated directly against the parent (this class
 * introduces no new Java method scope) - the inherited defaults, which
 * delegate to `getParent()`, already do the right thing.
 */
public final class TranspilerClassSelfNameContext extends JSTranspilerGeneratorContext {

	private final String className;
	private final VariableDef selfVarDef;

	public TranspilerClassSelfNameContext(JSTranspilerGeneratorContext parent, String className, VariableDef selfVarDef) {
		super(parent);
		this.className = className;
		this.selfVarDef = selfVarDef;
	}

	@Override
	public VariableDef getOwnVariable(String name) {
		return name.equals(className) ? selfVarDef : null;
	}
}
