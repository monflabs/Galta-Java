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
 * All member nodes.
 * Only apply to JsonArray.
 */
public class ASTArrayMemberFlatten extends ASTNode {

	private ASTNode node;
	private boolean deepscan;

	public ASTArrayMemberFlatten(Token t, ASTNode node, boolean deepscan) {
		super(t);
		this.node = assignParent(node);
		this.deepscan = deepscan;
	}


	@Override
	public STATEMENT_TYPE getStatementType() {
		return STATEMENT_TYPE.EXPRESSION;
	}	

	public ASTNode getNode() {
		return node;
	}

	public boolean isDeepscan() {
		return deepscan;
	}

	@Override
	public String getNodeString() {
		return deepscan? "..[*]" : ".[*]";
	}

	@Override
	public int getChildCount() {
		return super.getChildCount()+1;
	}
	@Override
	public ASTNode getChild(int index) {
		switch(index) {
			case 0 ->	{ return node; }
			default ->	{ return super.getChild(index-1); }
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
	public boolean isSequence() {
		return true;
	}	

	@Override
	public Signal evaluate(JSInterpretedRuntimeContext context, JSResult result) {
		try {
			node.evaluate(context,result);
			
			// Propagate null chaining...
			if(result.isChainingNull()) {
				return Signal.NONE;
			}
			
			RuntimeUtil.arrayFlatten(context.getEnvironment(),result,deepscan);

			return Signal.NONE;
		} catch(Throwable ex) {
			throw fillInStackTrace(ex);
		}		
	}

	@Override
	public Signal evaluateTypeof(JSInterpretedRuntimeContext context, JSResult result) {
		try {
			node.evaluate(context,result);
			
			// Propagate null chaining...
			if(result.isChainingNull()) {
				return Signal.NONE;
			}
			
			RuntimeUtil.arrayFlatten(context.getEnvironment(),result,deepscan);
			
			result.map( context.getEnvironment(), (v) -> {
				return RuntimeUtil.typeof(context.getEnvironment(),v);
			});

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
    	return StringFormat.format("arrayFlatten({0},{1})",JSTranspiler.asResult(jsContext,getNode()), deepscan);
    }
    
    @Override
    public String transpileTypeofExpression(JSTranspilerGeneratorContext jsContext) {
    	return StringFormat.format("seq(RuntimeUtil::typeof,{0})",JSTranspiler.asResult(jsContext,this));
    }
    
    @Override
	public String decompileExpression() {
    	StringBuilder b= new StringBuilder();
    	b.append(node.decompileExpression());
    	b.append(deepscan? "..[*]" : "[*]");
    	return b.toString();
    }
}
