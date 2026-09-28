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
package org.monflabs.galtajs.rt.builtins.standard.typedarrays.uint32;

import java.math.BigDecimal;
import java.math.BigInteger;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.builtins.JSAccessor;
import org.monflabs.galtajs.rt.builtins.standard.typedarrays.BaseArrayBuffer;
import org.monflabs.galtajs.rt.builtins.standard.typedarrays.TypedArray;
import org.monflabs.galtajs.rt.builtins.standard.typedarrays.arraybuffer.ArrayBuffer;

public class Uint32Array extends TypedArray {

	public Uint32Array(Uint32Array array) {
		super(array);
	}
	public Uint32Array(JSEnvironment env, long length) {
		super(env,Uint32ArrayConstructor.CLASSNAME,length);
	}
	public Uint32Array(JSEnvironment env, BaseArrayBuffer buffer, long byteOffset, long length) {
		super(env,Uint32ArrayConstructor.CLASSNAME,buffer, byteOffset, length);
	}

	@Override
	public TypedArray create(long length) {
		return new Uint32Array(getEnvironment(),length);
	}
	
	@Override
	public TypedArray clone() {
		return new Uint32Array(this);
	}

	@Override
	public TypedArray subarray(long begin, long end) {
		long byteOffset = byteIndex(begin);
		long length = end-begin;
		return new Uint32Array(getEnvironment(),getArrayBuffer(),byteOffset,length);
	}

	@Override
	public JSAccessor createAccessor(JSEnvironment env) {
		return new Uint32ArrayAccessor(env);
	}
	
	@Override
	public Long get(long index) {
		if(index<0 || index>=getLength()) {
			throw RuntimeUtil.rangeError("Invalid array index {0}",index);
		}
		return getArrayBuffer().readUint32(byteIndex(index),ArrayBuffer.LITTLE_INDIAN);
	}

	@Override
	public void set(long index, Number value) {
		if(index<0 || index>=getLength()) {
			throw RuntimeUtil.rangeError("Invalid array index {0}",index);
		}
		if(!getEnvironment().supportMixedBigNumber() && (value instanceof BigInteger || value instanceof BigDecimal)) {
			throw RuntimeUtil.typeError("Invalid type for array {0}",RuntimeUtil.objectTypeName(getEnvironment(),value));
		}
		// value.longValue() would saturate to Long.MAX_VALUE for a Double
		// Infinity instead of wrapping to 0 - RuntimeUtil.toUInt32() applies
		// the correct spec ToUint32 (NaN/Infinity -> 0, then truncate/mask).
		getArrayBuffer().writeUint32(byteIndex(index),RuntimeUtil.toUInt32(value),ArrayBuffer.LITTLE_INDIAN);
	}
}
