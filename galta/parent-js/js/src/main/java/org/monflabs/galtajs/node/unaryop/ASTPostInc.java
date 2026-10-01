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
package org.monflabs.galtajs.node.unaryop;

import org.monflabs.galtajs.node.ASTNode;
import org.monflabs.galtajs.parser.Token;
import org.monflabs.galtajs.rt.JSResult;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.interpreter.InterpretedUnitRuntimeContext.Signal;
import org.monflabs.galtajs.rt.interpreter.JSInterpretedRuntimeContext;
import org.monflabs.galtajs.transpiler.context.JSTranspilerGeneratorContext;
import org.monflabs.galtajs.types.JSType;
import org.monflabs.util.StringFormat;


/**
 * Post increment.
 */
public class ASTPostInc extends ASTAbstractIncDec {

	public ASTPostInc(Token t, ASTNode node) {
		super(t,node);
	}

	@Override
	public Signal evaluate(JSInterpretedRuntimeContext context, JSResult result) {
		try {
			
			getNode().evaluateAssign(context, null, 
				(v) -> RuntimeUtil.incNumber(context.getEnvironment(),v), 
				result, 
				(v) -> RuntimeUtil.toNumeric(context.getEnvironment(),v)
			);
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
	public String transpileJavaExpression(JSTranspilerGeneratorContext jsContext) {
    	return getNode().transpileJavaAssignment(jsContext, ASSIGN_TYPE.POSTINC, null, getNode().isSequence(), true);
    }
    
    @Override
	public String decompileExpression() {
		return StringFormat.format("{0}{1}", getNode().decompileExpression(), decompileOperator());
	}
    @Override
	protected String decompileOperator() {
    	return "++";
    }
}