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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.monflabs.galtajs.node.call.ASTBaseCall;
import org.monflabs.galtajs.node.call.ASTCall;
import org.monflabs.galtajs.node.call.ASTCallSpread;
import org.monflabs.galtajs.parser.Token;
import org.monflabs.galtajs.rt.JSResult;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.interpreter.InterpretedUnitRuntimeContext.Signal;
import org.monflabs.galtajs.rt.interpreter.JSInterpretedRuntimeContext;
import org.monflabs.galtajs.transpiler.JSTranspiler;
import org.monflabs.galtajs.transpiler.context.JSTranspilerGeneratorContext;
import org.monflabs.galtajs.types.JSType;
import org.monflabs.galtajs.util.JavaBuilder;


/**
 * Super Constructor Node.
 */
public class ASTSuperCtor extends ASTBaseCall {
	
	public ASTSuperCtor(Token t, List<ASTNode> parameters) {
		super(t,parameters);
	}	

    @Override
	public JSType getReturnedType() {
    	return JSType.UNKNOWN;
	}

	@Override
	public Signal evaluate(JSInterpretedRuntimeContext context, JSResult paramResult) {
		try {
			// Can this be shared with ASTCall?
	    	ASTNode[] parameters = getParameters();
			Object[] paramValues = RuntimeUtil.EMPTY_PARAMS;
			int pl = parameters.length; 
			if(pl>0) {
				if(hasSpread()) {
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
					paramValues = plist.toArray();
				} else {
					paramValues = new Object[pl];
					for(int i=0; i<pl; i++) {
						paramValues[i] = parameters[i].evaluateValue(context,paramResult);
					}
				}
			}
			
			Object _this = RuntimeUtil.superCtor(context, paramValues);
			paramResult.setValue(_this);
		return Signal.NONE;
		} catch(Throwable ex) {
			throw fillInStackTrace(ex);
		}
	}
	
    @Override
	public String transpileJavaExpression(JSTranspilerGeneratorContext jsContext) {
    	JavaBuilder b = new JavaBuilder();
    	// NOT `_this = superCtor(...)`: superCtor() already binds the new `this`
    	// onto the constructor's own JSFunctionContext (setThis()), which is the
    	// single source every `this`/`super` read in a derived constructor now
    	// goes through (see JSTranspiler.thisRef()). Assigning the local `_this`
    	// parameter too would reassign it - making it non-effectively-final and
    	// breaking any lambda in the same method that captures it (e.g. the
    	// executeIsolated/resolveMethodTarget closures a `this.method()` call
    	// emits) - while adding nothing, since nothing reads that local anymore.
    	b.append("superCtor({0},",JSTranspiler.MAIN_CONTEXT);
    	
    	ASTNode[] parameters = getParameters();
    	if(parameters.length>0) {
        	b.append(ASTCall.transpileParams(jsContext, getParameters(), hasSpread(), null));
    	} else {
	    	b.append("EMPTY_PARAMS");
    	}
    	
    	b.append(")");
    	return b.toString();
    }
    
    @Override
	public String decompileExpression() {
    	ASTNode[] parameters = getParameters();
    	StringBuilder b = new StringBuilder();
    	b.append("super(");
    	for(int i=0; i<parameters.length; i++) {
    		if(i>0) {
    	    	b.append(", ");
    		}
        	b.append(parameters[i].decompileExpression());
    	}
    	b.append(")");
    	return b.toString();
    }
}