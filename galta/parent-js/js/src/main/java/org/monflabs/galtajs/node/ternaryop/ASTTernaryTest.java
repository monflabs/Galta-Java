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
package org.monflabs.galtajs.node.ternaryop;

import org.monflabs.galtajs.node.ASTNode;
import org.monflabs.galtajs.parser.Token;
import org.monflabs.galtajs.rt.JSResult;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.interpreter.InterpretedUnitRuntimeContext.Signal;
import org.monflabs.galtajs.rt.interpreter.JSInterpretedRuntimeContext;
import org.monflabs.galtajs.transpiler.JSTranspiler;
import org.monflabs.galtajs.transpiler.context.JSTranspilerGeneratorContext;
import org.monflabs.galtajs.types.JSType;
import org.monflabs.util.StringFormat;




/**
 * Ternary test operator Node.
 */
public class ASTTernaryTest extends ASTTernaryOp {

	public ASTTernaryTest(Token t, ASTNode condNode, ASTNode thenNode, ASTNode elseNode) {
		super(t,condNode,elseNode,thenNode);
	}
	
	@Override
	public Signal evaluate(JSInterpretedRuntimeContext context, JSResult result) {
		try {
			Object value = getOp1Node().evaluateValue(context,result);
			if(RuntimeUtil.toBoolean(context.getEnvironment(),value)) {
				// Elvis test
				if(getOp3Node()==null) {
					result.setValue(value);
				} else {
					getOp3Node().evaluate(context,result);
				}
			} else {
				getOp2Node().evaluate(context,result);
			}
			return Signal.NONE;
		} catch(Throwable ex) {
			throw fillInStackTrace(ex);
		}		
	}

    @Override
	public JSType getReturnedType() {
		// Elvis form (`a ?: b`, getOp3Node()==null) yields the condition's
		// own value when truthy - see evaluate() above - so its "true branch"
		// type is op1Node's, not a missing op3Node's.
		JSType trueType = getOp3Node()!=null ? getOp3Node().getReturnedType() : getOp1Node().getReturnedType();
		JSType falseType = getOp2Node().getReturnedType();
    	return JSType.join(trueType, falseType);
	}

    @Override
	public String transpileJavaExpression(JSTranspilerGeneratorContext jsContext) {
		if(getOp3Node()==null) {
	    	String test = JSTranspiler.asValue(jsContext, getOp1Node());
	    	String ifTrue = JSTranspiler.asValue(jsContext, getOp2Node());
	    	return StringFormat.format("elvis({0}, () -> {1})", test, ifTrue);
		} else {
	    	String test = JSTranspiler.asBoolean(jsContext, getOp1Node());
	    	String ifTrue = JSTranspiler.asValue(jsContext, getOp3Node());
	    	String ifFalse = JSTranspiler.asValue(jsContext, getOp2Node());
	    	// We surround with parenthesis so the parameters support assignment
	    	return StringFormat.format("(({0}) ? ({1}) : ({2}))", test, ifTrue, ifFalse);
		}
    }
    
    @Override
	public String decompileExpression() {
    	StringBuilder b = new StringBuilder();
		b.append(getOp1Node().decompileExpression());
		if(getOp3Node()!=null) {
			b.append(" ? ");
			b.append(getOp3Node().decompileExpression());
			b.append(" : ");
			b.append(getOp2Node().decompileExpression());
		} else {
			b.append(" ?: ");
			b.append(getOp2Node().decompileExpression());
		}
		return b.toString();
    }
}