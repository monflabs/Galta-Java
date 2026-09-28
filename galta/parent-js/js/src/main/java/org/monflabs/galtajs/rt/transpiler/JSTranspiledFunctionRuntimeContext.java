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

import org.monflabs.galtajs.rt.JSFunctionContext;

public interface JSTranspiledFunctionRuntimeContext extends JSTranspiledRuntimeContext, JSFunctionContext {

	// Per-call local-variable slot array (see ASTVarContainer.
	// transpilerDeclareStatement) - normally allocated and read back within
	// the SAME generated method, but a generator function's parameter-binding
	// prologue (initGeneratorParams, run eagerly at call time) and body
	// (callVoid, run lazily on first next()) are two SEPARATE generated
	// methods - this is how the array crosses that boundary. Unused by
	// ordinary (non-generator) functions.
	public Object[] getLocals();
	public void setLocals(Object[] locals);
}
