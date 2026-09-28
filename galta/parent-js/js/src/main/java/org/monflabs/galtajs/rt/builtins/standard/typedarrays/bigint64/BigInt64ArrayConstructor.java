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

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.rt.builtins.standard.typedarrays.BaseArrayBuffer;
import org.monflabs.galtajs.rt.builtins.standard.typedarrays.TypedArray;
import org.monflabs.galtajs.rt.builtins.standard.typedarrays.TypedArrayConstructor;

public class BigInt64ArrayConstructor extends TypedArrayConstructor {
	
	public static final String CLASSNAME = "BigInt64Array";
	public static final int BYTES_PER_ELEMENT = 8;
	
	public BigInt64ArrayConstructor(JSEnvironment env) {
		super(env,CLASSNAME,BigInt64ArrayPrototype.get(env),BYTES_PER_ELEMENT);
	}
	
	@Override
	public Class<?> getNativeClass() {
		return BigtInt64Array.class;
	}

	@Override
	public BigtInt64Array createTypedArray(long length) {
		return new BigtInt64Array(getEnvironment(),length);
	}
	@Override
	public TypedArray createTypedArray(TypedArray typedArray) {
		long length = typedArray.getLength();
		TypedArray a = createTypedArray(length);
		for(long i=0; i<length; i++) {
			Number value = typedArray.get(i);
			a.set(i, value);
		}
		return a;
		
	}
	@Override
	public TypedArray createTypedArray(BaseArrayBuffer buffer, long byteOffset, long length) {
		return new BigtInt64Array(getEnvironment(),buffer,byteOffset,length);
	}
}
