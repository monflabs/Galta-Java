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
package org.monflabs.galtajs.vb.impl;


import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.JSException;
import org.monflabs.galtajs.modules.JSInterpretedUnit;
import org.monflabs.galtajs.rt.interpreter.InterpretedGlobalRuntimeContext;
import org.monflabs.galtajs.vb.ValueBinding;

/**
 * GaltaJS ValueBinding.
 * @author priand
 *
 */
public class GaltaJSValueBinding extends ValueBinding {
	
	private JSEnvironment env;
	private JSInterpretedUnit expr;
	
	public GaltaJSValueBinding(JSEnvironment env, String expr) {
		this(env,env.createExpression(expr)); // same as ValueBindingFactory.createExpression()
	}
	public GaltaJSValueBinding(JSEnvironment env, JSInterpretedUnit expr) {
		super(expr.getText());
		this.env = env;
		this.expr = expr;
	}
	
	public JSEnvironment getEnvironment() {
		return env;
	}
	
	@Override
	public boolean isConstant() {
		return expr.isConstant();
	}
	
	@Override
	public Object evaluate(InterpretedGlobalRuntimeContext ctx) {
		try {
			Object res = expr.executeWithContext(ctx);
			return res;
		} catch(Exception e) {
			throw new JSException(e, "Error while evaluating expression");
		}
	}
}
