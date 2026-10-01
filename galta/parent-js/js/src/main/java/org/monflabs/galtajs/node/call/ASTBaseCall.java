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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Function;

import org.monflabs.galtajs.node.ASTNode;
import org.monflabs.galtajs.parser.Token;
import org.monflabs.galtajs.rt.JSResult;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.builtins.Callable;
import org.monflabs.galtajs.rt.interpreter.JSInterpretedRuntimeContext;
import org.monflabs.galtajs.transpiler.JSTranspiler;
import org.monflabs.galtajs.transpiler.context.JSTranspilerGeneratorContext;
import org.monflabs.galtajs.types.JSType;
import org.monflabs.util.StringFormat;


/**
 * Base function call.
 */
public abstract class ASTBaseCall extends ASTNode {
	
	private ASTNode[] parameters;
	private boolean hasSpread;

	public ASTBaseCall(Token t, List<ASTNode> parameters) {
		super(t);
		this.parameters = assignParent(parameters);
	}
	
	public ASTNode[] getParameters() {
		return parameters;
	}
	
	public boolean hasSpread() {
		return hasSpread;
	}

	@Override
	public int getChildCount() {
		return super.getChildCount() + parameters.length;
	}
	@Override
	public ASTNode getChild(int index) {
		if(index<parameters.length) {
			return parameters[index];
		}
		return super.getChild(index-parameters.length);
	}
	@Override
	protected void _setChild(int index, ASTNode node) {
		if(index<parameters.length) {
			parameters[index] = node;
			return;
		}
		super._setChild(index-parameters.length,node);
	}

    @Override
	public JSType getReturnedType() {
    	return JSType.UNKNOWN;
	}
	
	@Override
	protected void init(InitContext initContext) {
		this.hasSpread = false;
		if(parameters!=null) {
			for(ASTNode p: parameters) {
				if(p instanceof ASTCallSpread) {
					hasSpread = true;
					break;
				}
			}
		}
		
		super.init(initContext);
	}
	
	// Arguments are evaluated regardless of whether the callee turns out to be
	// callable - per spec, that check happens only after ArgumentListEvaluation.
	// Used to evaluate (for side effects) the arguments before throwing a
	// "not callable"/"not a function" TypeError.
	protected void evaluateParams(JSInterpretedRuntimeContext context, JSResult paramResult) {
		getParamValues(context, null, paramResult);
	}

	// In tail position (TailPosition): a JavaScript function is not called
	// here but returned as a TailCall, performed by the function that
	// returns it once its own frame is gone
	private boolean tailCall;

	protected void setTailCall(boolean tailCall) {
		this.tailCall = tailCall;
	}

	public boolean isTailCall() {
		return tailCall;
	}

	protected Object call(JSInterpretedRuntimeContext context, Object function, Object _this, JSResult paramResult) {
		// Should the parameters be calculated once for each call, or just one time for all calls?
		// Feels safer to evaluate them per call in case of side effects
		Object[] paramValues = getParamValues(context, function, paramResult);
		if(tailCall) {
			org.monflabs.galtajs.rt.TailCall tc = org.monflabs.galtajs.rt.TailCall.create(function, _this, paramValues);
			if(tc!=null) {
				return tc;
			}
		}
		ASTNode oldCaller = context.setCallerNode(this);
		try {
			return RuntimeUtil.call(context.getEnvironment(), function, _this, paramValues);
		} finally {
			context.setCallerNode(oldCaller);
		}
	}
	protected Object constructObject(JSInterpretedRuntimeContext context, Object value, JSResult paramResult) {
		// Should the parameters be calculated once for each call, or just one time for all calls?
		// Feels safer to evaluate them per call in case of side effects
		Object[] paramValues = getParamValues(context, value, paramResult);
		ASTNode oldCaller = context.setCallerNode(this);
		try {
			return RuntimeUtil.constructObject(context.getEnvironment(), value, paramValues);
		} finally {
			context.setCallerNode(oldCaller);
		}
	}
	
	
	private Object[] getParamValues(JSInterpretedRuntimeContext context, Object value, JSResult paramResult) {
		int pl = parameters.length; 
		if(pl>0) {
			if(hasSpread) {
				List<Object> plist = new ArrayList<>(pl+8);
				for(int i=0; i<pl; i++) {
					ASTNode n = parameters[i];
					if(n instanceof ASTCallSpread cs) {
						Object v = cs.evaluateValue(context,paramResult);
						// Spreading null/undefined is a TypeError (valueIterator throws it)
						{
							Iterator<Object> it=RuntimeUtil.valueIterator(context.getEnvironment(), v);
							if(it!=null) {
								while(it.hasNext()) {
									plist.add(it.next());
								}
							} else {
								throw RuntimeUtil.typeError("Spread syntax requires an iteratable");
							}
						}
					} else {
						plist.add(parameters[i].evaluateValue(context,paramResult));
					}
				}
				return plist.toArray();
			} else {
				Object[] paramValues = new Object[pl];
				for(int i=0; i<pl; i++) {
					paramValues[i] = parameters[i].evaluateValue(context,paramResult);
				}
				return paramValues;
			}
		}
		return RuntimeUtil.EMPTY_PARAMS;
	}
	
    public static String transpileParams(JSTranspilerGeneratorContext jsContext, ASTNode[] parameters, boolean hasSpread, String extraParam) {
    	return transpileParams(jsContext, parameters, hasSpread, extraParam, false);
    }

    // When allowDirect is true, non-spread calls with <= Callable.MAX_DIRECT_ARITY
    // args (counting extraParam) emit comma-separated arguments instead of
    // `new Object[]{...}`. The caller must dispatch to an arity-N-aware invoke
    // helper (invokeFunction / invokeMethod / invokeMethodWithThis with String
    // receiver — Object-index methods and paramBuilder-based sequence paths
    // stay on the Object[] form).
    public static String transpileParams(JSTranspilerGeneratorContext jsContext, ASTNode[] parameters, boolean hasSpread, String extraParam, boolean allowDirect) {
    	if(parameters.length>0 || extraParam!=null) {
    		StringBuilder b = new StringBuilder();
    		if(hasSpread) {
	    		b.append("paramBuilder(");
	    		b.append(JSTranspiler.MAIN_CONTEXT);
	    		b.append(",");
	    		b.append(parameters.length+8);
	    		b.append(")");
		    	for(int i=0; i<parameters.length; i++) {
		    		b.append( (parameters[i] instanceof ASTCallSpread) ? ".spread(": ".value(");
		    		String v = JSTranspiler.asValue(jsContext,parameters[i]);
		    		b.append(v);
		    		b.append(")");
		    	}
		    	if(extraParam!=null) {
	    			b.append(".value(");
			    	b.append(extraParam);
		    		b.append(")");
		    	}
		    	b.append(".toArray()");
    		} else {
    			int total = parameters.length + (extraParam!=null ? 1 : 0);
    			boolean direct = allowDirect && total <= Callable.MAX_DIRECT_ARITY;
    			// At arity 1, both invokeFunction(...,Object) and invokeFunction(...,Object[])
    			// are applicable candidates when the single arg is a literal `null`
    			// (Object[] is a subtype of Object, so Java resolves the varargs-shaped
    			// Object[] overload as more specific). Cast the arg to (Object) to force
    			// binding to the arity-1 overload. Only relevant when direct && total==1.
    			boolean castArity1 = direct && total==1;
    			if(!direct) {
    				b.append("new Object[]{");
    			}
		    	for(int i=0; i<parameters.length; i++) {
		    		if(i>0) {
		    			b.append(",");
		    		}
		    		String v = JSTranspiler.asValue(jsContext,parameters[i]);
		    		if(castArity1) {
		    			b.append("(Object)(");
		    			b.append(v);
		    			b.append(")");
		    		} else {
			    		b.append(v);
		    		}
		    	}
		    	if(extraParam!=null) {
		    		if(parameters.length>0) {
		    			b.append(",");
		    		}
		    		if(castArity1) {
		    			b.append("(Object)(");
		    			b.append(extraParam);
		    			b.append(")");
		    		} else {
			    		b.append(extraParam);
		    		}
		    	}
    			if(!direct) {
    				b.append("}");
    			}
    		}
    		return b.toString();
    	}
    	return "";
    }

	// Annex B.1.2 sec-runtime-errors-for-function-call-assignment-targets:
	// a CallExpression is syntactically a valid (non-strict) assignment
	// target ("web-compat"), but its evaluation result is a Value, not a
	// Reference, so any actual assignment attempt is a runtime
	// ReferenceError. The call itself must still run (for its side
	// effects) before the error is thrown, and the RHS/assigner/old-value
	// callbacks must never run at all - test262
	// annexB/language/expressions/assignmenttargettype/*.js assert the
	// callee runs exactly once, its return value's valueOf() is never
	// invoked, and (for `=`/`+=`) the right-hand side is never evaluated.
	@Override
	public void evaluateAssign(JSInterpretedRuntimeContext context, Object rightValue, Function<Object, Object> assigner, JSResult result, Function<Object, Object> returnOriginalValue) {
		evaluateValue(context, result);
		throw RuntimeUtil.referenceError("Invalid left-hand side in assignment");
	}

	// Transpiled mirror of evaluateAssign() above - see its own doc. The
	// right-hand side/assigner (rightValue, type, etc.) are deliberately
	// never referenced: the call's own transpiled expression is the ONLY
	// thing embedded in the returned Java expression text, so whatever Java
	// code the RHS would have produced never appears in the compiled output
	// at all, and can never run - same effect as evaluateAssign() never
	// touching `rightValue`/`assigner`. RuntimeUtil.invalidAssignmentTarget()
	// takes the call's own value as its argument purely so Java's own
	// left-to-right evaluation runs the call BEFORE the throw (same
	// "expression, not statement" pattern as
	// RuntimeUtil.throwSuperDeleteReferenceError()).
	@Override
	public String transpileJavaAssignment(JSTranspilerGeneratorContext jsContext, ASSIGN_TYPE type, String rightValue, boolean sequence, boolean returnOriginalValue) {
		return StringFormat.format("RuntimeUtil.invalidAssignmentTarget({0})", JSTranspiler.asValue(jsContext, this));
	}
}
