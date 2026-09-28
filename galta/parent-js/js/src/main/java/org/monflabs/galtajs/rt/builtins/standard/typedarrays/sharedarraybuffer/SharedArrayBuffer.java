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
import org.monflabs.galtajs.rt.builtins.JSAccessor;
import org.monflabs.galtajs.rt.builtins.standard.typedarrays.BaseArrayBuffer;

public class SharedArrayBuffer extends BaseArrayBuffer {

	public SharedArrayBuffer(int size) {
		this(size,-1);
	}
	public SharedArrayBuffer(int size, int maxByteLength) {
		super(new byte[size],maxByteLength);
	}
	private SharedArrayBuffer(byte[] buf, int maxByteLength) {
		super(buf,maxByteLength);
	}

	@Override
	protected SharedArrayBuffer create(byte[] buf, int maxByteLength) {
		return new SharedArrayBuffer(buf,maxByteLength);
	}

	@Override
	public JSAccessor createAccessor(JSEnvironment env) {
		return new SharedArrayBufferAccessor(env);
	}

}
