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
package org.monflabs.galtajs.rt.transpiler;

import org.monflabs.galtajs.rt.JSRuntimeContext;
import org.monflabs.galtajs.rt.builtins.privatename.PrivateName;
import org.monflabs.galtajs.rt.builtins.standard.function.BuiltinClassConstructor;

/**
 * Transpiled-mode counterpart of
 * {@code org.monflabs.galtajs.rt.interpreter.InterpretedClassPrivateScopeContext}
 * - a runtime scope introduced once per class evaluation (spec:
 * PrivateEnvironment), cached in a field on the generated
 * {@code BuiltinClassConstructor.Initializer} and used as the captured
 * "current context" for every function object (method/getter/setter/
 * constructor) built while this class is being evaluated - see
 * ASTClassDecl.transpileJavaExpression. A #name reference anywhere inside
 * the class body (including nested arrow functions) resolves to THIS
 * evaluation's PrivateName tokens via the ordinary parent-chain walk
 * (JSRuntimeContext.resolvePrivateName). A nested class's own #name (if any)
 * is found first; otherwise the walk continues outward, so a nested class
 * can still see an outer enclosing class's private names.
 */
public class TranspiledClassPrivateScopeRuntimeContext extends TranspiledRuntimeContext {

	private final BuiltinClassConstructor clazz;

	public TranspiledClassPrivateScopeRuntimeContext(JSRuntimeContext parent, BuiltinClassConstructor clazz) {
		super(parent);
		this.clazz = clazz;
	}

	@Override
	public PrivateName resolvePrivateName(String name) {
		PrivateName pn = clazz.getOwnPrivateName(name);
		if(pn!=null) {
			return pn;
		}
		return super.resolvePrivateName(name);
	}
}
