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
package org.monflabs.galtajs.node.assignop;

import org.monflabs.galtajs.node.ASTArrayMember;
import org.monflabs.galtajs.node.ASTNode;
import org.monflabs.galtajs.parser.Token;
import org.monflabs.galtajs.rt.JSResult;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.interpreter.InterpretedUnitRuntimeContext.Signal;
import org.monflabs.galtajs.rt.interpreter.JSInterpretedRuntimeContext;
import org.monflabs.galtajs.transpiler.context.JSTranspilerGeneratorContext;
import org.monflabs.galtajs.types.JSType;




/**
 * Assignment node.
 */
public class ASTAssignLShift extends ASTAbstractAssign {

	public ASTAssignLShift(Token t, ASTNode leftNode, ASTNode rightNode) {
		super(t,leftNode,rightNode);
	}

	@Override
	public Signal evaluate(JSInterpretedRuntimeContext context, JSResult result) {
		try {
			if(leftNode instanceof ASTArrayMember arrayMember && arrayMember.canResolveReference()) {
				ASTArrayMember.ResolvedReference ref = arrayMember.resolveReference(context, result);
				ref = ASTArrayMember.coerceKey(context.getEnvironment(), ref);
				Object rightValue = getRightNode().evaluateValue(context, result);
				if(ref!=null) {
					arrayMember.assignToResolved(context, ref, rightValue, (v) -> RuntimeUtil.lshift(context.getEnvironment(),v,rightValue), result, null);
				}
			} else {
				// RHS must be evaluated after the LHS reference is resolved - see
				// ASTAssignAdd's else branch for the rationale.
				leftNode.evaluateAssign(context, null, (v) -> RuntimeUtil.lshift(context.getEnvironment(),v,getRightNode().evaluateValue(context, new JSResult())), result, null);
			}
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
    	String rightValue = getRightNode().transpileJavaExpression(jsContext);
    	return getLeftNode().transpileJavaAssignment(jsContext, ASSIGN_TYPE.EQUALS_LSHIFT, rightValue, getRightNode().isSequence(), false);
    }
    
    @Override
	protected String decompileOperator() {
    	return " <<= ";
    }
}