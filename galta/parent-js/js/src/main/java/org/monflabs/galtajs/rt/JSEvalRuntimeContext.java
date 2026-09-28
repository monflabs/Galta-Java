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
package org.monflabs.galtajs.rt;

/**
 * Root context when evaluating an expression with eval().
 */
public interface JSEvalRuntimeContext extends JSRuntimeContext {

	/**
	 * Called once the eval'd source has been parsed, so this context's strictness can
	 * also reflect an own "use strict" directive at the top of the eval'd code itself
	 * (in addition to whatever it inherits from the calling/surrounding context).
	 */
	public void setForceStrictMode(boolean forceStrictMode);

	/**
	 * A direct eval's own var/function declarations only actually reach the
	 * real global object when there is no pre-existing local (or inherited
	 * parent-accessor) binding of the same name AND the eval is non-strict -
	 * see this context's own createVariable() runtime logic, which this
	 * method mirrors exactly but as a pure, side-effect-free read, so
	 * ASTProgram's upfront CanDeclareGlobalFunction/CanDeclareGlobalVar
	 * validation pre-pass can determine, before any binding is created,
	 * whether a given name would end up colliding with the real global
	 * object. Returns null when this eval's declaration of varName would NOT
	 * reach the real global (already shadowed locally/by a parent, or this
	 * eval is strict) - in that case no global-collision validation applies.
	 */
	public JSGlobalContext resolveGlobalDeclarationTarget(String varName);

	/**
	 * True when this eval's call site sits lexically inside SOME enclosing
	 * function's own parameter list (broader than the "arguments"-restriction
	 * check StandardLibrary.isCallerInParameterExpressionScope() performs at
	 * parse time - no arrow exemption here, since an arrow's own OTHER
	 * parameter can genuinely collide with an eval'd `var` too). Used by
	 * ASTProgram.evaluate()'s own EvalDeclarationInstantiation collision
	 * check (sub-case B: a parameter-scope `var` colliding with a same-named
	 * parameter) at declaration time. Default false so any OTHER
	 * implementation of this interface (if one exists without a caller
	 * concept at all) stays permissive.
	 */
	public default boolean isCallerInAnyParameterExpressionScope() {
		return false;
	}

}