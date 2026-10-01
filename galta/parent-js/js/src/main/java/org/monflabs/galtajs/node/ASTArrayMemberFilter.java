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
import org.monflabs.galtajs.rt.MemberAccessor;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.interpreter.InterpretedUnitRuntimeContext.Signal;
import org.monflabs.galtajs.rt.interpreter.JSInterpretedRuntimeContext;
import org.monflabs.galtajs.transpiler.JSTranspiler;
import org.monflabs.galtajs.transpiler.context.JSTranspilerGeneratorContext;
import org.monflabs.galtajs.types.JSType;
import org.monflabs.util.StringFormat;


/**
 * Filter Array elements.
 */
public class ASTArrayMemberFilter extends ASTNode {

	private ASTNode node;
	private ASTNode filter;

	public ASTArrayMemberFilter(Token t, ASTNode node, ASTNode filter) {
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
		return "[?(...)]";
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
		return true;
	}	
	
	public void forEachEntries(JSInterpretedRuntimeContext context, JSResult sequence, MemberAccessor accessor, boolean forUpdate) {
		JSResult predicateResult = new JSResult();
		Function<Object,Object> cond = new Function<>() {
			@Override
			public Object apply(Object fo) {
				return context.executeWithFilterContext(fo, filter, predicateResult);
			}
		};
		RuntimeUtil._arrayFilter(context, sequence, cond, accessor, forUpdate);
	}

	@Override
	public Signal evaluate(JSInterpretedRuntimeContext context, JSResult result) {
		try {
			node.evaluate(context,result);
			
			// Propagate null chaining...
			if(result.isChainingNull()) {
				return Signal.NONE;
			}

			JSResult res = result.ejectAndSequence();
			forEachEntries( context, res, (base,index,getter,setter,remover) -> {
        		result.addToSequence(context.getEnvironment(),getter.get());
			},false);					
			
			return Signal.NONE;
		} catch(Throwable ex) {
			throw fillInStackTrace(ex);
		}		
	}
	
	@Override
	public Signal evaluateTypeof(JSInterpretedRuntimeContext context, JSResult result) {
		try {
			node.evaluate(context,result);

			JSResult leftValue = result.ejectAndSequence();
			forEachEntries(context, leftValue, (base,index,getter,setter,remover) -> {
				Object v = getter.get();
				result.addToSequence(context.getEnvironment(),RuntimeUtil.typeof(context.getEnvironment(),v));
			},false);
			
			return Signal.NONE;
		} catch(Throwable ex) {
			throw fillInStackTrace(ex);
		}
	}
	
	// Not needed, we evaluate the result 
	//public Object evaluateTypeof(InterpretedContext context, JSResult result) {
	
	@Override
	public void evaluateAssign(JSInterpretedRuntimeContext context, Object _rightValue, Function<Object, Object> assigner, JSResult result, Function<Object, Object> returnOriginalValue) {
		try {
			node.evaluate(context,result);
			
			// Propagate null chaining...
			if(result.isChainingNull()) {
				return;
			}
			
			JSResult res = result.ejectAndSequence();
			forEachEntries( context, res, (base,index,getter,setter,remover) -> {
				if(base==null) {
					//throw new JSRuntimeException(null, "Left part of member is null, {0}", node.getNodeString());
				} else {
					Object rightValue = _rightValue;
					if(assigner!=null) {
						Object oldValue = getter.get();
						if(oldValue==RuntimeUtil.NOT_AVAILABLE) {
							oldValue = RuntimeUtil.UNDEFINED;
						}
						rightValue = assigner.apply(oldValue);
						setter.accept(rightValue);
						result.addToSequence(context.getEnvironment(),returnOriginalValue!=null ? returnOriginalValue.apply(oldValue)  : rightValue);
					} else {
						setter.accept(rightValue);
						result.addToSequence(context.getEnvironment(),rightValue);
					}
				}
			},true);
		} catch(Throwable ex) {
			throw fillInStackTrace(ex);
		}		
	}
	
	@Override
	public boolean evaluateDelete(JSInterpretedRuntimeContext context, JSResult result) {
		try {
			node.evaluate(context,result);
			
			// Propagate null chaining...
			if(result.isChainingNull()) {
				return true;
			}
			
			JSResult res = result.ejectAndSequence();
			result.setValue(false);
			forEachEntries( context, res, (base,index,getter,setter,remover) -> {
				boolean v = remover.getAsBoolean();
				if(v) {
					result.setValue(true);
				}
			},true);
			return (Boolean)result.getValue();
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
    	return StringFormat.format("seqArrayFilter({0}, {1}, ({2}{3}) -> {4})", 
    	  				JSTranspiler.MAIN_CONTEXT,
    	   				JSTranspiler.asResult(jsContext,node),
    	   				JSTranspiler.CURRENT_VALUE, valueId,
    	   				JSTranspiler.asValue(jsContext, valueId, filter)
    	   		);
    }

    @Override
	public String transpileJavaAssignment(JSTranspilerGeneratorContext jsContext, ASSIGN_TYPE type, String rightValue, boolean sequence, boolean returnOriginalValue) {
    	// Sequence or not, this should be the same 
    	int valueId = jsContext.generateUniqueId();
    	return StringFormat.format("seqArrayFilterAssign({0},{1},({2}{3}) -> {4}, {5}, RuntimeUtil::{6})", 
    	  				JSTranspiler.MAIN_CONTEXT,
    	  				JSTranspiler.asResult(jsContext, node), 
    	   				JSTranspiler.CURRENT_VALUE, valueId,
    	   				JSTranspiler.asValue(jsContext, valueId, filter),
    	   				rightValue,
    	   				type.assignmentRuntimeFunction()
    	   		);
    }

    
    @Override
	public String decompileExpression() {
    	StringBuilder b= new StringBuilder();
    	b.append(node.decompileExpression());
    	b.append("[?(");
    	b.append(filter.decompileExpression());
    	b.append(")]");
    	return b.toString();
	}    
}
