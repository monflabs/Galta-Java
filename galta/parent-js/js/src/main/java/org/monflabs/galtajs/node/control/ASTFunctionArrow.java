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
package org.monflabs.galtajs.node.control;

import java.util.List;

import org.monflabs.galtajs.node.ASTNode;
import org.monflabs.galtajs.node.literal.ASTArrayLiteral;
import org.monflabs.galtajs.parser.Token;
import org.monflabs.galtajs.rt.JSResult;
import org.monflabs.galtajs.rt.builtins.standard.function.BuiltinFunction;
import org.monflabs.galtajs.rt.builtins.standard.function.BuiltinFunctionInterpreter;
import org.monflabs.galtajs.rt.interpreter.InterpretedUnitRuntimeContext.Signal;
import org.monflabs.galtajs.rt.interpreter.JSInterpretedRuntimeContext;
import org.monflabs.galtajs.types.JSType;
import org.monflabs.galtajs.util.JavaBuilder;


/**
 * Arrow function declaration.
 * It is anonymous, so it is not registered in the variable context, but returned when hit.
 */
public class ASTFunctionArrow extends ASTFunction {

	private boolean generator;
	private boolean async;

	public ASTFunctionArrow(Token t, ASTArrayLiteral parameters, List<ASTNode> nodes) {
		super(t, null, parameters, nodes);
	}

	@Override
	public boolean isArrow() {
		return true;
	}

	@Override
	public boolean isGenerator() {
		return generator;
	}

	public void setGenerator(boolean generator) {
		this.generator = generator;
	}

	@Override
	public boolean isAsync() {
		return async;
	}

	public void setAsync(boolean async) {
		this.async = async;
	}

	@Override
	public Signal evaluate(JSInterpretedRuntimeContext context, JSResult result) {
		try {
			BuiltinFunction fct = new BuiltinFunctionInterpreter(context, this, getVariables(),getParamLength());
			result.setValue(fct);
			return Signal.NONE;
		} catch(Throwable ex) {
			throw fillInStackTrace(ex);
		}
	}

    @Override
	public JSType getReturnedType() {
    	return JSType.UNKNOWN;
	}

    @Override
	public String decompileExpression() {
    	StringBuilder b = new StringBuilder();
		if(isAsync()) {
			b.append("async ");
		}
		b.append("(");
		if(getParameters()!=null) {
			b.append(getParameters().decompileParameters());
		}
		b.append(") => {\n");
		
		JavaBuilder jb = new JavaBuilder();
		jb.incIndent();
    	if(isForceStrictMode()) {
    		jb.println("\"use strict\";");
    	}
		decompileStatements(jb, getStatements());
		jb.decIndent();
		jb.append("}");

		b.append(jb.toString());
		return b.toString();
	}
}
