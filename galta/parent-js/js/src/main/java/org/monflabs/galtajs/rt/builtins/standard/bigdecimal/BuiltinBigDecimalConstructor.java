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
package org.monflabs.galtajs.rt.builtins.standard.bigdecimal;

import java.math.BigDecimal;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.builtins.Constructor;
import org.monflabs.galtajs.rt.builtins.primitives.BaseStandardConstructor;

/**
 * 
 */
public class BuiltinBigDecimalConstructor extends BaseStandardConstructor {

	public static final String CLASSNAME = "Decimal";
	
	public BuiltinBigDecimalConstructor(JSEnvironment env) {
		super(env,CLASSNAME,BuiltinBigDecimalPrototype.get(env),1);
	}
	
	@Override
	public Class<?> getNativeClass() {
		return BigDecimal.class;
	}

	@Override
	public Object constructObject(Object[] parameters, Constructor topConstructor) {
		throw RuntimeUtil.typeError("Decimal is not a constructor");
	}
	
	@Override
	public Object call(Object _this, Object[] parameters) {
		Object v = param(parameters, 0, RuntimeUtil.UNDEFINED);
		// NaN and +-Infinity are Decimal values too, represented by the matching Number. As in the proposal, a
		// Number goes through its string form, so -0 is +0 (the string "-0" gives -0)
		if(v instanceof Number n ) {
			if((n instanceof Double || n instanceof Float) && n.doubleValue()==0) {
				return BigDecimal.ZERO;
			}
			return DecimalArithmetic.toDecimal(n, getEnvironment().getMathContext());
		}
		if(v instanceof CharSequence s) {
			try {
				return DecimalArithmetic.parse(s.toString(), getEnvironment().getMathContext());
			} catch(NumberFormatException ex) {
				throw RuntimeUtil.syntaxError("Cannot convert {0} to a Decimal", s);
			}
		}
		throw RuntimeUtil.typeError("Cannot convert {0} to a Decimal",RuntimeUtil.objectTypeName(getEnvironment(),v));
	}	
}
