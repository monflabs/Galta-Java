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
package org.monflabs.galtajs.node.binaryop;

import org.monflabs.galtajs.StaticConfiguration;
import org.monflabs.galtajs.node.ASTNode;
import org.monflabs.galtajs.parser.Token;
import org.monflabs.galtajs.rt.JSResult;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.interpreter.JSInterpretedRuntimeContext;
import org.monflabs.galtajs.transpiler.JSTranspiler;
import org.monflabs.galtajs.transpiler.context.JSTranspilerGeneratorContext;
import org.monflabs.galtajs.types.JSType;
import org.monflabs.util.StringFormat;




/**
 * And binary operation Node.
 */
public class ASTAnd extends ASTLogicalOp {

	public ASTAnd(Token t, ASTNode leftNode, ASTNode rightNode) {
		super(t,leftNode,rightNode);
	}
	
	// Cannot do pair wise operations
	@Override
	public Object evaluateValue(JSInterpretedRuntimeContext context, JSResult result) {
		try {
			Object leftValue=leftNode.evaluateValue(context,result);
			if(!RuntimeUtil.toBoolean(context.getEnvironment(),leftValue)) {
				return leftValue;
			}
			return rightNode.evaluateValue(context,result);
		} catch(Throwable ex) {
			throw fillInStackTrace(ex);
		}
	}

    @Override
	public JSType getReturnedType() {
    	return JSType.join(getLeftNode().getReturnedType(), getRightNode().getReturnedType());
	}

    @Override
	public String transpileJavaExpression(JSTranspilerGeneratorContext jsContext) {
    	if(isSequence()) {
    		return StringFormat.format("seq(RuntimeUtil::and,{0},{1})",JSTranspiler.asRawValue(jsContext,leftNode), JSTranspiler.asRawValue(jsContext,rightNode));
    	} else {
    		// Generation optimization
        	JSType leftType = getLeftNode().getReturnedType();
        	JSType rightType = getRightNode().getReturnedType();
        	if(leftType==JSType.BOOLEAN && rightType==JSType.BOOLEAN) {
            	return StringFormat.format("{0} && {1}", JSTranspiler.asValue(jsContext,leftNode), JSTranspiler.asValue(jsContext,rightNode));
        	}
        	// Looks like the javac compiler had a issues with the lambdas
        	// The unit tests were failing - Looks like it is fixed now
        	if(StaticConfiguration.TRANSPILER_NO_CLOSURE) {
            	return StringFormat.format("(andTest({0}={1}) ? {0} : ({2}))", JSTranspiler.TEMP_VAR, JSTranspiler.asValue(jsContext,leftNode), JSTranspiler.asValue(jsContext,rightNode));
        	} else {
        		return StringFormat.format("and({0},()-> {1})", JSTranspiler.asValue(jsContext,leftNode), JSTranspiler.asValue(jsContext,rightNode));
        	}
    	}
    }
    
    @Override
	protected String decompileOperator() {
    	return " && ";
    }
}