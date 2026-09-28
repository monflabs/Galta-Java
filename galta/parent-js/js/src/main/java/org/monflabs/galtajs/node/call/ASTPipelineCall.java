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
package org.monflabs.galtajs.node.call;

import org.monflabs.galtajs.node.ASTNode;
import org.monflabs.galtajs.parser.Token;
import org.monflabs.galtajs.rt.JSResult;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.builtins.Callable;
import org.monflabs.galtajs.rt.interpreter.InterpretedUnitRuntimeContext.Signal;
import org.monflabs.galtajs.rt.interpreter.JSInterpretedRuntimeContext;
import org.monflabs.galtajs.transpiler.JSTranspiler;
import org.monflabs.galtajs.transpiler.context.JSTranspilerGeneratorContext;
import org.monflabs.galtajs.types.JSType;


/**
 * Pipleline function call.
 */
public class ASTPipelineCall extends ASTNode {
	
	private ASTNode leftNode;
	private ASTNode rightNode;

	public ASTPipelineCall(Token t, ASTNode leftNode, ASTNode rightNode) {
		super(t);
		this.leftNode = assignParent(leftNode);
		this.rightNode = assignParent(rightNode);
	}
	
	public ASTNode getLeftNode() {
		return leftNode;
	}
	
	public ASTNode getRightNode() {
		return rightNode;
	}
	
	@Override
	public String getNodeString() {
		return "|>";
	}

	@Override
	public int getChildCount() {
		return super.getChildCount()+2;
	}
	@Override
	public ASTNode getChild(int index) {
		switch(index) {
			case 0 ->		{ return leftNode; }
			case 1 ->		{ return rightNode; }
			default ->		{ return super.getChild(index-2); }
		}
	}
	@Override
	protected void _setChild(int index, ASTNode node) {
		switch(index) {
			case 0 ->	{ this.leftNode = node; }
			case 1 ->	{ this.rightNode  = node; }
			default ->  { super._setChild(index-2,node); }
		}
	}
	

	@Override
	public Signal evaluate(JSInterpretedRuntimeContext context, JSResult result) {
		try {
			context.getGlobalContext().checkInterrupted();

			Object arg = leftNode.evaluateValue(context,result);
			if(arg==null) {
				throw RuntimeUtil.error("Empty function reference");
			}
			
			ASTNode callNode;
			Object[] paramValues;
			if(rightNode instanceof ASTCall fc) {
				callNode = fc.getNode();
				ASTNode[] parameters = fc.getParameters();
				paramValues = new Object[parameters.length+1];
				paramValues[0] = arg;
				for(int i=0; i<parameters.length; i++) {
					paramValues[i+1] = parameters[i].evaluateValue(context,result);
				}
			} else {
				callNode = rightNode;
				paramValues = new Object[] {arg};
			}
			
			Object o = callNode.evaluateValue(context,result);
			if(o instanceof Callable callable) {
				Object r = callable.call(null, paramValues);
				result.setValue(r);
				return Signal.NONE;
			} else {
				throw RuntimeUtil.typeError("Object is not callable, {0}", o);
			}
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
		ASTNode callNode;
		ASTNode[] parameters;
		if(rightNode instanceof ASTCall fc) {
			callNode = fc.getNode();

			ASTNode[] nodeParams = fc.getParameters();
			parameters = new ASTNode[nodeParams.length+1];
			parameters[0] = leftNode;
			System.arraycopy(nodeParams, 0, parameters, 1, nodeParams.length);
		} else {
			callNode = rightNode;
			parameters = new ASTNode[] {leftNode};
		}

    	StringBuilder b = new StringBuilder(128);
    	b.append("invokeFunction(");
    	b.append(JSTranspiler.MAIN_CONTEXT);
    	b.append(",");
    	b.append(JSTranspiler.asValue(jsContext, callNode));

    	// Pipeline never has spread; arity == parameters.length. Use direct-arg
    	// form when within the supported ceiling; otherwise fall back to Object[].
    	// See ASTBaseCall.transpileParams for the (Object) cast rationale at arity 1.
    	boolean direct = parameters.length <= Callable.MAX_DIRECT_ARITY;
    	boolean castArity1 = direct && parameters.length==1;
    	if(parameters.length>0) {
    		b.append(direct ? "," : ", new Object[]{");
	    	for(int i=0; i<parameters.length; i++) {
	    		if(i>0) {
	    			b.append(",");
	    		}
	    		String v = JSTranspiler.asValue(jsContext, parameters[i]);
	    		if(castArity1) {
	    			b.append("(Object)(");
	    			b.append(v);
	    			b.append(")");
	    		} else {
		    		b.append(v);
	    		}
	    	}
	    	if(!direct) {
		    	b.append("}");
	    	}
    	}

    	b.append(")");
    	return b.toString();
    }
    
    @Override
	public String decompileExpression() {
    	StringBuilder b = new StringBuilder();
		b.append(leftNode.decompileExpression());
		b.append(" |> ");
		b.append(rightNode.decompileExpression());
		return b.toString();
    }
}