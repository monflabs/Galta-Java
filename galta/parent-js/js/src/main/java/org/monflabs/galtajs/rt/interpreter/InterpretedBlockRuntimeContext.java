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
 * Block runtime context.
 */
public class InterpretedBlockRuntimeContext extends InterpretedRuntimeContext {

	private JSRuntimeContext varDeclContext;

	public InterpretedBlockRuntimeContext(JSInterpretedRuntimeContext parent) {
		super(parent);
	}

	// Typo fix: was "isStricMode" (missing 't'), so this never actually
	// overrode JSContext's default isStrictMode() (which falls back to the
	// STATIC environment-wide config flag instead of the enclosing script/
	// function's real dynamic strict-mode-ness) - any block that creates its
	// own InterpretedBlockRuntimeContext (any {} with a let/const/class
	// declaration: ASTBlock, ASTTry's try/catch/finally bodies, etc.) and
	// gets .with()-entered as the ambient JSContext silently reported the
	// wrong strict-mode-ness to every DESC_CHECK.CHECK-based dynamic check
	// (assignment to non-writable properties, setter-less accessors, etc.)
	// for any code running inside it.
	@Override
	public boolean isStrictMode() {
		return getParent().isStrictMode();
	}

	@Override
	public JSRuntimeContext getVarDeclContext() {
		if(varDeclContext==null) {
			for(JSRuntimeContext c=getParent(); c!=null; c=c.getParent()) {
				if(c instanceof JSInterpretedRuntimeContext ic) {
					varDeclContext = ic.getVarDeclContext();
					break;
				}
			}
		}
		return varDeclContext;
	}
}
