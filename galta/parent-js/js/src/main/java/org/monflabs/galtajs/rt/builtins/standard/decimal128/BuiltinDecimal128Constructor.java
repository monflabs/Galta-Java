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
package org.monflabs.galtajs.rt.builtins.standard.decimal128;

import java.math.BigInteger;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.builtins.Constructor;
import org.monflabs.galtajs.rt.builtins.primitives.BaseStandardConstructor;
import org.monflabs.util.number.Decimal128;

/**
 * The Decimal128 function, as in the Decimal proposal: converts a BigInt, a Number or a String to a
 * {@link Decimal128} primitive.
 */
public class BuiltinDecimal128Constructor extends BaseStandardConstructor {

	public static final String CLASSNAME = "Decimal128";
	
	public BuiltinDecimal128Constructor(JSEnvironment env) {
		super(env,CLASSNAME,BuiltinDecimal128Prototype.get(env),1);
	}
	
	@Override
	public Class<?> getNativeClass() {
		return Decimal128.class;
	}

	@Override
	public Object constructObject(Object[] parameters, Constructor topConstructor) {
		throw RuntimeUtil.typeError("Decimal128 is not a constructor");
	}
	
	@Override
	public Object call(Object _this, Object[] parameters) {
		Object v = RuntimeUtil.toPrimitive(getEnvironment(),param(parameters, 0, RuntimeUtil.UNDEFINED),RuntimeUtil.HINT.NUMBER);
		return convert(v);
	}
	
	/** Converts a primitive to a Decimal128 as the constructor does. */
	static Decimal128 convert(Object v) {
		if(v instanceof Decimal128 d) {
			return d;
		}
		if(v instanceof BigInteger bi) {
			return Decimal128.of(bi);
		}
		if(v instanceof Number n) {
			return org.monflabs.util.TypeUtil.toDecimal128(n);
		}
		if(v instanceof CharSequence s) {
			try {
				return Decimal128.of(s.toString());
			} catch(NumberFormatException e) {
				throw RuntimeUtil.syntaxError("Cannot convert {0} to a Decimal128",s);
			}
		}
		throw RuntimeUtil.typeError("Cannot convert {0} to a Decimal128",v);
	}
}
