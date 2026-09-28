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
package org.monflabs.galtajs.rt.builtins.standard.typedarrays.dataview;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.builtins.AbstractPropertiesHolder;
import org.monflabs.galtajs.rt.builtins.JSAccessor;
import org.monflabs.galtajs.rt.builtins.standard.typedarrays.ArrayBufferView;
import org.monflabs.galtajs.rt.builtins.standard.typedarrays.BaseArrayBuffer;

public class DataView extends AbstractPropertiesHolder implements ArrayBufferView {

	private BaseArrayBuffer arrayBuffer;
	private int byteOffset;
	private int byteLength; // only meaningful when !lengthTracking
	private boolean lengthTracking;

	public DataView(BaseArrayBuffer arrayBuffer, int byteOffset, int byteLength, boolean lengthTracking) {
		this.arrayBuffer = arrayBuffer;
		this.byteOffset = byteOffset;
		this.byteLength = byteLength;
		this.lengthTracking = lengthTracking;
	}

	@Override
	public JSAccessor createAccessor(JSEnvironment env) {
		return new DataViewAccessor(env);
	}

	public BaseArrayBuffer getArrayBuffer() {
		return arrayBuffer;
	}

	public int getByteOffset() {
		return byteOffset;
	}

	// IsViewOutOfBounds: detached, or (length-tracking view) the offset is
	// now beyond the buffer's current length, or (fixed-length view) the
	// offset+length no longer fits - both possible only via a resizable
	// buffer having been shrunk after this DataView was created.
	public boolean isOutOfBounds() {
		if(arrayBuffer.isDetached()) {
			return true;
		}
		int currentLength = arrayBuffer.getByteLength();
		return lengthTracking ? byteOffset>currentLength : byteOffset+byteLength>currentLength;
	}

	// GetViewByteLength: caller must have already confirmed !isOutOfBounds().
	public int getViewByteLength() {
		return lengthTracking ? arrayBuffer.getByteLength()-byteOffset : byteLength;
	}

	// DataView.prototype.byteLength / byteOffset getters throw TypeError
	// (not just return a stale value) once the view is out of bounds.
	public int getByteLength() {
		if(isOutOfBounds()) {
			throw RuntimeUtil.typeError("DataView is out of bounds of its buffer");
		}
		return getViewByteLength();
	}

	public int getCheckedByteOffset() {
		if(isOutOfBounds()) {
			throw RuntimeUtil.typeError("DataView is out of bounds of its buffer");
		}
		return byteOffset;
	}
}
