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

import org.monflabs.galtajs.transpiler.JSTranspiler;

/**
 * Transpiler context.
 */
public class TranspilerGeneratorFunctionContext extends JSTranspilerGeneratorContext {

	// null = inherit from parent (see getContextJavaName below) - non-null pins
	// this scope's context name regardless of what the parent reports.
	private final String contextJavaName;

	// Used for a genuinely NEW function body scope (ASTFunction.transpileFunctionExpression) -
	// always resets to the plain ambient _ctx, regardless of what encloses it: the
	// enclosing scope's own name (e.g. "classScope") refers to a Java variable that
	// doesn't exist inside THIS function's own callVoid - only its own _ctx parameter does.
	public TranspilerGeneratorFunctionContext(JSTranspilerGeneratorContext parent) {
		super(parent);
		this.contextJavaName = JSTranspiler.MAIN_CONTEXT;
	}

	// Used by ASTClassDecl. Two cases:
	//  - contextJavaName!=null (e.g. "classScope"): this class declares private
	//    members - pin every construction/private-access site in its own body to
	//    its own new per-evaluation scope.
	//  - contextJavaName==null: this class declares NO private members of its
	//    own. Unlike the single-arg constructor above, this does NOT introduce a
	//    new Java method scope (we're still inside whatever enclosing
	//    initClass/initInstance/callVoid body the class declaration itself
	//    appears in) - so it must INHERIT the enclosing scope's own context name
	//    via the default getContextJavaName() chain-walk instead of resetting.
	//    This matters for a class declared inline inside an OUTER private-bearing
	//    class's own body (e.g. an instance field initializer `x = class Inner {
	//    ... }`): Inner's own construction sites must still resolve through the
	//    OUTER class's "classScope", not a nonexistent "_ctx" (found via test262's
	//    private-field-on-nested-class.js family of failures).
	public TranspilerGeneratorFunctionContext(JSTranspilerGeneratorContext parent, String contextJavaName) {
		super(parent);
		this.contextJavaName = contextJavaName;
	}

	@Override
	public String getContextJavaName() {
		return contextJavaName!=null ? contextJavaName : super.getContextJavaName();
	}
}
