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
package org.monflabs.galtajs.rt.builtins.standard.typedarrays.int8;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.builtins.standard.typedarrays.BaseArrayBuffer;
import org.monflabs.galtajs.rt.builtins.standard.typedarrays.TypedArray;
import org.monflabs.galtajs.rt.builtins.standard.typedarrays.TypedArrayConstructor;

public class Int8ArrayConstructor extends TypedArrayConstructor {
	
	public static final String CLASSNAME = "Int8Array";
	public static final int BYTES_PER_ELEMENT = 1;
	
	public Int8ArrayConstructor(JSEnvironment env) {
		super(env,CLASSNAME,Int8ArrayPrototype.get(env),BYTES_PER_ELEMENT);
	}
	
	@Override
	public Class<?> getNativeClass() {
		return Int8Array.class;
	}

	@Override
	public Int8Array createTypedArray(long length) {
		return new Int8Array(getEnvironment(),length);
	}
	@Override
	public TypedArray createTypedArray(TypedArray typedArray) {
		long length = typedArray.getLength();
		TypedArray a = createTypedArray(length);
		for(long i=0; i<length; i++) {
			Object value = typedArray.get(i);
			a.set(i, RuntimeUtil.toInt8(getEnvironment(),value));
		}
		return a;
		
	}
	@Override
	public TypedArray createTypedArray(BaseArrayBuffer buffer, long byteOffset, long length) {
		return new Int8Array(getEnvironment(),buffer,byteOffset,length);
	}
}
