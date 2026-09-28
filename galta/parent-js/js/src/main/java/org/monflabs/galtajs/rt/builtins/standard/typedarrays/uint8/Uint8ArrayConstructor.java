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
package org.monflabs.galtajs.rt.builtins.standard.typedarrays.uint8;

import java.io.ByteArrayOutputStream;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.builtins.BaseMethod;
import org.monflabs.galtajs.rt.builtins.standard.typedarrays.BaseArrayBuffer;
import org.monflabs.galtajs.rt.builtins.standard.typedarrays.TypedArray;
import org.monflabs.galtajs.rt.builtins.standard.typedarrays.TypedArrayConstructor;

public class Uint8ArrayConstructor extends TypedArrayConstructor {

	public static final String CLASSNAME = "Uint8Array";
	public static final int BYTES_PER_ELEMENT = 1;

	public Uint8ArrayConstructor(JSEnvironment env) {
		super(env,CLASSNAME,Uint8ArrayPrototype.get(env),BYTES_PER_ELEMENT);
		setOwnMethod(new Method(env, MethodId.fromBase64, 1));
		setOwnMethod(new Method(env, MethodId.fromHex, 1));
	}

	@Override
	public Class<?> getNativeClass() {
		return Uint8Array.class;
	}

	@Override
	public Uint8Array createTypedArray(long length) {
		return new Uint8Array(getEnvironment(),length);
	}
	@Override
	public TypedArray createTypedArray(TypedArray typedArray) {
		long length = typedArray.getLength();
		TypedArray a = createTypedArray(length);
		for(long i=0; i<length; i++) {
			Object value = typedArray.get(i);
			a.set(i, RuntimeUtil.toUInt8(getEnvironment(),value));
		}
		return a;
		
	}
	@Override
	public TypedArray createTypedArray(BaseArrayBuffer buffer, long byteOffset, long length) {
		return new Uint8Array(getEnvironment(),buffer,byteOffset,length);
	}

	private static enum MethodId {
		fromBase64,
		fromHex,
	}

	private final class Method extends BaseMethod {
		private MethodId methodId;

		private Method(JSEnvironment env, MethodId methodId, int length) {
			super(env, methodId.name(), length);
			this.methodId = methodId;
		}

		@Override
		public Object call(final Object obj, final Object[] args) {
			JSEnvironment env = getEnvironment();
			Object stringArg = param(args, 0, RuntimeUtil.UNDEFINED);
			if(!(stringArg instanceof String s) || !Base64HexCodec.isStringPrimitive(env, stringArg)) {
				throw RuntimeUtil.typeError("{0} argument must be a string", methodId.name());
			}
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			switch(methodId) {
				case fromBase64 -> {
					Object optionsArg = param(args, 1, RuntimeUtil.UNDEFINED);
					Base64HexCodec.Alphabet alphabet = Base64HexCodec.readAlphabetOption(env, optionsArg);
					Base64HexCodec.LastChunkHandling mode = Base64HexCodec.readLastChunkHandlingOption(env, optionsArg);
					Base64HexCodec.decodeBase64(s, alphabet, mode, Integer.MAX_VALUE, out::write);
				}
				case fromHex -> {
					Base64HexCodec.decodeHex(s, Integer.MAX_VALUE, out::write);
				}
				default -> throw new IllegalStateException();
			}
			byte[] bytes = out.toByteArray();
			Uint8Array a = createTypedArray(bytes.length);
			for(int i=0; i<bytes.length; i++) {
				a.set(i, bytes[i]&0xFF);
			}
			return a;
		}
	}
}
