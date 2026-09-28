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

import org.monflabs.galtajs.rt.builtins.privatename.PrivateName;
import org.monflabs.galtajs.rt.builtins.standard.function.BuiltinClassConstructor;

/**
 * Runtime scope introduced exactly once per class evaluation (spec:
 * PrivateEnvironment), sitting between the enclosing scope and the class
 * body's initClass/initInstance block contexts - see ASTClassDecl.evaluate().
 * Every method/getter/setter/field-initializer closure created while this
 * class is being evaluated captures this context (directly or transitively)
 * as part of its normal closure chain, so a #name reference anywhere inside
 * the class body - including nested arrow functions - resolves to THIS
 * evaluation's PrivateName tokens via the ordinary parent-chain walk. A
 * nested class's own #name (if any) is found first (see resolvePrivateName);
 * otherwise the walk continues outward, so a nested class can still see an
 * outer enclosing class's private names.
 */
public class InterpretedClassPrivateScopeContext extends InterpretedBlockRuntimeContext {

	private final BuiltinClassConstructor clazz;

	public InterpretedClassPrivateScopeContext(JSInterpretedRuntimeContext parent, BuiltinClassConstructor clazz) {
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

	// See JSRuntimeContext.collectEnclosingPrivateNames()'s doc.
	@Override
	public void collectEnclosingPrivateNames(java.util.Set<String> into) {
		into.addAll(clazz.getOwnPrivateNames());
		super.collectEnclosingPrivateNames(into);
	}
}
