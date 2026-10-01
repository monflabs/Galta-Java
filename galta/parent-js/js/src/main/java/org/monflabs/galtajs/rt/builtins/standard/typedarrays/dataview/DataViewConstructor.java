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
package org.monflabs.galtajs.rt.builtins.standard.typedarrays.dataview;

import java.math.BigInteger;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.builtins.BaseMethod;
import org.monflabs.galtajs.rt.builtins.Constructor;
import org.monflabs.galtajs.rt.builtins.primitives.BaseStandardConstructor;
import org.monflabs.galtajs.rt.builtins.primitives.symbol.Symbol;
import org.monflabs.galtajs.rt.builtins.standard.typedarrays.ArrayBufferView;
import org.monflabs.galtajs.rt.builtins.standard.typedarrays.BaseArrayBuffer;

public class DataViewConstructor extends BaseStandardConstructor {

	public static final String CLASSNAME = "DataView";
	
	public DataViewConstructor(JSEnvironment env) {
		super(env,CLASSNAME,DataViewPrototype.get(env),1);
		setOwnMethod(new Method(env,MethodId.isView,1));
	}
	
	@Override
	public Class<?> getNativeClass() {
		return BigInteger.class;
	}

	@Override
	public Object constructObject(Object[] parameters, Constructor topConstructor) {
		JSEnvironment env = getEnvironment();
		Object buf = param(parameters, 0, null);
		if(!(buf instanceof BaseArrayBuffer)) {
			throw RuntimeUtil.typeError("Parameter is not an ArrayBuffer, '{0}'",buf);
		}
		BaseArrayBuffer b = (BaseArrayBuffer)buf;
		long offset = RuntimeUtil.toIndex(env, param(parameters, 1, RuntimeUtil.UNDEFINED));
		if(b.isDetached()) {
			throw RuntimeUtil.typeError("ArrayBuffer is detached");
		}
		int bufferByteLength = b.getByteLength();
		if(offset>bufferByteLength) {
			throw RuntimeUtil.rangeError("Invalid byteOffset value, '{0}'",offset);
		}
		Object rawLength = param(parameters, 2, RuntimeUtil.UNDEFINED);
		boolean lengthTracking = rawLength==RuntimeUtil.UNDEFINED;
		int viewByteLength;
		if(lengthTracking) {
			viewByteLength = bufferByteLength-(int)offset;
		} else {
			long length = RuntimeUtil.toIndex(env, rawLength);
			if(offset+length>bufferByteLength) {
				throw RuntimeUtil.rangeError("Invalid byteLength value, '{0}'",length);
			}
			viewByteLength = (int)length;
		}
		DataView view = applyNewTargetPrototype(new DataView(b,(int)offset,viewByteLength,lengthTracking), topConstructor);
		// Spec steps 10-13: applyNewTargetPrototype() above is
		// OrdinaryCreateFromConstructor, which reads newTarget's
		// "prototype" property - a user-defined accessor there can run
		// arbitrary code that detaches or (for a resizable buffer) shrinks
		// `b` out from under the offset/length computed above. The spec
		// re-validates against the buffer's CURRENT state immediately
		// afterward, so re-check here too rather than silently returning a
		// DataView backed by now-invalid bounds. See test262
		// built-ins/DataView/custom-proto-access-*.js.
		if(b.isDetached()) {
			throw RuntimeUtil.typeError("ArrayBuffer is detached");
		}
		int currentBufferByteLength = b.getByteLength();
		if(offset>currentBufferByteLength) {
			throw RuntimeUtil.rangeError("Invalid byteOffset value, '{0}'",offset);
		}
		if(!lengthTracking && offset+viewByteLength>currentBufferByteLength) {
			throw RuntimeUtil.rangeError("Invalid byteLength value, '{0}'",viewByteLength);
		}
		return view;
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
		protected Object invoke(final Object obj, final Object[] args) {
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
