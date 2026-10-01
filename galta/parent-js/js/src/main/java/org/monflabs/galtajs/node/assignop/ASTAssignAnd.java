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

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.node.ASTArrayMember;
import org.monflabs.galtajs.node.ASTIdentifier;
import org.monflabs.galtajs.node.ASTMember;
import org.monflabs.galtajs.node.ASTNode;
import org.monflabs.galtajs.node.clazz.ASTClassDecl;
import org.monflabs.galtajs.node.control.ASTFunction;
import org.monflabs.galtajs.parser.Token;
import org.monflabs.galtajs.rt.JSResult;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.interpreter.InterpretedUnitRuntimeContext.Signal;
import org.monflabs.galtajs.rt.interpreter.JSInterpretedRuntimeContext;
import org.monflabs.galtajs.transpiler.context.JSTranspilerGeneratorContext;
import org.monflabs.galtajs.types.JSType;
import org.monflabs.util.StringUtil;




/**
 * Assignment node.
 */
public class ASTAssignAnd extends ASTAbstractAssign {

	public ASTAssignAnd(Token t, ASTNode leftNode, ASTNode rightNode) {
		super(t,leftNode,rightNode);
	}

	@Override
	public void init(InitContext initContext) {
		// NamedEvaluation: only when the LHS is a plain identifier reference (unlike
		// plain "=", logical assignment operators don't extend this to member targets).
		// skipTransparent() unwraps a ParenthesizedExpression wrapper, which
		// HasName/IsFunctionDefinition propagate through per spec - see ASTAssign.
		ASTNode rightNode = skipTransparent(this.rightNode);
		if(rightNode instanceof ASTFunction fd && StringUtil.isEmpty(fd.getFunctionName()) && leftNode instanceof ASTIdentifier id) {
			fd.setFunctionName(id.getId());
		} else if(rightNode instanceof ASTClassDecl cd && StringUtil.isEmpty(cd.getClassName()) && leftNode instanceof ASTIdentifier id) {
			cd.setClassName(id.getId());
		}
		super.init(initContext);
	}

	@Override
	public Signal evaluate(JSInterpretedRuntimeContext context, JSResult result) {
		try {
			if(leftNode instanceof ASTArrayMember arrayMember && arrayMember.canResolveReference()) {
				ASTArrayMember.ResolvedReference ref = arrayMember.resolveReference(context, result);
				if(ref!=null) {
					JSEnvironment env = context.getEnvironment();
					ref = ASTArrayMember.coerceKey(env, ref);
					Object oldValue = RuntimeUtil.getProperty(env, ref.base(), ref.index());
					if(!RuntimeUtil.toBoolean(env, oldValue)) {
						result.setValue(oldValue);
					} else {
						Object rightValue = getRightNode().evaluateValue(context, new JSResult());
						RuntimeUtil.setProperty(env, ref.base(), ref.index(), rightValue);
						result.setValue(rightValue);
					}
				}
			} else if(leftNode instanceof ASTMember member && member.canResolveReference()) {
				ASTMember.ResolvedReference ref = member.resolveReference(context, result);
				if(ref!=null) {
					JSEnvironment env = context.getEnvironment();
					// member.readProperty/writeProperty, not the plain
					// RuntimeUtil.getProperty/setProperty(env,base,memberName)
					// overloads - those don't know about PrivateName
					// resolution (see ASTMember.readProperty's doc).
					Object oldValue = member.readProperty(env, ref.base(), context);
					if(!RuntimeUtil.toBoolean(env, oldValue)) {
						result.setValue(oldValue);
					} else {
						Object rightValue = getRightNode().evaluateValue(context, new JSResult());
						member.writeProperty(env, ref.base(), rightValue, context);
						result.setValue(rightValue);
					}
				}
			} else if(leftNode instanceof ASTIdentifier ident) {
				evaluateLogicalAssign(context, ident, (v) -> RuntimeUtil.toBoolean(context.getEnvironment(), v), result);
			} else {
				leftNode.evaluateAssign(context, null, (v) -> RuntimeUtil.and(context.getEnvironment(), v, ()->getRightNode().evaluateValue(context, new JSResult())), result, null);
			}
			return Signal.NONE;
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
    	String rightValue = getRightNode().transpileJavaExpression(jsContext);
    	return getLeftNode().transpileJavaAssignment(jsContext, ASSIGN_TYPE.EQUALS_AND, rightValue, getRightNode().isSequence(), false);
    }
    
    @Override
	protected String decompileOperator() {
    	return " &&= ";
    }
}