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
package org.monflabs.galtajs.rt.builtins.standard.typedarrays;

import java.util.Iterator;
import java.util.NoSuchElementException;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.builtins.AbstractPropertiesHolder;
import org.monflabs.galtajs.rt.builtins.standard.typedarrays.arraybuffer.ArrayBuffer;
import org.monflabs.json.JsonArray.EntryConsumer;
import org.monflabs.json.JsonArray.EntryConsumerWhile;

public abstract class TypedArray extends AbstractPropertiesHolder implements ArrayBufferView {

	// Sentinel `length` value for the 4-arg (buffer,byteOffset,length)
	// constructor meaning "no explicit length was given" - the spec's
	// auto-length-tracking view (e.g. `new Uint8Array(resizableBuffer)`),
	// whose reported length/byteLength must be recomputed from the
	// buffer's CURRENT length on every access rather than cached once at
	// construction. Kept as a sentinel (not a boolean overload) to avoid
	// touching every concrete TypedArray subclass's constructor signature
	// across all 11 element types.
	public static final long LENGTH_TRACKING = -1L;

	private JSEnvironment env;
	private TypedArrayConstructor ctor;
	private BaseArrayBuffer buffer;
	private long byteOffset;
	private long byteLength; // only meaningful when !lengthTracking
	private boolean lengthTracking;

	public TypedArray(TypedArray array) {
		this.env = array.env;
		this.ctor = array.ctor;
		// Must size the new buffer from getByteLength() (the dynamic,
		// currently-correct value), NOT the raw byteLength field - that
		// field is only meaningful for a fixed-length source view; for a
		// length-tracking one it's left unset/stale (0), which previously
		// sized this buffer as empty regardless of the source's real
		// current length, corrupting the subsequent arraycopy in copyTo()
		// (confirmed via with/immutable.js and its siblings, whose clone
		// is taken from a length-tracking view backed by a resizable
		// buffer).
		long len = array.getByteLength();
		this.buffer = new ArrayBuffer((int)len);
		this.byteOffset = 0;
		this.byteLength = len;
		array.copyTo(buffer.getBytes(), byteOffset, byteLength);
	}
	private TypedArray(JSEnvironment env, String ctorClassName) {
		this.env = env;
		this.ctor = (TypedArrayConstructor)env.getStandardObjects().getConstructor(ctorClassName);
	}
	
	public TypedArray(JSEnvironment env, String ctorClassName, long length) {
		this(env,ctorClassName);
		// The byte size is computed in long: length*bytesPerElement overflows an int
		// well before length itself does (e.g. a 0x20000001 element Float64Array)
		long bytes = length*(long)ctor.getBytesPerElement();
		if(length<0 || bytes>=Integer.MAX_VALUE-8) {
			throw RuntimeUtil.rangeError("Invalid TypedArray length '{0}'",length);
		}
		this.buffer = new ArrayBuffer((int)bytes);
		this.byteOffset = 0;
		this.byteLength = (int)bytes;
	}
	public TypedArray(JSEnvironment env, String ctorClassName, BaseArrayBuffer buffer, long byteOffset, long length) {
		this(env,ctorClassName);
		if(byteOffset<0) {
			throw RuntimeUtil.rangeError("Invalid TypedArray byteOffset '{0}'",byteOffset);
		}
		if(length==LENGTH_TRACKING) {
			if(byteOffset>buffer.getByteLength()) {
				throw RuntimeUtil.rangeError("Invalid TypedArray byteOffset '{0}'",byteOffset);
			}
			this.buffer = buffer;
			this.byteOffset = (int)byteOffset;
			this.lengthTracking = true;
			return;
		}
		if(byteOffset+length*ctor.getBytesPerElement()>buffer.getByteLength()) {
			throw RuntimeUtil.rangeError("Invalid TypedArray byteOffset '{0}'",byteOffset);
		}
		if(length<0 || length>=Integer.MAX_VALUE) {
			throw RuntimeUtil.rangeError("Invalid TypedArray byteLength '{0}'",byteLength);
		}
		this.buffer = buffer;
		this.byteOffset = (int)byteOffset;
		this.byteLength = (int)length*ctor.getBytesPerElement();
	}
	
	public JSEnvironment getEnvironment() {
		return env;
	}

	public final TypedArrayConstructor getTypedArrayConstructor() {
		return ctor;
	}

	@Override
	public abstract TypedArray clone();

	public abstract TypedArray create(long length); 
	public abstract TypedArray subarray(long offset, long length); 

	public abstract Number get(long index);
	public abstract void set(long index, Number value);

	// Spec's [[ContentType]] internal slot: true for BigInt64Array/
	// BigUint64Array, false for every other typed array type - determines
	// whether element assignment must go through ToBigInt (not ToNumber).
	public boolean isBigIntTypedArray() {
		return false;
	}

	public TypedArrayConstructor getConstructor() {
		return ctor;
	}


	public void copyTo(byte[] destination, long byteOffset, long byteLength) {
		byte[] src = getArrayBuffer().getBytes();
		System.arraycopy(src,(int)this.byteOffset,destination,(int)byteOffset,(int)byteLength);
	}

	public long actualIndex(long index) {
		return actualIndex(getLength(), index);
	}
	// Callers that need a length read exactly ONCE, cached BEFORE argument
	// coercion (which may itself resize/detach the underlying resizable
	// buffer, changing what getLength() would return afterward), should use
	// this overload instead - matches the equivalent BuiltinArrayPrototype
	// helpers' pattern (confirmed via e.g.
	// copyWithin/coerced-values-end-detached.js: a poisoned `end` argument
	// that detaches the buffer mid-coercion must not change what length
	// copyWithin's own "is there anything to actually copy" check computes
	// against - the detached-buffer TypeError must still fire).
	public long actualIndex(long len, long index) {
		return index<0 ? index + len : index;
	}

	// Spec's relative-index clamp (used after actualIndex() converts a
	// negative index to relative-from-end): a start/end/target argument is
	// NEVER out-of-range enough to throw - it's clamped into [0, length],
	// matching every spec algorithm that takes a "relative start/end"
	// (slice, copyWithin, fill, indexOf, lastIndexOf, includes, subarray,
	// with, at).
	public long boundIndex(long index) {
		return boundIndex(getLength(), index);
	}
	public long boundIndex(long len, long index) {
		if(index<0) {
			return 0;
		}
		return index>len ? len : index;
	}


	public BaseArrayBuffer getArrayBuffer() {
		return buffer;
	}

	public boolean isLengthTracking() {
		return lengthTracking;
	}

	// Spec's IsTypedArrayOutOfBounds: the buffer is detached, or (a
	// length-tracking view) byteOffset no longer fits within the buffer's
	// CURRENT length, or (a fixed-length view) byteOffset+byteLength no
	// longer fits - both only reachable via a resizable buffer having been
	// shrunk after this view was created.
	public boolean isOutOfBounds() {
		if(buffer.isDetached()) {
			return true;
		}
		long currentBufferLength = buffer.getByteLength();
		return lengthTracking ? byteOffset>currentBufferLength : byteOffset+byteLength>currentBufferLength;
	}

	// The spec's [[ByteOffset]] internal slot - always the RAW stored
	// offset, regardless of out-of-bounds status. Do NOT confuse this with
	// %TypedArray%.prototype.byteOffset (the JS-visible getter), which
	// separately applies the "0 once out of bounds" rule ONLY at that
	// property-read level (see TypedArrayPrototype.java's own "byteOffset"
	// getter) - every spec algorithm that computes byte offsets
	// internally (byteIndex() below, subarray's beginByteOffset, etc.)
	// needs the true raw value (confirmed via
	// subarray/byteoffset-with-detached-buffer.js, whose species
	// constructor must observe the array's real original byteOffset even
	// though the buffer is detached by the time subarray computes it).
	public long getByteOffset() {
		return byteOffset;
	}

	public long getByteLength() {
		if(isOutOfBounds()) {
			return 0;
		}
		if(lengthTracking) {
			long remaining = buffer.getByteLength()-byteOffset;
			return remaining - (remaining % ctor.getBytesPerElement());
		}
		return byteLength;
	}

	public long getLength() {
		return getByteLength() / ctor.getBytesPerElement();
	}

	// Spec's IsValidIntegerIndex, reused here so the shared iteration
	// helpers below (and anything else walking indices directly rather
	// than through the property-access exotic-object algorithm in
	// TypedArrayAccessor) tolerate a buffer detached MID-ITERATION (e.g. a
	// callback that calls ArrayBuffer.prototype.transfer()) the same way
	// [[Get]] does - by treating the read as "not present" rather than
	// throwing the low-level BaseArrayBuffer.checkBuffer() exception.
	public boolean isValidIndex(long index) {
		return !getArrayBuffer().isDetached() && index>=0 && index<getLength();
	}
	public Object getOrUndefined(long index) {
		return isValidIndex(index) ? get(index) : RuntimeUtil.UNDEFINED;
	}
	// Companion to getOrUndefined() for writes: silently no-ops (matching
	// IntegerIndexedElementSet's own silent-drop behavior) instead of
	// throwing BaseArrayBuffer.checkBuffer()'s low-level exception, for
	// callers (e.g. TypedArray.prototype.set) whose own spec algorithm
	// explicitly tolerates the buffer going invalid mid-write.
	public void setIfValid(long index, Number value) {
		if(isValidIndex(index)) {
			set(index, value);
		}
	}

	// Helpers
	protected int byteIndex(long index) {
		// Make it an int to access the byte[]
		return (int)(getByteOffset() + index * ctor.getBytesPerElement());
	}

	public Iterator<Number> values() {
        return new Iterator<Number>() {
            private long i = 0;
            @Override
            public boolean hasNext() {
                return i<getLength();
            }
            @Override
            public Number next() {
            	if(i<getLength()) {
            		return get(i++);
            	}
				throw new NoSuchElementException();
            }
        };
	}

	public void jsForEach(EntryConsumer c) {
		jsForEach(c, getLength());
	}
	// Callers that already cached `len` BEFORE some argument coercion that
	// could resize/detach the buffer (and so change what a fresh
	// getLength() would now return) should use this overload instead, so
	// the iteration bound reflects the length AT THE TIME the operation
	// started - not a value re-derived mid-call (confirmed via
	// includes/detached-buffer-during-fromIndex-returns-true-for-
	// undefined.js and several TypedArrayPrototype.java callers below).
	public void jsForEach(EntryConsumer c, long len) {
		for(long i=0; i<len; i++) {
			c.process(i, getOrUndefined(i));
		}
	}
	public boolean jsForEachWhile(EntryConsumerWhile c, long start) {
		return jsForEachWhile(c, start, getLength());
	}
	public boolean jsForEachWhile(EntryConsumerWhile c, long start, long len) {
		for(long i=start; i<len; i++) {
			if(!c.process(i, getOrUndefined(i))) {
				return false;
			}
		}
		return true;
	}
	public void jsForEachReverse(EntryConsumer c) {
		jsForEachReverse(c, getLength());
	}
	public void jsForEachReverse(EntryConsumer c, long len) {
		for(long i=len-1; i>=0; i--) {
			c.process(i, getOrUndefined(i));
		}
	}
	public boolean jsForEachWhileReverse(EntryConsumerWhile c, long start) {
		for(long i=start-1; i>=0; i--) {
			if(!c.process(i, getOrUndefined(i))) {
				return false;
			}
		}
		return true;
	}
}
