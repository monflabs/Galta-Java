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
import org.monflabs.galtajs.transpiler.JSTranspiler;
import org.monflabs.galtajs.transpiler.context.JSTranspilerGeneratorContext;
import org.monflabs.galtajs.types.JSType;




/**
 * Array instantiation operator (new []).
 */
public class ASTNewArray extends ASTNew {

	private ASTNode arrayExpression;
	private int dimensions;

	public ASTNewArray(Token t, ASTNode typeNode, int dimensions, ASTNode arrayExpression) {
		super(t,typeNode,null);
		this.arrayExpression = assignParent(arrayExpression);
		this.dimensions = dimensions;
	}

	@Override
	public int getChildCount() {
		return super.getChildCount()+1;
	}
	@Override
	public ASTNode getChild(int index) {
		switch(index) {
			case 0 ->	{ return arrayExpression; }
			default ->	{ return super.getChild(index-1); }
		}
	}
	@Override
	protected void _setChild(int index, ASTNode node) {
		switch(index) {
			case 0 ->	{ this.arrayExpression = node; }
			default ->  { super._setChild(index-1,node); }
		}
	}

	@Override
	public Signal evaluate(JSInterpretedRuntimeContext context, JSResult result) {
		try {
			Object value = getTypeNode().evaluateValue(context,result);
			Object sizeObject = arrayExpression.evaluateValue(context,result);
			int size = RuntimeUtil.toInt32(context.getEnvironment(),sizeObject);
			result.setValue(RuntimeUtil.constructArray(context.getEnvironment(), value, dimensions, size));
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
    	StringBuilder b = new StringBuilder(64);
    	b.append("newArray(");
    	b.append(JSTranspiler.asValue(jsContext, getTypeNode()));
    	b.append(",");
    	b.append(dimensions);
    	b.append(",");
    	b.append(JSTranspiler.asValue(jsContext, arrayExpression));
    	b.append(")");
    	return b.toString();
    }
    
    @Override
	public String decompileExpression() {
    	StringBuilder b = new StringBuilder();
		b.append("new ");
		b.append(getTypeNode().decompileExpression());
		b.append("[");
		if(dimensions>=1) {
			for(int i=0; i<dimensions; i++ ) {
				b.append("][");
			}
		}
		b.append(arrayExpression.decompileExpression());
		b.append("]");
		return b.toString();
	}
}