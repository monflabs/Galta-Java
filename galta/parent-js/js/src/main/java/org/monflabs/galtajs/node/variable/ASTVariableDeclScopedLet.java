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
package org.monflabs.galtajs.node.variable;

import org.monflabs.galtajs.parser.Token;
import org.monflabs.galtajs.rt.JSRuntimeContext;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.interpreter.JSInterpretedRuntimeContext.VAR_TYPE;


/**
 * Scoped variable definition.
 * 
 * let v = ...
 * 
 */
public class ASTVariableDeclScopedLet extends ASTVariableDecl {

    public ASTVariableDeclScopedLet(Token t) {
    	super(t);
    }

    @Override
	public VAR_TYPE getVarType() {
    	return VAR_TYPE.LET;
    }

	@Override
	protected JSRuntimeContext getDeclContext(JSRuntimeContext context) {
		return context;
	}

	@Override
	protected void createVariable(JSRuntimeContext declContext, String name, Object value) {
		// The binding already exists (as a TDZ placeholder installed at
		// container-entry, see ASTBlock/ASTProgram/BuiltinFunctionInterpreter) -
		// initialize it in place rather than re-declaring, since VariableMap
		// .createVariable() would reject a second declaration of a LET name.
		// NOT_AVAILABLE (e.g. a destructuring rest-pattern binding with no
		// matching source value) still normalizes to UNDEFINED, matching
		// createVariable()'s own new-entry semantics.
		declContext.getVariableMap(true).set(name, value!=RuntimeUtil.NOT_AVAILABLE ? value : RuntimeUtil.UNDEFINED);
	}
}