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
package org.monflabs.galtajs.rt.builtins.standard.typedarrays.arraybuffer;

import java.math.BigInteger;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.builtins.BaseMethod;
import org.monflabs.galtajs.rt.builtins.Constructor;
import org.monflabs.galtajs.rt.builtins.primitives.BaseStandardConstructor;
import org.monflabs.galtajs.rt.builtins.primitives.symbol.Symbol;
import org.monflabs.galtajs.rt.builtins.standard.typedarrays.ArrayBufferView;

public class ArrayBufferConstructor extends BaseStandardConstructor {

	public static final String CLASSNAME = "ArrayBuffer";

	public ArrayBufferConstructor(JSEnvironment env) {
		super(env,CLASSNAME,ArrayBufferPrototype.get(env),1);
		setOwnMethod(new Method(env,MethodId.isView,1));

		// get [Symbol.species] () { return this; } - a getter-only accessor
		// (not a static value) so subclasses correctly return themselves.
		setOwnProperty(Symbol.SPECIES, true, false, (base,key) -> base, null);
	}

	@Override
	public Class<?> getNativeClass() {
		return BigInteger.class;
	}

	@Override
	public Object constructObject(Object[] parameters, Constructor topConstructor) {
		int len = paramInt(parameters, 0, 0);
		// GetArrayBufferMaxByteLengthOption: -1 means "no [[ArrayBufferMaxByteLength]]
		// slot" (not resizable), distinct from an explicit maxByteLength of 0.
		int maxByteLength = -1;
		Object options = param(parameters, 1, null);
		Long maxByteLengthOption = null;
		if(RuntimeUtil.isObject(getEnvironment(), options)) {
			Object mbl = getEnvironment().getAccessor(options).getProperty(options, "maxByteLength", RuntimeUtil.UNDEFINED);
			if(!RuntimeUtil.isUndefined(mbl)) {
				maxByteLengthOption = RuntimeUtil.toIndex(getEnvironment(), mbl);
			}
		}
		if(len<0) {
			throw RuntimeUtil.rangeError("Invalid array buffer length {0}",len);
		}
		// AllocateArrayBuffer step 2a ("If byteLength > maxByteLength, throw a
		// RangeError") happens BEFORE step 3's OrdinaryCreateFromConstructor -
		// confirmed via options-maxbytelength-compared-before-object-creation.js.
		if(maxByteLengthOption!=null && len>maxByteLengthOption) {
			throw RuntimeUtil.rangeError("byteLength exceeds maxByteLength");
		}
		// OrdinaryCreateFromConstructor (newTarget's own "prototype", read via
		// a possibly-throwing user accessor) happens next, BEFORE
		// CreateByteDataBlock - i.e. before the too-large-to-allocate checks
		// below, which stand in for that step failing. Confirmed via
		// Reflect.construct(ArrayBuffer, [hugeLength], newTarget) with a
		// throwing "prototype" getter on newTarget
		// (data-allocation-after-object-creation.js).
		Object proto = topConstructor!=null && topConstructor!=this
				? RuntimeUtil.getPrototypeFromConstructor(getEnvironment(), topConstructor, getCreationPrototype())
				: null;
		if(len>Integer.MAX_VALUE-8) {
			throw RuntimeUtil.rangeError("Invalid array buffer length {0}",len);
		}
		if(maxByteLengthOption!=null) {
			if(maxByteLengthOption>Integer.MAX_VALUE-8) {
				throw RuntimeUtil.rangeError("Invalid array buffer maxByteLength {0}",maxByteLengthOption);
			}
			maxByteLength = maxByteLengthOption.intValue();
		}
		ArrayBuffer buf = new ArrayBuffer(len, maxByteLength);
		if(proto!=null) {
			RuntimeUtil.setPrototype(getEnvironment(), buf, proto);
		}
		return buf;
	}
	
	@Override
	public Object call(Object _this, Object[] parameters) {
		throw RuntimeUtil.typeError("ByteBuffer() cannot be used as a function");
	}
	
	private static enum MethodId {
		isView(),
		;
		Object id;
		MethodId() {
			this.id = name();
		}
		MethodId(Symbol id) {
			this.id = id;
		}
	}
	private static final class Method extends BaseMethod {
		private MethodId methodId;
		
		private Method(JSEnvironment env, MethodId methodId, int length) {
			super(env,methodId.id,length);
			this.methodId = methodId;
		}
		
	    @Override
		public Object call(final Object obj, final Object[] args) {
	        switch(methodId) {
	        
	        	case isView -> {
	                Object o = param(args,0,RuntimeUtil.UNDEFINED);
	    		    return o instanceof ArrayBufferView;
	        	}

	        	default -> {
	    		    throw new IllegalStateException(); // Should never be here 
	            }
	        }
	    }
	}	
}
