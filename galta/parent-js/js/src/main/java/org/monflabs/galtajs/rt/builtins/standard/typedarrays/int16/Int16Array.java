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
package org.monflabs.galtajs.rt.builtins.standard.typedarrays.int16;

import java.math.BigDecimal;
import java.math.BigInteger;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.builtins.JSAccessor;
import org.monflabs.galtajs.rt.builtins.standard.typedarrays.BaseArrayBuffer;
import org.monflabs.galtajs.rt.builtins.standard.typedarrays.TypedArray;
import org.monflabs.galtajs.rt.builtins.standard.typedarrays.arraybuffer.ArrayBuffer;

public class Int16Array extends TypedArray {

	public Int16Array(Int16Array array) {
		super(array);
	}
	public Int16Array(JSEnvironment env, long length) {
		super(env,Int16ArrayConstructor.CLASSNAME,length);
	}
	public Int16Array(JSEnvironment relm, BaseArrayBuffer buffer, long byteOffset, long length) {
		super(relm,Int16ArrayConstructor.CLASSNAME,buffer, byteOffset, length);
	}

	@Override
	public TypedArray create(long length) {
		return new Int16Array(getEnvironment(),length);
	}
	
	@Override
	public TypedArray clone() {
		return new Int16Array(this);
	}

	@Override
	public TypedArray subarray(long begin, long end) {
		long byteOffset = byteIndex(begin);
		long length = end-begin;
		return new Int16Array(getEnvironment(),getArrayBuffer(),byteOffset,length);
	}

	@Override
	public JSAccessor createAccessor(JSEnvironment env) {
		return new Int16ArrayAccessor(env);
	}

	@Override
	public Short get(long index) {
		if(index<0 || index>=getLength()) {
			throw RuntimeUtil.rangeError("Invalid array index {0}",index);
		}
		return getArrayBuffer().readInt16(byteIndex(index),ArrayBuffer.LITTLE_INDIAN);
	}

	@Override
	public void set(long index, Number value) {
		if(index<0 || index>=getLength()) {
			throw RuntimeUtil.rangeError("Invalid array index {0}",index);
		}
		if(!getEnvironment().supportMixedBigNumber() && (value instanceof BigInteger || value instanceof BigDecimal)) {
			throw RuntimeUtil.typeError("Invalid type for array {0}",RuntimeUtil.objectTypeName(getEnvironment(),value));
		}
		// See Int8Array.set() - Number.shortValue() narrows via intValue()
		// first, which SATURATES for an out-of-int32-range Double instead of
		// wrapping; toInt32() does the correct wraparound.
		getArrayBuffer().writeInt16(byteIndex(index),(short)RuntimeUtil.toInt32(value),ArrayBuffer.LITTLE_INDIAN);
	}
}
