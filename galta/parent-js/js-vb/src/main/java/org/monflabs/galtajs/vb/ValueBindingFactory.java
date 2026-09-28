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
package org.monflabs.galtajs.vb;

import java.util.ArrayList;
import java.util.List;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.JSException;
import org.monflabs.galtajs.modules.JSInterpretedUnit;
import org.monflabs.galtajs.rt.interpreter.InterpretedGlobalRuntimeContext;
import org.monflabs.galtajs.vb.impl.GaltaJSValueBinding;
import org.monflabs.galtajs.vb.impl.IdentityValueBinding;

public class ValueBindingFactory {
	
	public static final String DEFAULT_EL_START		= "${";
	public static final String DEFAULT_EL_END			= "}";

	public static class MultiPartScriptExpression extends ValueBinding {
		List<ValueBinding> expressions;
		private MultiPartScriptExpression(String expression, List<ValueBinding>  expressions) {
			super(expression);
			this.expressions = expressions;
		}
		@Override
		public boolean isConstant() {
			for(int i=0; i<expressions.size(); i++) {
				if(!expressions.get(i).isConstant()) {
					return false;
				}
			}
			return true;
		}
		@Override
		public Object evaluate(InterpretedGlobalRuntimeContext context) {
			StringBuilder builder = new StringBuilder();
			for(int i=0; i<expressions.size(); i++) {
				Object v = expressions.get(i).evaluate(context);
				if(v!=null) {
					String s = v.toString();
					builder.append(s);
				}
			}
			return builder.toString();
		}

	}

	private JSEnvironment env;
	private String elStart;
	private String elEnd;
	private char   elStartChar;
	private char   elEndChar;

	public ValueBindingFactory(JSEnvironment env) {
		this(env,DEFAULT_EL_START,DEFAULT_EL_END);
	}
	
	public ValueBindingFactory(JSEnvironment env,String elStart, String elEnd) {
		this.env = env;
		this.elStart = elStart;
		this.elEnd = elEnd;
		this.elStartChar = elStart.charAt(0);
		this.elEndChar = elEnd.charAt(0);
	}
	
	public String getElStart() {
		return elStart;
	}

	public String getElEnd() {
		return elEnd;
	}

	public Object evaluate(InterpretedGlobalRuntimeContext context, String expression) {
		ValueBinding expr = createValueBinding(expression);
		return expr.evaluate(context);
	}
	
	public boolean isValueBinding(String expression) {
		return expression!=null && expression.indexOf(elStart)>=0;
	}

	public ValueBinding createValueBinding(String expression) {
        ArrayList<ValueBinding> exprs = new ArrayList<ValueBinding>();
        
        int start = 0;
        int length = expression.length();
        int blockDepth = 0;
        int stringDelimiter = 0;
        boolean inExpression = false;
        
        for(int i=0; i<length; i++) {
            char ch = expression.charAt(i);
            
            // If we are not in an expression, then we check if this is the start of an expression
            if(!inExpression) {
                if(ch == elStartChar && expression.startsWith(elStart,i)) {
                	inExpression = true;                
                    if (i>start) {
                    	exprs.add(new IdentityValueBinding(expression.substring(start, i)));
                    }
                    start = i;
                    i += elStart.length()-1;
                }
                continue;
            }

            // We are already in an expression but in a string
            // We skip the character unless it closes the string
            if(stringDelimiter!=0) {
                if(ch==stringDelimiter) {
                	stringDelimiter = 0;
                }
                if(ch=='\\') {
                	i++;
                }
            	continue;
        	}
            
            // We are in an expression but not in a string 
            // We check for the beginning of a string
            if(ch=='"' || ch=='\'') {
            	stringDelimiter = ch;
            	continue;
            }
        	if(ch=='{') {
                blockDepth++;
            	continue;
        	}
        	if(ch=='}') {
        		if(blockDepth>0) {
        			blockDepth--;
        			continue;
        		}
        	}
            if(ch == elEndChar && expression.startsWith(elEnd,i)) {
                inExpression = false;
                exprs.add(createExpression(expression.substring(start+elStart.length(), i)));
                start = i+elEnd.length();
        	}
        }
        
        if(inExpression) {
        	throw new JSException(null,"Expression is missing a closing {0}", elEnd);
        }
        
        if(start<length) {
            exprs.add(new IdentityValueBinding(expression.substring(start)));
        }

        if(exprs.size()>0) {
			if(exprs.size()==1) {
				return exprs.get(0);
			}
			return new MultiPartScriptExpression(expression,exprs);
		}

		return new IdentityValueBinding(expression);
    }

	public ValueBinding createExpression(String expression) {
		JSInterpretedUnit expr = env.createExpression(expression);
		return new GaltaJSValueBinding(env,expr);
	}
}
