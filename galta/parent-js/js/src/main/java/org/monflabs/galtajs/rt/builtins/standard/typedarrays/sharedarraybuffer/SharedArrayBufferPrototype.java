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

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.builtins.BaseMethod;
import org.monflabs.galtajs.rt.builtins.BasePrototype;
import org.monflabs.galtajs.rt.builtins.Constructor;
import org.monflabs.galtajs.rt.builtins.PropertyDescriptor;
import org.monflabs.galtajs.rt.builtins.primitives.symbol.Symbol;

public class SharedArrayBufferPrototype extends BasePrototype {

	public static SharedArrayBufferPrototype get(JSEnvironment env) {
		SharedArrayBufferPrototype proto = (SharedArrayBufferPrototype)env.getRegisteredPrototype(SharedArrayBufferPrototype.class);
		if(proto==null) {
			proto = new SharedArrayBufferPrototype(env);
			env.registerPrototype(SharedArrayBufferPrototype.class,proto);
		}
		return proto;
	}
	
	private SharedArrayBufferPrototype(JSEnvironment env) {
		super(env);
		setOwnProperty(Symbol.TO_STRING_TAG,SharedArrayBufferConstructor.CLASSNAME,PropertyDescriptor.DESC_PROP_TOSTRINGTAG);

		setOwnProperty("byteLength",true,false, 
				(t,k) -> {
					if(t instanceof SharedArrayBuffer ab) {
						return ab.getByteLength();
					}
		    		throw RuntimeUtil.typeError("Property SharedArrayBuffer.byteLength requested on incompatible receiver {0}", t!=null?t.getClass():"null");
				}, 
				null
			);
		setOwnProperty("maxByteLength",true,false,
				(t,k) -> {
					if(t instanceof SharedArrayBuffer ab) {
						return ab.isResizable() ? ab.getMaxByteLength() : ab.getByteLength();
					}
		    		throw RuntimeUtil.typeError("Property SharedArrayBuffer.maxByteLength requested on incompatible receiver {0}", t!=null?t.getClass():"null");
				},
				null
			);
		setOwnProperty("growable",true,false,
				(t,k) -> {
					if(t instanceof SharedArrayBuffer ab) {
						return ab.isResizable();
					}
		    		throw RuntimeUtil.typeError("Property SharedArrayBuffer.growable requested on incompatible receiver {0}", t!=null?t.getClass():"null");
				},
				null
			);
		
		setOwnMethod(new Method(env,MethodId.grow,1));
		setOwnMethod(new Method(env,MethodId.slice,2));
	}
	
	private static enum MethodId {
		grow,
		slice,
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
		
	    @Override
		public Object call(final Object obj, final Object[] args) {
	    	if(!(obj instanceof SharedArrayBuffer)) {
	    		throw RuntimeUtil.typeError("Method ArrayBuffer.prototype.{0} called on incompatible receiver {1}", methodId.toString(), RuntimeUtil.objectTypeName(getEnvironment(),obj));
	    	}

	    	// Current Object
			final SharedArrayBuffer _this = (SharedArrayBuffer)obj;			
	    	
	    	switch(methodId) {
	    	
	    		case grow-> {
	    			int maxByteLength = _this.getMaxByteLength();
	    			if(maxByteLength<0) {
	    				throw RuntimeUtil.typeError("The buffer is not growable");
	    			}
	    			int newLength = paramInt(args, 0);
	    			if(newLength<_this.getByteLength() || newLength>maxByteLength) {
	    				throw RuntimeUtil.rangeError("Invalid new buffer size");
	    			}
	    			// Under the Atomics lock: an Atomics operation on another thread must not
	    			// write into the old byte[] while it is being copied
	    			synchronized(org.monflabs.galtajs.rt.builtins.standard.atomics.AtomicsWaitRegistry.get()) {
	    				_this.resize(newLength);
	    			}
	    			return RuntimeUtil.UNDEFINED;
	    		}
	    		case slice-> {
	    			int start = paramInt(args, 0, 0);
	    			int end = paramInt(args, 1, _this.getByteLength());
	    			SharedArrayBuffer sliced = (SharedArrayBuffer)_this.slice(start, end);
	    			JSEnvironment env = getEnvironment();
	    			Constructor defaultCtor = env.getStandardObjects().getConstructor(SharedArrayBufferConstructor.CLASSNAME);
	    			Constructor ctor = RuntimeUtil.speciesConstructor(env, _this, defaultCtor);
	    			if(ctor==defaultCtor) {
	    				return sliced;
	    			}
	    			Object newBufObj = RuntimeUtil.constructObject(env, ctor, new Object[] { sliced.getByteLength() });
	    			if(newBufObj==_this) {
	    				throw RuntimeUtil.typeError("Species constructor returned the same SharedArrayBuffer");
	    			}
	    			if(!(newBufObj instanceof SharedArrayBuffer newBuf)) {
	    				throw RuntimeUtil.typeError("Species constructor did not return a SharedArrayBuffer");
	    			}
	    			if(newBuf.getByteLength()<sliced.getByteLength()) {
	    				throw RuntimeUtil.typeError("Species constructor returned a SharedArrayBuffer that is too small");
	    			}
	    			System.arraycopy(sliced.getBytes(), 0, newBuf.getBytes(), 0, sliced.getByteLength());
	    			return newBuf;
	    		}
	    		
	            default-> {
	    		    throw new IllegalStateException(); // Should never be here 
	            }
	        }
	    }
	}	
}