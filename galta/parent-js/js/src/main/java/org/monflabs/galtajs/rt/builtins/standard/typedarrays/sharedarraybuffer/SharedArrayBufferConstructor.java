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
package org.monflabs.galtajs.rt.builtins.standard.typedarrays.sharedarraybuffer;

import java.math.BigInteger;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.builtins.BaseMethod;
import org.monflabs.galtajs.rt.builtins.Constructor;
import org.monflabs.galtajs.rt.builtins.primitives.BaseStandardConstructor;
import org.monflabs.galtajs.rt.builtins.primitives.symbol.Symbol;
import org.monflabs.galtajs.rt.builtins.standard.typedarrays.ArrayBufferView;

public class SharedArrayBufferConstructor extends BaseStandardConstructor {

	public static final String CLASSNAME = "SharedArrayBuffer";
	
	public SharedArrayBufferConstructor(JSEnvironment env) {
		super(env,CLASSNAME,SharedArrayBufferPrototype.get(env),1);
		setOwnMethod(new Method(env,MethodId.isView,1));
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
		// See ArrayBufferConstructor's identical ordering: AllocateArrayBuffer
		// step 2a ("byteLength > maxByteLength" -> RangeError) happens BEFORE
		// OrdinaryCreateFromConstructor (newTarget's own "prototype"), which
		// in turn happens before the too-large-to-allocate checks below.
		if(maxByteLengthOption!=null && len>maxByteLengthOption) {
			throw RuntimeUtil.rangeError("byteLength exceeds maxByteLength");
		}
		Object proto = topConstructor!=null && topConstructor!=this
				? RuntimeUtil.getPrototypeFromConstructor(getEnvironment(), topConstructor, getCreationPrototype())
				: null;
		if(len>100_000_000) {
			throw RuntimeUtil.rangeError("Invalid array buffer length {0}",len);
		}
		if(maxByteLengthOption!=null) {
			if(maxByteLengthOption>100_000_000) {
				throw RuntimeUtil.rangeError("Invalid array buffer maxByteLength {0}",maxByteLengthOption);
			}
			maxByteLength = maxByteLengthOption.intValue();
		}
		SharedArrayBuffer buf = new SharedArrayBuffer(len, maxByteLength);
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
	                Object o = param(args,0);
	    		    return o instanceof ArrayBufferView; 
	        	}

	        	default -> {
	    		    throw new IllegalStateException(); // Should never be here 
	            }
	        }
	    }
	}	
}
