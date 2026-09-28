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

import java.util.function.Function;

import org.monflabs.galtajs.node.ASTNode;
import org.monflabs.galtajs.node.NoopNode;
import org.monflabs.galtajs.parser.Token;
import org.monflabs.galtajs.rt.JSResult;
import org.monflabs.galtajs.rt.interpreter.InterpretedUnitRuntimeContext.Signal;
import org.monflabs.galtajs.rt.interpreter.JSInterpretedRuntimeContext;
import org.monflabs.galtajs.transpiler.JSTranspiler;
import org.monflabs.galtajs.transpiler.context.JSTranspilerGeneratorContext;
import org.monflabs.galtajs.types.JSType;
import org.monflabs.util.StringFormat;


/**
 * Parenthesis within an expression.
 */
public class ASTParen extends ASTUnaryOp implements NoopNode {

	public ASTParen(Token t, ASTNode content) {
		super(t,content);
	}

	@Override
	public STATEMENT_TYPE getStatementType() {
		return STATEMENT_TYPE.EXPRESSION;
	}	

	@Override
	public Signal evaluate(JSInterpretedRuntimeContext context, JSResult result) {
		try {
			getNode().evaluate(context, result);
			return Signal.NONE;
		} catch(Throwable ex) {
			throw fillInStackTrace(ex);
		}		
	}
	
	@Override
	public Signal evaluateTypeof(JSInterpretedRuntimeContext context, JSResult result) {
		return getNode().evaluateTypeof(context,result);
	}
	
	@Override
	public void evaluateAssign(JSInterpretedRuntimeContext context, Object rightValue, Function<Object, Object> assigner, JSResult result, Function<Object, Object> returnOriginalValue) {
		getNode().evaluateAssign(context,rightValue,assigner,result,returnOriginalValue);
	}
	
	@Override
	public boolean evaluateDelete(JSInterpretedRuntimeContext context, JSResult result) {
		return getNode().evaluateDelete(context,result);
	}

    @Override
	public JSType getReturnedType() {
    	return getNode().getReturnedType();
	}
    
    
	
	//
	// Transpiler
	//
    
    @Override
	public String transpileJavaExpression(JSTranspilerGeneratorContext jsContext) {
    	return StringFormat.format("({0})", JSTranspiler.asValue(jsContext,getNode()));
    }

    @Override
	public String transpileTypeofExpression(JSTranspilerGeneratorContext jsContext) {
    	return getNode().transpileTypeofExpression(jsContext);
    }

    @Override
    public String transpileDeleteExpression(JSTranspilerGeneratorContext jsContext) {
    	return getNode().transpileDeleteExpression(jsContext);
    }
    
    @Override
	public String transpileJavaAssignment(JSTranspilerGeneratorContext jsContext, ASSIGN_TYPE type, String rightValue, boolean sequence, boolean returnOriginalValue) {
    	return getNode().transpileJavaAssignment(jsContext,type,rightValue,sequence,returnOriginalValue);
    }
    
    @Override
	public String decompileExpression() {
		return StringFormat.format("({0})", getNode().decompileExpression() );
	}
    @Override
	protected String decompileOperator() {
    	return "()"; // not used...
    }
}
