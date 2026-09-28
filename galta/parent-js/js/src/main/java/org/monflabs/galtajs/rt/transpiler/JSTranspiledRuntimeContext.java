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

/**
 * Runtime context used by the interpreter.
 */
public interface JSTranspiledRuntimeContext extends JSRuntimeContext {

	public Object getReturnValue();
	
	public void setReturnValue(Object returnValue);
	
	// varTypes: each entry's real VAR_TYPE.name() (VAR/FUNCTION/AUTO/LET/
	// CONST/USING/...), parallel to varNames - see GlobalThis's own
	// scriptVariables field doc for why this matters: only a var/function-
	// declared name is a genuine globalThis own-property per spec (a
	// lexical global declaration lives in the Global Environment Record's
	// separate declarative record, never as an object property at all).
	public default void initGlobalVariables(Object[] variables, String[] varNames, String[] varTypes) {
		initGlobalVariables(variables, varNames, varTypes, null);
	}
	// annexBBlockHoisted: parallel to varNames/varTypes - true for a
	// FUNCTION entry that's an Annex-B block-hoisted candidate (see
	// ASTVarContainer.VariableDefContainer.annexBBlockHoisted()'s own doc).
	// Distinguishes B.3.3.3's CreateGlobalVarBinding synchronization step
	// (never touches an existing global property's descriptor) from an
	// ordinary top-level function declaration's CreateGlobalFunctionBinding
	// (unconditionally redefines it) - TranspiledGlobalRuntimeContext's own
	// override is the only implementation that looks at this; null (every
	// caller before this parameter existed) means "no entries are".
	public default void initGlobalVariables(Object[] variables, String[] varNames, String[] varTypes, boolean[] annexBBlockHoisted) {
		initGlobalVariables(variables, varNames, varTypes);
	}
}
