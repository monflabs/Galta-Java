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
package org.monflabs.galtajs.rt.builtins.standard.map;

import java.math.BigDecimal;
import java.math.BigInteger;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.jsonfactory.CustomLinkedMap;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.builtins.primitives.symbol.Symbol;


/**
 * JavaScript Map
 */
public class JavaScriptMap extends CustomLinkedMap<Object> {

	private final JSEnvironment env;
	private boolean mixedBigNumbers;

	public JavaScriptMap(JSEnvironment env, boolean mixedBigNumbers) {
		this.env = env;
		this.mixedBigNumbers = mixedBigNumbers;
	}

	public JSEnvironment getEnvironment() {
		return env;
	}

    public boolean isMixedBigNumbers() {
		return mixedBigNumbers;
	}

	// A boxed primitive (new Number(1), Object('a'), ...) is a Java Integer/
	// String/... registered in the environment's side tables: it is an object,
	// so it is an identity key, distinct from the primitive with the same value.
	// The check is a null test while nothing was ever boxed in the environment.
	private boolean isBoxed(Object o) {
		return RuntimeUtil.isBoxedPrimitive(env, o);
	}

	/**
	 * Spec: a -0 key (a primitive, not a boxed Number) is stored as +0.
	 */
	public Object canonicalKey(Object key) {
		if((key instanceof Double || key instanceof Float) && !isBoxed(key)) {
			return RuntimeUtil.canonicalizeKeyedCollectionKey(key);
		}
		return key;
	}

	@Override
	protected int _hash(Object o) {
		if(o==null || o==RuntimeUtil.UNDEFINED) {
			return 0;
		}
		if(RuntimeUtil.isPrimitiveType(o) && !isBoxed(o)) {
			// Treat all primitive numbers as equals
			if(o instanceof Number n) {
				if(mixedBigNumbers || !(n instanceof BigInteger || n instanceof BigDecimal) ) {
					double d = n.doubleValue();
					if(d==-0.0) {
						d = 0.0; // Else the Hashcode is different!
					}
					return Double.hashCode(d);
				}
			}
			return o.hashCode();
		}
		return System.identityHashCode(o);
    }
	private static boolean isBig(Number n) {
		return n instanceof BigInteger || n instanceof BigDecimal;
	}
	@Override
	protected boolean equalsKey(Object o1, Object o2) {
		return sameValueZero(o1, o2);
	}
	@Override
	protected boolean equalsValue(Object o1, Object o2) {
		return sameValueZero(o1, o2);
	}
	private boolean sameValueZero(Object o1, Object o2) {
		if(o1==o2) {
			return true;
		}
		boolean eq;
		if(o1 instanceof CharSequence || o1 instanceof Boolean || o1 instanceof Symbol) {
			eq = o1.equals(o2);
		} else if(o2 instanceof CharSequence || o2 instanceof Boolean || o2 instanceof Symbol) {
			eq = o2.equals(o1);
		} else if(o1 instanceof Number n1 && o2 instanceof Number n2) {
			// SameValueZero: a BigInt never equals a Number (0n and 0 are two keys),
			// unless the environment mixes big numbers with numbers
			if(!mixedBigNumbers && isBig(n1)!=isBig(n2)) {
				return false;
			}
			eq = RuntimeUtil.eqNumber(n1,n2,true);
		} else {
			return false;
		}
		// Two distinct objects are never equal, even when they box the same value
		return eq && !isBoxed(o1) && !isBoxed(o2);
	}
}
