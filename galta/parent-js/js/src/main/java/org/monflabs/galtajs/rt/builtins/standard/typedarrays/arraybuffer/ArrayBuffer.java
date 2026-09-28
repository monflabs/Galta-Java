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
package org.monflabs.galtajs.rt.builtins.standard.typedarrays.arraybuffer;

import java.nio.ByteOrder;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.builtins.JSAccessor;
import org.monflabs.galtajs.rt.builtins.standard.typedarrays.BaseArrayBuffer;

public class ArrayBuffer extends BaseArrayBuffer {
	
	public static final boolean LITTLE_INDIAN = !ByteOrder.nativeOrder().equals(ByteOrder.BIG_ENDIAN);

	public ArrayBuffer(int size) {
		this(size,-1);
	}
	public ArrayBuffer(int size, int maxByteLength) {
		super(new byte[size],maxByteLength);
	}
	private ArrayBuffer(byte[] buf, int maxByteLength) {
		super(buf,maxByteLength);
	}
	// An immutable, fixed-length buffer over `bytes` (taken as-is, not
	// copied) - what a `with { type: "bytes" }` module's default export is
	// built on (spec CreateBytesModule: an immutable ArrayBuffer).
	public static ArrayBuffer immutableOf(byte[] bytes) {
		ArrayBuffer result = new ArrayBuffer(bytes,-1);
		result.setImmutable(true);
		return result;
	}

	@Override
	protected ArrayBuffer create(byte[] buf, int maxByteLength) {
		return new ArrayBuffer(buf,maxByteLength);
	}

	@Override
	public JSAccessor createAccessor(JSEnvironment env) {
		return new ArrayBufferAccessor(env);
	}
	
	public ArrayBuffer transfer(int byteLength) {
		return _transfer(byteLength, false);
	}
	public ArrayBuffer transferToFixedLength(int byteLength) {
		return _transfer(byteLength, true);
	}
	// ArrayBuffer.prototype.transferToImmutable() - like
	// transferToFixedLength(), but the new buffer is immutable rather than
	// merely fixed-length (an immutable buffer is always fixed-length too,
	// so this reuses the same detach-and-copy machinery).
	public ArrayBuffer transferToImmutable(int byteLength) {
		ArrayBuffer result = _transfer(byteLength, true);
		result.setImmutable(true);
		return result;
	}
	private ArrayBuffer _transfer(int byteLength, boolean fixedLength) {
		if (buf==null) {
			throw RuntimeUtil.typeError("Buffer is detached");
		}
		// newByteLength (ToIndex, possibly running user valueOf()) is always
		// read by the caller BEFORE this is called - the immutable check
		// itself must come after that, per transfer/transferToFixedLength's
		// own "must read newLength before verifying mutability" ordering
		// (confirmed via test262's this-is-immutable-arraybuffer.js).
		if (immutable) {
			throw RuntimeUtil.typeError("Cannot transfer an immutable ArrayBuffer");
		}
		if (byteLength<0) {
			throw RuntimeUtil.rangeError("Buffer size exceeds maximum size");
		}
		// The maxByteLength-vs-newByteLength bound only applies when
		// PRESERVING resizability (plain transfer()) - the new buffer then
		// inherits the SOURCE's own max, so it must fit within it.
		// transferToFixedLength()/transferToImmutable() instead allocate a
		// brand new, non-resizable buffer of exactly newByteLength with no
		// such constraint at all (spec ArrayBufferCopyAndDetach's
		// fixed-length/immutable branch never consults the source's
		// [[ArrayBufferMaxByteLength]]) - confirmed via test262
		// transferToImmutable/to-larger.js, which transfers a resizable
		// 4-byte buffer (maxByteLength 8) to 9 bytes.
		if (!fixedLength && maxByteLength>=0 && byteLength>maxByteLength) {
			throw RuntimeUtil.rangeError("Buffer size exceeds maximum size");
		}
		byte[] newBuf = new byte[byteLength];
		System.arraycopy(buf, 0, newBuf, 0, Math.min(buf.length, byteLength));
		buf = null;
		int maxLength = fixedLength ? -1 : maxByteLength;
		return new ArrayBuffer(newBuf,maxLength);
	}

	// ArrayBuffer.prototype.sliceToImmutable(start, end) - like slice(),
	// but produces a genuinely immutable buffer directly; per spec this
	// does NOT go through SpeciesConstructor at all (confirmed via
	// species-returns-immutable-arraybuffer.js, which calls it standalone
	// from inside a species callback with no further indirection).
	//
	// Unlike plain slice() (which reads its own bounds against the CURRENT
	// length at call time), spec's own algorithm requires `len` to be
	// captured BEFORE start/end are coerced (ToIntegerOrInfinity, which may
	// run arbitrary user code that resizes or detaches the buffer) - bounds
	// are resolved against that ORIGINAL len, and only the FINAL check
	// (currentLen < final => RangeError) re-reads the buffer's state
	// afterward. Confirmed via test262 this-grows.js/this-shrinks.js
	// (bounds must use the pre-coercion length) and this-is-not-detached.js
	// (detachment must be checked before coercing start/end at all - see
	// this method's own caller in ArrayBufferPrototype.java for that half).
	// `len` is therefore passed in explicitly by the caller (captured
	// before it coerces start/end into these already-resolved int
	// parameters), rather than read from `this` here.
	public ArrayBuffer sliceToImmutable(int len, int start, int end) {
		int first = actualIndex(start, len);
		int last = actualIndex(end, len);
		int newLen = Math.max(last - first, 0);
		if (buf==null) {
			throw RuntimeUtil.typeError("Buffer is detached");
		}
		if (getByteLength() < last) {
			throw RuntimeUtil.rangeError("Buffer size exceeds maximum size");
		}
		byte[] newBuf = new byte[newLen];
		System.arraycopy(buf, first, newBuf, 0, newLen);
		ArrayBuffer result = create(newBuf, -1);
		result.setImmutable(true);
		return result;
	}
}
