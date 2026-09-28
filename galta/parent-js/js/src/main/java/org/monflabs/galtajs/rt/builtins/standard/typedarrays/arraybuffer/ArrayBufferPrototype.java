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

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.builtins.BaseMethod;
import org.monflabs.galtajs.rt.builtins.BasePrototype;
import org.monflabs.galtajs.rt.builtins.Constructor;
import org.monflabs.galtajs.rt.builtins.PropertyDescriptor;
import org.monflabs.galtajs.rt.builtins.primitives.symbol.Symbol;

public class ArrayBufferPrototype extends BasePrototype {

	public static ArrayBufferPrototype get(JSEnvironment env) {
		ArrayBufferPrototype proto = (ArrayBufferPrototype)env.getRegisteredPrototype(ArrayBufferPrototype.class);
		if(proto==null) {
			proto = new ArrayBufferPrototype(env);
			env.registerPrototype(ArrayBufferPrototype.class,proto);
		}
		return proto;
	}
	
	private ArrayBufferPrototype(JSEnvironment env) {
		super(env);
		setOwnProperty(Symbol.TO_STRING_TAG,ArrayBufferConstructor.CLASSNAME,PropertyDescriptor.DESC_PROP_TOSTRINGTAG);

		setOwnProperty("byteLength",true,false, 
				(t,k) -> {
					if(t instanceof ArrayBuffer ab) {
						return ab.getByteLength();
					}
			    	throw RuntimeUtil.typeError("Property ArrayBuffer.byteLength called on incompatible receiver {0}", t!=null?t.getClass():"null");
				}, 
				null
			);
		setOwnProperty("detached",true,false, 
				(t,k) -> {
					if(t instanceof ArrayBuffer ab) {
						return ab.isDetached();
					}
			    	throw RuntimeUtil.typeError("Property ArrayBuffer.detached called on incompatible receiver {0}", t!=null?t.getClass():"null");
				}, 
				null
			);
		setOwnProperty("maxByteLength",true,false,
				(t,k) -> {
					if(t instanceof ArrayBuffer ab) {
						return ab.isResizable() ? ab.getMaxByteLength() : ab.getByteLength();
					}
			    	throw RuntimeUtil.typeError("Property ArrayBuffer.maxByteLength called on incompatible receiver {0}", t!=null?t.getClass():"null");
				},
				null
			);
		setOwnProperty("resizable",true,false,
				(t,k) -> {
					if(t instanceof ArrayBuffer ab) {
						return ab.isResizable();
					}
			    	throw RuntimeUtil.typeError("Property ArrayBuffer.resizable called on incompatible receiver {0}", t!=null?t.getClass():"null");
				},
				null
			);
		setOwnProperty("immutable",true,false,
				(t,k) -> {
					if(t instanceof ArrayBuffer ab) {
						return ab.isImmutable();
					}
			    	throw RuntimeUtil.typeError("Property ArrayBuffer.immutable called on incompatible receiver {0}", t!=null?t.getClass():"null");
				},
				null
			);


		setOwnMethod(new Method(env,MethodId.resize,1));
		setOwnMethod(new Method(env,MethodId.slice,2));
		setOwnMethod(new Method(env,MethodId.sliceToImmutable,2));
		setOwnMethod(new Method(env,MethodId.transfer,0));
		setOwnMethod(new Method(env,MethodId.transferToFixedLength,0));
		setOwnMethod(new Method(env,MethodId.transferToImmutable,0));
	}

	@Override
	public String getClassName() {
		return ArrayBufferConstructor.CLASSNAME;
	}
	
	private static enum MethodId {
		resize,
		slice,
		sliceToImmutable,
		transfer,
		transferToFixedLength,
		transferToImmutable,
		;
		Object id;
		MethodId() {
			this.id = name();
		}
		MethodId(Symbol id) {
			this.id = id;
		}
	}
	
	
	private final static class Method extends BaseMethod {
		private MethodId methodId;
		
		private Method(JSEnvironment env, MethodId methodId, int length) {
			super(env,methodId.id,length);
			this.methodId = methodId;
		}

		// ToIndex(newLength) per spec (transfer/transferToFixedLength/
		// transferToImmutable's own AO) - throws RangeError for anything
		// outside [0, 2^53-1], unlike paramInt's silent ToInt32-adjacent
		// coercion, which let a too-large newLength silently become a huge
		// positive int, crashing with an OutOfMemoryError from `new
		// byte[byteLength]` instead of the spec-mandated RangeError
		// (test262 new-length-excessive.js). Also rejects anything beyond
		// Integer.MAX_VALUE separately - never a valid Java array size
		// regardless of what ToIndex itself allows.
		private int paramToIndexInt(Object[] arguments, int pos, int defaultValue) {
			Object a = param(arguments, pos, RuntimeUtil.UNDEFINED);
			if(a==RuntimeUtil.UNDEFINED) {
				return defaultValue;
			}
			long index = RuntimeUtil.toIndex(getEnvironment(), a);
			if(index>Integer.MAX_VALUE) {
				throw RuntimeUtil.rangeError("Buffer size exceeds maximum size");
			}
			return (int)index;
		}

	    @Override
		public Object call(final Object obj, final Object[] args) {
	    	if(!(obj instanceof ArrayBuffer)) {
	    		throw RuntimeUtil.typeError("Method ArrayBuffer.prototype.{0} called on incompatible receiver {1}", methodId.toString(), RuntimeUtil.objectTypeName(getEnvironment(),obj));
	    	}

	    	// Current Object
			final ArrayBuffer _this = (ArrayBuffer)obj;			
	    	
	    	switch(methodId) {
	    	
	    		case resize-> {
	    			if(!_this.isResizable()) {
	    				throw RuntimeUtil.typeError("ArrayBuffer is not resizable");
	    			}
	    			int newByteLength = paramInt(args, 0);
	    			if(_this.isDetached()) {
	    				throw RuntimeUtil.typeError("ArrayBuffer is detached");
	    			}
	    			if(newByteLength<0 || newByteLength>_this.getMaxByteLength()) {
	    				throw RuntimeUtil.rangeError("Invalid new buffer size");
	    			}
	    			_this.resize(newByteLength);
	    			return RuntimeUtil.UNDEFINED;
	    		}
	    		case slice-> {
	    			int start = paramInt(args, 0, 0);
	    			int end = paramInt(args, 1, _this.getByteLength());
	    			ArrayBuffer sliced = (ArrayBuffer)_this.slice(start, end);
	    			JSEnvironment env = getEnvironment();
	    			Constructor defaultCtor = env.getStandardObjects().getConstructor(ArrayBufferConstructor.CLASSNAME);
	    			Constructor ctor = RuntimeUtil.speciesConstructor(env, _this, defaultCtor);
	    			if(ctor==defaultCtor) {
	    				return sliced;
	    			}
	    			Object newBufObj = RuntimeUtil.constructObject(env, ctor, new Object[] { sliced.getByteLength() });
	    			if(newBufObj==_this) {
	    				throw RuntimeUtil.typeError("Species constructor returned the same ArrayBuffer");
	    			}
	    			if(!(newBufObj instanceof ArrayBuffer newBuf)) {
	    				throw RuntimeUtil.typeError("Species constructor did not return an ArrayBuffer");
	    			}
	    			if(newBuf.isDetached()) {
	    				throw RuntimeUtil.typeError("Species constructor returned a detached ArrayBuffer");
	    			}
	    			if(newBuf.getByteLength()<sliced.getByteLength()) {
	    				throw RuntimeUtil.typeError("Species constructor returned an ArrayBuffer that is too small");
	    			}
	    			if(newBuf.isImmutable()) {
	    				throw RuntimeUtil.typeError("Species constructor returned an immutable ArrayBuffer");
	    			}
	    			if(_this.isDetached()) {
	    				throw RuntimeUtil.typeError("ArrayBuffer got detached during species construction");
	    			}
	    			System.arraycopy(sliced.getBytes(), 0, newBuf.getBytes(), 0, sliced.getByteLength());
	    			return newBuf;
	    		}
	    		case sliceToImmutable-> {
	    			// Detachment must be verified BEFORE start/end are ever
	    			// read/coerced (test262 this-is-not-detached.js: a
	    			// poisoned start/end valueOf() must never be called at
	    			// all), and `len` must be captured HERE, before that
	    			// coercion runs and can resize the buffer - see
	    			// ArrayBuffer.sliceToImmutable()'s own doc comment for
	    			// why bounds are resolved against THIS captured value,
	    			// not whatever the buffer's length is by the time this
	    			// method's Java body actually runs.
	    			if (_this.isDetached()) {
	    				throw RuntimeUtil.typeError("Buffer is detached");
	    			}
	    			int len = _this.getByteLength();
	    			int start = paramInt(args, 0, 0);
	    			int end = paramInt(args, 1, len);
	    			return _this.sliceToImmutable(len, start, end);
	    		}
	    		case transfer-> {
	    			int newByteLength = paramToIndexInt(args, 0, _this.getByteLength());
    				return _this.transfer(newByteLength);
	    		}
	    		case transferToFixedLength-> {
	    			int newByteLength = paramToIndexInt(args, 0, _this.getByteLength());
    				return _this.transferToFixedLength(newByteLength);
	    		}
	    		case transferToImmutable-> {
	    			int newByteLength = paramToIndexInt(args, 0, _this.getByteLength());
    				return _this.transferToImmutable(newByteLength);
	    		}

	            default-> {
	    		    throw new IllegalStateException(); // Should never be here 
	            }
	        }
	    }
	}	
}