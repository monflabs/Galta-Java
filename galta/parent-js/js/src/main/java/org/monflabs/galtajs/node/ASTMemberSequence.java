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
package org.monflabs.galtajs.node;

import java.util.function.Function;

import org.monflabs.galtajs.parser.Token;
import org.monflabs.galtajs.rt.JSResult;
import org.monflabs.galtajs.rt.interpreter.InterpretedUnitRuntimeContext.Signal;
import org.monflabs.galtajs.rt.interpreter.JSInterpretedRuntimeContext;
import org.monflabs.galtajs.transpiler.JSTranspiler;
import org.monflabs.galtajs.transpiler.context.JSTranspilerGeneratorContext;
import org.monflabs.galtajs.types.JSType;


/**
 * Transform a value into a sequence.
 * ** This is not in use yet **
 */
public class ASTMemberSequence extends ASTNode {
	
	private ASTNode node;

	public ASTMemberSequence(Token t, ASTNode node) {
		super(t);
		this.node = assignParent(node);
	}

	@Override
	public STATEMENT_TYPE getStatementType() {
		return STATEMENT_TYPE.EXPRESSION;
	}	

	@Override
	public boolean isSequence() {
		return true;
	}	
	
	public ASTNode getNode() {
		return node;
	}

	@Override
	public String getNodeString() {
		return ".$";
	}

	@Override
	public int getChildCount() {
		return super.getChildCount()+1;
	}
	@Override
	public ASTNode getChild(int index) {
		switch(index) {
			case 0 ->		{ return node; }
			default ->		{ return super.getChild(index-1); }
		}
	}
	@Override
	protected void _setChild(int index, ASTNode node) {
		switch(index) {
			case 0 ->	{ this.node = node; }
			default ->  { super._setChild(index-1,node); }
		}
	}
	
	@Override
	public Signal evaluate(JSInterpretedRuntimeContext context, JSResult result) {
		try {
			node.evaluate(context,result);
			result.convertToSequence();
			return Signal.NONE;
		} catch(Throwable ex) {
			throw fillInStackTrace(ex);
		}		
	}
	
	@Override
	public void evaluateAssign(JSInterpretedRuntimeContext context, Object rightValue, Function<Object, Object> assigner, JSResult result, Function<Object, Object> returnOriginalValue) {
		try {
			getNode().evaluateAssign(context,rightValue,assigner,result,returnOriginalValue);
		} catch(Throwable ex) {
			throw fillInStackTrace(ex);
		}		
	}
	
	@Override
	public boolean evaluateDelete(JSInterpretedRuntimeContext context, JSResult result) {
		try {
			return getNode().evaluateDelete(context,result);
		} catch(Throwable ex) {
			throw fillInStackTrace(ex);
		}		
	}

    @Override
	public JSType getReturnedType() {
    	return node.getReturnedType();
	}

    @Override
    public String transpileJavaExpression(JSTranspilerGeneratorContext jsContext) {
   		return JSTranspiler.asResult(jsContext,node);
    }
    
    @Override
    public String transpileTypeofExpression(JSTranspilerGeneratorContext jsContext) {
    	return node.transpileTypeofExpression(jsContext);
    }
    
    @Override
    public String transpileDeleteExpression(JSTranspilerGeneratorContext jsContext) {
    	return node.transpileTypeofExpression(jsContext);
    }

    @Override
	public String transpileJavaAssignment(JSTranspilerGeneratorContext jsContext, ASSIGN_TYPE type, String rightValue, boolean sequence, boolean returnOriginalValue) {
    	return node.transpileJavaAssignment(jsContext,type,rightValue,sequence,returnOriginalValue);
    }

}
