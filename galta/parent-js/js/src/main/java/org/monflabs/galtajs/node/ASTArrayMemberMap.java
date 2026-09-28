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
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.builtins.Callable;
import org.monflabs.galtajs.rt.interpreter.InterpretedUnitRuntimeContext.Signal;
import org.monflabs.galtajs.rt.interpreter.JSInterpretedRuntimeContext;
import org.monflabs.galtajs.transpiler.JSTranspiler;
import org.monflabs.galtajs.transpiler.context.JSTranspilerGeneratorContext;
import org.monflabs.galtajs.types.JSType;
import org.monflabs.util.StringFormat;


/**
 * Map Array elements.
 *   .(...)
 */
public class ASTArrayMemberMap extends ASTNode {

	private ASTNode node;
	private ASTNode filter;

	public ASTArrayMemberMap(Token t, ASTNode node, ASTNode filter) {
		super(t);
		this.node = assignParent(node);
		this.filter = assignParent(filter);
	}

	@Override
	public STATEMENT_TYPE getStatementType() {
		return STATEMENT_TYPE.EXPRESSION;
	}	

	public ASTNode getNode() {
		return node;
	}
	
	public ASTNode getFilter() {
		return filter;
	}
	
	@Override
	public String getNodeString() {
		return ".(...)";
	}

	@Override
	public int getChildCount() {
		return super.getChildCount()+2;
	}
	@Override
	public ASTNode getChild(int index) {
		switch(index) {
			case 0 ->	{ return node; }
			case 1 ->	{ return filter; }
			default ->	{ return super.getChild(index-2); }
		}
	}
	@Override
	protected void _setChild(int index, ASTNode node) {
		switch(index) {
			case 0 ->	{ this.node = node; }
			case 1 ->	{ this.filter  = node; }
			default ->  { super._setChild(index-2,node); }
		}
	}

	@Override
	public boolean isSequence() {
		return node.isSequence();
	}	

	@Override
	public Signal evaluate(JSInterpretedRuntimeContext context, JSResult result) {
		try {
			node.evaluate(context,result);
			
			// Propagate null chaining...
			if(result.isChainingNull()) {
				return Signal.NONE;
			}
			
			Function<Object,Object> function = new Function<Object,Object>() {
				JSResult functionResult = new JSResult();
				@Override
				public Object apply(Object fo) {
					return context.executeWithFilterContext(fo, () -> {
						Object result = filter.evaluateValue(context,functionResult);
						if(result instanceof Callable callable) {
							result = callable.call(fo, RuntimeUtil.EMPTY_PARAMS);
						}
						return result;
					});				
				}
			};
			
			result.map( context.getEnvironment(), (val) -> {
	        	return function.apply(val);
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
    	int valueId = jsContext.generateUniqueId();
    	return StringFormat.format("arrayMap({0}, {1}, ({2}{3}) -> {4})",
    	  				JSTranspiler.MAIN_CONTEXT,
    	  				JSTranspiler.asResult(jsContext,node),
    	  				JSTranspiler.CURRENT_VALUE, valueId,
    	   				JSTranspiler.asValue(jsContext, valueId, filter)
    			);
    }

    
    @Override
	public String decompileExpression() {
    	StringBuilder b= new StringBuilder();
    	b.append(node.decompileExpression());
    	b.append(".(");
    	b.append(filter.decompileExpression());
    	b.append(")");
    	return b.toString();
	}    
}
