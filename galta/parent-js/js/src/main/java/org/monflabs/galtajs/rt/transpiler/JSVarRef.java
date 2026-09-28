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

import org.monflabs.galtajs.rt.interpreter.JSInterpretedRuntimeContext.VAR_TYPE;

/**
 * Variable object.
 */
public class JSVarRef implements VarAccessor {

	public static JSVarRef of(String name, Object[] vars, int index) {
		return new JSVarRef(name,vars,index,VAR_TYPE.VAR,true);
	}

	// The eval-args VarAccessor[] bundle (see ASTCall.getVariableJavaReferences)
	// needs the REAL declared VAR_TYPE (LET/CONST/PREDECLARED/etc.), not just
	// the interface default, so that a direct eval's own var/lexical-collision
	// early-SyntaxError check (ASTProgram.evaluate's JSEvalRuntimeContext
	// branch, which reads getType()==LET||CONST) can actually see a captured
	// `let`/`const` for what it is instead of every transpiled local reporting
	// as VAR_TYPE.VAR unconditionally.
	public static JSVarRef of(String name, Object[] vars, int index, VAR_TYPE type) {
		return new JSVarRef(name,vars,index,type,true);
	}

	// Used only by the direct-eval bundle (ASTCall.getVariableJavaReferences) to
	// tag an entry that was only reached by walking OUT past the eval call site's
	// own enclosing function/global boundary - see VarAccessor.isOwnScope()'s own
	// doc for why this distinction matters for a direct eval's own var/function
	// declarations.
	public static JSVarRef of(String name, Object[] vars, int index, VAR_TYPE type, boolean ownScope) {
		return new JSVarRef(name,vars,index,type,ownScope);
	}

	private String name;
	private Object[] vars;
	private int index;
	private VAR_TYPE type;
	private boolean ownScope;

	private JSVarRef(String name, Object[] vars, int index, VAR_TYPE type, boolean ownScope) {
		this.name = name;
		this.vars = vars;
		this.index = index;
		this.type = type;
		this.ownScope = ownScope;
	}

	@Override
	public String getKey() {
		return name;
	}

	@Override
	public Object getValue() {
		return vars[index];
	}

	@Override
	public Object setValue(Object value) {
		return vars[index] = value;
	}

	@Override
	public VAR_TYPE getType() {
		return type;
	}

	@Override
	public boolean isOwnScope() {
		return ownScope;
	}
}
