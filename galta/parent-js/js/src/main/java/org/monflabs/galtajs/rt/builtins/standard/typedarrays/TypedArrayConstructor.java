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
package org.monflabs.galtajs.rt.builtins.standard.typedarrays;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.builtins.BasePrototype;
import org.monflabs.galtajs.rt.builtins.Constructor;
import org.monflabs.galtajs.rt.builtins.PropertyDescriptor;
import org.monflabs.galtajs.rt.builtins.primitives.BaseStandardConstructor;

public abstract class TypedArrayConstructor extends BaseStandardConstructor {

	public static final String CLASSNAME = "TypedArray";

	private int bytesPerElement;

	public TypedArrayConstructor(JSEnvironment env, String name, BasePrototype prototype, int bytesPerElement) {
		super(env,name,prototype,3);
		this.bytesPerElement = bytesPerElement;
		setOwnProperty("BYTES_PER_ELEMENT",bytesPerElement,PropertyDescriptor.DESC_READONLY_HIDDEN_PROP);
	}

	// Object.getPrototypeOf(Int8Array) etc. must be the shared abstract
	// %TypedArray% intrinsic (which itself inherits from Function.prototype),
	// not Function.prototype directly - this is also where from/of/isView/
	// Symbol.species now live, inherited by every concrete constructor
	// instead of being duplicated on each.
	@Override
	protected Object getDefaultPrototype() {
		return AbstractTypedArrayConstructor.get(getEnvironment());
	}

	public int getBytesPerElement() {
		return bytesPerElement;
	}


	public abstract TypedArray createTypedArray(long length);
	public abstract TypedArray createTypedArray(TypedArray typedArray);
	public abstract TypedArray createTypedArray(BaseArrayBuffer buffer, long byteOffset, long length);

	@Override
	public Object constructObject(Object[] parameters, Constructor topConstructor) {
		if(parameters.length==0 || RuntimeUtil.isNullOrUndefined(parameters[0])) {
			return applyNewTargetPrototype(createTypedArray(0), topConstructor);
		}
		Object p = param(parameters, 0, null);
		if(p instanceof TypedArray typedArray) {
			// InitializeTypedArrayFromTypedArray's own first check: a source
			// view whose underlying resizable buffer has since shrunk out
			// from under it (fixed-length view no longer fitting, or a
			// length-tracking view whose byteOffset itself no longer fits)
			// must throw TypeError here, NOT silently be treated as a
			// zero-length source the way getLength()/getByteLength() report
			// it everywhere else (confirmed via test262
			// src-typedarray-resizable-buffer.js).
			if(typedArray.isOutOfBounds()) {
				throw RuntimeUtil.typeError("TypedArray is out of bounds");
			}
			TypedArray a = createTypedArray(typedArray);
			return applyNewTargetPrototype(a, topConstructor);
		}
		if(p instanceof BaseArrayBuffer arrayBuffer) {
			// ToIndex(byteOffset) and ToIndex(length) are performed BEFORE the
			// detached-buffer check (and before reading the buffer's current
			// byte length) - a poisoned valueOf() on either argument that
			// detaches the buffer mid-conversion must still be caught by the
			// checks below, not an earlier one.
			long offset = RuntimeUtil.toIndex(getEnvironment(), param(parameters, 1, 0L));
			if(offset % bytesPerElement != 0) {
				throw RuntimeUtil.rangeError("Start offset {0} is not a multiple of {1}", offset, bytesPerElement);
			}
			Object lengthArg = param(parameters, 2, RuntimeUtil.UNDEFINED);
			boolean lengthUndefined = lengthArg==RuntimeUtil.UNDEFINED;
			long newLength = lengthUndefined ? 0 : RuntimeUtil.toIndex(getEnvironment(), lengthArg);

			if(arrayBuffer.isDetached()) {
				throw RuntimeUtil.typeError("Cannot perform Construct on a detached ArrayBuffer");
			}
			int bufferByteLength = arrayBuffer.getByteLength();
			long elementLength;
			// Spec's InitializeTypedArrayFromArrayBuffer: an omitted length
			// only produces an auto-length-tracking view (recomputed from
			// the buffer's CURRENT length on every later access) when the
			// buffer is itself resizable - for an ordinary fixed-length
			// buffer, an omitted length still just snapshots "everything
			// remaining" as a plain fixed length, same as before.
			if(lengthUndefined) {
				if(offset > bufferByteLength) {
					throw RuntimeUtil.rangeError("Invalid byteOffset {0}", offset);
				}
				if(arrayBuffer.isResizable()) {
					elementLength = TypedArray.LENGTH_TRACKING;
				} else {
					long remaining = bufferByteLength - offset;
					if(remaining % bytesPerElement != 0) {
						throw RuntimeUtil.rangeError("Buffer length minus the byteOffset is not a multiple of {0}", bytesPerElement);
					}
					elementLength = remaining / bytesPerElement;
				}
			} else {
				long newByteLength = newLength * bytesPerElement;
				if(offset + newByteLength > bufferByteLength) {
					throw RuntimeUtil.rangeError("Invalid length {0}", newLength);
				}
				elementLength = newLength;
			}
			TypedArray a = createTypedArray(arrayBuffer,offset,elementLength);
			return applyNewTargetPrototype(a, topConstructor);
		}
		if(!RuntimeUtil.isPrimitiveValue(getEnvironment(),p)) {
			return AbstractTypedArrayConstructor.from(getEnvironment(), this, topConstructor, p, RuntimeUtil.UNDEFINED, RuntimeUtil.UNDEFINED);
		}

		long len = RuntimeUtil.toIndex(getEnvironment(),p);
		TypedArray a = createTypedArray(len);
		return applyNewTargetPrototype(a, topConstructor);
	}

	@Override
	public Object call(Object _this, Object[] parameters) {
		throw RuntimeUtil.typeError("ByteBuffer() cannot be used as a function");
	}

}
