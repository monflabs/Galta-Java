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
package org.monflabs.galtajs.rt.builtins.standard.typedarrays.bigint64;

import java.math.BigInteger;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.builtins.JSAccessor;
import org.monflabs.galtajs.rt.builtins.standard.typedarrays.BaseArrayBuffer;
import org.monflabs.galtajs.rt.builtins.standard.typedarrays.TypedArray;
import org.monflabs.galtajs.rt.builtins.standard.typedarrays.arraybuffer.ArrayBuffer;
import org.monflabs.json.JsonUtil;

public class BigtInt64Array extends TypedArray {

	public BigtInt64Array(BigtInt64Array array) {
		super(array);
	}
	public BigtInt64Array(JSEnvironment env, long length) {
		super(env,BigInt64ArrayConstructor.CLASSNAME,length);
	}
	public BigtInt64Array(JSEnvironment env, BaseArrayBuffer buffer, long byteOffset, long length) {
		super(env,BigInt64ArrayConstructor.CLASSNAME,buffer, byteOffset, length);
	}

	@Override
	public TypedArray create(long length) {
		return new BigtInt64Array(getEnvironment(),length);
	}
	
	@Override
	public TypedArray clone() {
		return new BigtInt64Array(this);
	}

	@Override
	public TypedArray subarray(long begin, long end) {
		long byteOffset = byteIndex(begin);
		long length = end-begin;
		return new BigtInt64Array(getEnvironment(),getArrayBuffer(),byteOffset,length);
	}

	@Override
	public JSAccessor createAccessor(JSEnvironment env) {
		return new BigInt64ArrayAccessor(env);
	}

	@Override
	public boolean isBigIntTypedArray() {
		return true;
	}

	@Override
	public BigInteger get(long index) {
		if(index<0 || index>=getLength()) {
			throw RuntimeUtil.rangeError("Invalid array index {0}",index);
		}
		return getArrayBuffer().readBigInt64(byteIndex(index),ArrayBuffer.LITTLE_INDIAN);
	}

	@Override
	public void set(long index, Number value) {
		if(index<0 || index>=getLength()) {
			throw RuntimeUtil.rangeError("Invalid array index {0}",index);
		}
		if(!getEnvironment().supportMixedBigNumber() && !(value instanceof BigInteger)) {
			throw RuntimeUtil.typeError("Invalid type for array {0}",RuntimeUtil.objectTypeName(getEnvironment(),value));
		}
		getArrayBuffer().writeBigInt64(byteIndex(index),JsonUtil.toBigInteger(value),ArrayBuffer.LITTLE_INDIAN);
	}
}
