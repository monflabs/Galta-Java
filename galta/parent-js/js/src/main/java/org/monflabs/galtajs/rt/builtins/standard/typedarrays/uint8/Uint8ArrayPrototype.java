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
import org.monflabs.galtajs.jsonfactory.JSObject;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.builtins.BaseMethod;
import org.monflabs.galtajs.rt.builtins.BasePrototype;
import org.monflabs.galtajs.rt.builtins.PropertyDescriptor;
import org.monflabs.galtajs.rt.builtins.standard.typedarrays.TypedArrayPrototype;

public class Uint8ArrayPrototype extends BasePrototype {

	public static Uint8ArrayPrototype get(JSEnvironment env) {
		Uint8ArrayPrototype proto = (Uint8ArrayPrototype)env.getRegisteredPrototype(Uint8ArrayPrototype.class);
		if(proto==null) {
			proto = new Uint8ArrayPrototype(env);
			env.registerPrototype(Uint8ArrayPrototype.class,proto);
		}
		return proto;
	}

	private Uint8ArrayPrototype(JSEnvironment env) {
		super(env);
		setOwnProperty("BYTES_PER_ELEMENT",Uint8ArrayConstructor.BYTES_PER_ELEMENT,PropertyDescriptor.DESC_READONLY_HIDDEN_PROP);
		setOwnMethod(new Method(env, MethodId.toBase64, 0));
		setOwnMethod(new Method(env, MethodId.setFromBase64, 1));
		setOwnMethod(new Method(env, MethodId.toHex, 0));
		setOwnMethod(new Method(env, MethodId.setFromHex, 1));
	}

	@Override
	protected Object getDefaultPrototype() {
		return TypedArrayPrototype.get(getEnvironment());
	}

	private static enum MethodId {
		toBase64,
		setFromBase64,
		toHex,
		setFromHex,
	}

	private static final class Method extends BaseMethod {
		private MethodId methodId;

		private Method(JSEnvironment env, MethodId methodId, int length) {
			super(env, methodId.name(), length);
			this.methodId = methodId;
		}

		@Override
		public Object call(final Object obj, final Object[] args) {
			JSEnvironment env = getEnvironment();
			switch(methodId) {
				case toBase64 -> {
					if(!(obj instanceof Uint8Array a)) {
						throw RuntimeUtil.typeError("Method Uint8Array.prototype.toBase64 called on incompatible receiver {0}", obj!=null?obj.getClass():"null");
					}
					Object optionsArg = param(args, 0, RuntimeUtil.UNDEFINED);
					Base64HexCodec.Alphabet alphabet = Base64HexCodec.readAlphabetOption(env, optionsArg);
					boolean omitPadding = Base64HexCodec.readOmitPaddingOption(env, optionsArg);
					if(a.getArrayBuffer().isDetached()) {
						throw RuntimeUtil.typeError("Cannot perform operation on a detached ArrayBuffer");
					}
					return Base64HexCodec.encodeBase64(readBytes(a), alphabet, omitPadding);
				}
				case toHex -> {
					if(!(obj instanceof Uint8Array a)) {
						throw RuntimeUtil.typeError("Method Uint8Array.prototype.toHex called on incompatible receiver {0}", obj!=null?obj.getClass():"null");
					}
					if(a.getArrayBuffer().isDetached()) {
						throw RuntimeUtil.typeError("Cannot perform operation on a detached ArrayBuffer");
					}
					return Base64HexCodec.encodeHex(readBytes(a));
				}
				case setFromBase64 -> {
					if(!(obj instanceof Uint8Array a)) {
						throw RuntimeUtil.typeError("Method Uint8Array.prototype.setFromBase64 called on incompatible receiver {0}", obj!=null?obj.getClass():"null");
					}
					// Must be checked before ANY argument is read (test262
					// throws-when-target-is-backed-by-immutable-
					// arraybuffer.js: a poisoned `options` getter must
					// never be called).
					if(a.getArrayBuffer().isImmutable()) {
						throw RuntimeUtil.typeError("Cannot write to a Uint8Array backed by an immutable ArrayBuffer");
					}
					Object stringArg = param(args, 0, RuntimeUtil.UNDEFINED);
					if(!(stringArg instanceof String s) || !Base64HexCodec.isStringPrimitive(env, stringArg)) {
						throw RuntimeUtil.typeError("setFromBase64 argument must be a string");
					}
					Object optionsArg = param(args, 1, RuntimeUtil.UNDEFINED);
					Base64HexCodec.Alphabet alphabet = Base64HexCodec.readAlphabetOption(env, optionsArg);
					Base64HexCodec.LastChunkHandling mode = Base64HexCodec.readLastChunkHandlingOption(env, optionsArg);
					if(a.getArrayBuffer().isDetached()) {
						throw RuntimeUtil.typeError("Cannot perform operation on a detached ArrayBuffer");
					}
					int maxLength = (int)a.getLength();
					int[] written = {0};
					int read = Base64HexCodec.decodeBase64(s, alphabet, mode, maxLength, b -> a.set(written[0]++, b));
					JSObject result = JSObject.create(env);
					result.setOwnProperty("read", (double)read);
					result.setOwnProperty("written", (double)written[0]);
					return result;
				}
				case setFromHex -> {
					if(!(obj instanceof Uint8Array a)) {
						throw RuntimeUtil.typeError("Method Uint8Array.prototype.setFromHex called on incompatible receiver {0}", obj!=null?obj.getClass():"null");
					}
					// Must be checked before ANY argument is read - see
					// setFromBase64's own identical check just above.
					if(a.getArrayBuffer().isImmutable()) {
						throw RuntimeUtil.typeError("Cannot write to a Uint8Array backed by an immutable ArrayBuffer");
					}
					Object stringArg = param(args, 0, RuntimeUtil.UNDEFINED);
					if(!(stringArg instanceof String s) || !Base64HexCodec.isStringPrimitive(env, stringArg)) {
						throw RuntimeUtil.typeError("setFromHex argument must be a string");
					}
					if(a.getArrayBuffer().isDetached()) {
						throw RuntimeUtil.typeError("Cannot perform operation on a detached ArrayBuffer");
					}
					int maxLength = (int)a.getLength();
					int[] written = {0};
					int read = Base64HexCodec.decodeHex(s, maxLength, b -> a.set(written[0]++, b));
					JSObject result = JSObject.create(env);
					result.setOwnProperty("read", (double)read);
					result.setOwnProperty("written", (double)written[0]);
					return result;
				}
				default -> throw new IllegalStateException();
			}
		}

		private static byte[] readBytes(Uint8Array a) {
			int n = (int)a.getLength();
			byte[] bytes = new byte[n];
			for(int i=0; i<n; i++) {
				bytes[i] = (byte)(a.get(i).intValue() & 0xFF);
			}
			return bytes;
		}
	}
}