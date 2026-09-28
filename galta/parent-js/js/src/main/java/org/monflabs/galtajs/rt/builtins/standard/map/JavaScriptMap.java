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

import org.monflabs.galtajs.jsonfactory.CustomLinkedMap;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.builtins.primitives.symbol.Symbol;


/**
 * JavaScript Map
 */
public class JavaScriptMap extends CustomLinkedMap<Object> {

	private boolean shouldSoftDelete;
	private boolean mixedBigNumbers;

	public JavaScriptMap(boolean mixedBigNumbers) {
		this.mixedBigNumbers = mixedBigNumbers;
	}

	@Override
	public boolean isShouldSoftDelete(){
		return shouldSoftDelete;
	}
	@Override
	public void setShouldSoftDelete(boolean shouldSoftDelete) {
		this.shouldSoftDelete = shouldSoftDelete;
	}
	
    public boolean isMixedBigNumbers() {
		return mixedBigNumbers;
	}

	@Override
	protected int _hash(Object o) {
		if(o==null || o==RuntimeUtil.UNDEFINED) {
			return 0;
		}
		// Below are different entries:
		//   a.set(1,'AA');
		//   a.set(new Number(1),'BB');
		//   a.set(new Number(1.0),'CC');
		// TODO:
		// We currently have a bug - we need the property map here for the proper behavior
		//if(RuntimeUtil.isPrimitiveValue(null, o)) {
		if(RuntimeUtil.isPrimitiveType(o)) {
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
		if(o1==o2) {
			return true;
		}
		if(o1 instanceof CharSequence || o1 instanceof Boolean || o1 instanceof Symbol) {
			return o1.equals(o2);
		}
		if(o2 instanceof CharSequence || o2 instanceof Boolean || o2 instanceof Symbol) {
			return o2.equals(o1);
		}
		if(o1 instanceof Number n1 && o2 instanceof Number n2) {
			// SameValueZero: a BigInt never equals a Number (0n and 0 are two keys),
			// unless the environment mixes big numbers with numbers
			if(!mixedBigNumbers && isBig(n1)!=isBig(n2)) {
				return false;
			}
			return RuntimeUtil.eqNumber(n1,n2,true);
		}
		return false;
	}
	@Override
	protected boolean equalsValue(Object o1, Object o2) {
		if(o1==o2) {
			return true;
		}
		if(o1 instanceof CharSequence || o1 instanceof Boolean || o1 instanceof Symbol) {
			return o1.equals(o2);
		}
		if(o2 instanceof CharSequence || o2 instanceof Boolean || o2 instanceof Symbol) {
			return o2.equals(o1);
		}
		if(o1 instanceof Number n1 && o2 instanceof Number n2) {
			// SameValueZero: a BigInt never equals a Number (0n and 0 are two keys),
			// unless the environment mixes big numbers with numbers
			if(!mixedBigNumbers && isBig(n1)!=isBig(n2)) {
				return false;
			}
			return RuntimeUtil.eqNumber(n1,n2,true);
		}
		return false;
	}
}
