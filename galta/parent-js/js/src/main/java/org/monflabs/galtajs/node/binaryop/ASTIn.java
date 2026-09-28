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

import org.monflabs.galtajs.node.ASTNode;
import org.monflabs.galtajs.node.literal.ASTLiteral;
import org.monflabs.galtajs.node.literal.ASTPrivateNameLiteral;
import org.monflabs.galtajs.parser.Token;
import org.monflabs.galtajs.rt.JSResult;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.RuntimeUtil.MODE;
import org.monflabs.galtajs.rt.builtins.privatename.PrivateName;
import org.monflabs.galtajs.rt.interpreter.InterpretedUnitRuntimeContext.Signal;
import org.monflabs.galtajs.rt.interpreter.JSInterpretedRuntimeContext;
import org.monflabs.galtajs.transpiler.JSTranspiler;
import org.monflabs.galtajs.transpiler.context.JSTranspilerGeneratorContext;
import org.monflabs.galtajs.types.JSType;
import org.monflabs.util.StringFormat;



/**
 * In operator.
 */
public class ASTIn extends ASTComparisonOp {

	public ASTIn(Token t, RuntimeUtil.MODE mode, ASTNode leftNode, ASTNode rightNode) {
		super(t,RuntimeUtil::in,mode,leftNode,rightNode);
	}

	// #name in obj: the parser marks a genuine private-name brand-check
	// reference with ASTPrivateNameLiteral (distinct from a coincidental
	// ordinary string literal like "#name" that happens to start with "#" -
	// see that class's doc). It needs the enclosing class evaluation's
	// PrivateName token (via context.resolvePrivateName), which the shared
	// TriPredicate-based comparison framework (RuntimeUtil::in, only given an
	// env + two already-evaluated values) has no way to carry through.
	// Handled directly here instead; anything else (an ordinary `"x" in
	// obj`, or the rare sequence-mode `*in`) still goes through the normal
	// generic path.
	@Override
	public Signal evaluate(JSInterpretedRuntimeContext context, JSResult result) {
		if(getMode()==MODE.ONE && leftNode instanceof ASTPrivateNameLiteral lit) {
			try {
				String s = (String)lit.getValue();
				Object rightValue = rightNode.evaluateValue(context, result);
				if(!RuntimeUtil.isObject(context.getEnvironment(), rightValue)) {
					throw RuntimeUtil.typeError("Cannot use 'in' operator to search for private member {0} in a non-object value", s);
				}
				PrivateName pn = context.resolvePrivateName(s);
				result.setValue(pn!=null && RuntimeUtil.hasPrivateField(rightValue, pn));
				return Signal.NONE;
			} catch(Throwable ex) {
				throw fillInStackTrace(ex);
			}
		}
		return super.evaluate(context, result);
	}

    @Override
	public JSType getReturnedType() {
    	return JSType.BOOLEAN;
	}

    @Override
	public String transpileJavaExpression(JSTranspilerGeneratorContext jsContext) {
    	// See evaluate() above - the same private-name brand-check special case,
    	// mirrored for transpiled code.
    	if(getMode()==MODE.ONE && leftNode instanceof ASTPrivateNameLiteral lit) {
    		return StringFormat.format("privateIn({0},{1},{2})",
    				jsContext.getContextJavaName(),
    				JSTranspiler.asValue(jsContext, rightNode),
    				ASTLiteral.encodeString((String)lit.getValue()));
    	}
    	if(leftNode.isSequence() || rightNode.isSequence()) {
    		return StringFormat.format("seqCmp(RuntimeUtil::in,{0},{1},MODE.{2})", JSTranspiler.asRawValue(jsContext,leftNode), JSTranspiler.asRawValue(jsContext,rightNode), getMode());
    	} else {
    		return StringFormat.format("in({0},{1})", JSTranspiler.asValue(jsContext,leftNode), JSTranspiler.asValue(jsContext,rightNode));
    	}
    }
    
    @Override
	protected String decompileOperator() {
    	return getMode()==MODE.ALL ? " *in " : " in ";
    }
}