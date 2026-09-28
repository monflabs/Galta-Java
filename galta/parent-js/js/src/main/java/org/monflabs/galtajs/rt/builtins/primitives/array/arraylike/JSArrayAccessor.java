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
package org.monflabs.galtajs.rt.builtins.primitives.array.arraylike;

import org.eclipse.jdt.annotation.NonNull;
import org.monflabs.galtajs.jsonfactory.JSArray;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.builtins.JSAccessor;
import org.monflabs.galtajs.rt.builtins.PropertyDescriptor;
import org.monflabs.util.StringFormat;


//
// This implementation might be wroing as it does
//
public class JSArrayAccessor extends JSBaseArray {

	public static JSArrayAccessor of(@NonNull JSAccessor accessor, @NonNull Object object) {
		return new JSArrayAccessor(accessor,object);
	}

	private JSAccessor accessor;
	private Object object;
	
	private JSArrayAccessor(@NonNull JSAccessor accessor, @NonNull Object object) {
		super(accessor.getEnvironment());
		this.accessor = accessor;
		this.object = object;
	}

	@Override
	public long arrayLength() {
		// Spec: this class is the generic array-LIKE wrapper (a Proxy or
		// other non-JSArray accessor-backed object), so array-generic
		// methods (slice/forEach/indexOf/...) reading its "length" must use
		// LengthOfArrayLike's ToLength - clamped to [0, 2^53-1], never
		// throwing - not the stricter MAX_ARRAY_SIZE (2^31-1, a genuine
		// Array exotic object's own bound, enforced elsewhere by JSArrayImpl/
		// BuiltinArrayConstructor) - confirmed via
		// slice/length-exceeding-integer-limit-proxied-array.js, whose Proxy
		// "length" trap intentionally returns 2^53+2 and still expects a
		// normal (clamped, non-throwing) slice.
		Object l = accessor.getProperty(object,"length",0);
		return RuntimeUtil.toLength(getEnvironment(), l);
	}
	@Override
	public Object arrayGet(long index, Object defaultValue) {
		return accessor.getProperty(object, index, defaultValue);
	}

	// JSArray's own default getOwnProperty(long,...) only calls arrayGet()
	// when RuntimeUtil.isMemberIndex(index) - true up to ~2^32-2, a genuine
	// Array exotic object's own index bound - falling through to
	// JSObjectDelegate's unrelated "members" bag default otherwise. That
	// bound is meaningless here: this class wraps an arbitrary accessor
	// (e.g. a Proxy), which must be consulted via its OWN "get" trap for
	// ANY numeric index, however large (ToLength permits up to 2^53-1) -
	// confirmed via slice/length-exceeding-integer-limit-proxied-array.js,
	// whose Proxy "length" trap reports a huge value and expects indices
	// near it to be read back correctly, not silently missed.
	@Override
	public Object getOwnProperty(long index, Object defaultValue, Object receiver) {
		return arrayGet(index, defaultValue);
	}
	// Same rationale as getOwnProperty above, for the write side: JSArray's
	// shared default setOwnProperty(long,...) is ALSO gated on
	// isMemberIndex(index), silently falling through to JSObjectDelegate's
	// unrelated "members" bag (never reaching the wrapped accessor's own
	// "set"/"defineProperty" traps at all) for any index beyond ~2^32-2 -
	// confirmed via
	// reverse/length-exceeding-integer-limit-with-proxy.js, whose
	// swap-loop writes to indices near 2^53 and expects the genuine
	// Set/GetOwnPropertyDescriptor/DefineProperty trap sequence, not a
	// silent no-op.
	@Override
	public boolean setOwnProperty(long index, Object value, PropertyDescriptor desc, DESC_CHECK check, Object receiver) {
		return arraySet(index, value, check);
	}


	@Override
	public boolean arraySetLength(long size, DESC_CHECK check) {
		return accessor.setProperty(object,"length",size, null, check);
	}
	@Override
	public JSArray arrayAdd(Object value, DESC_CHECK check) {
		long size = arrayLength();
		if(size==JSArray.MAX_ARRAY_SIZE) {
			throw RuntimeUtil.rangeError("Invalid array length {0}",JSArray.MAX_ARRAY_SIZE+1);
		}
		accessor.setProperty(object, Long.toString(size), value, null, check);
		accessor.setProperty(object, "length", size+1, null, check);
		return this;
	}
	@Override
	public JSArray arrayAdd(long index, Object value, DESC_CHECK check) {
		long size = arrayLength();
		if(size==JSArray.MAX_ARRAY_SIZE) {
			throw RuntimeUtil.rangeError("Invalid array length {0}",JSArray.MAX_ARRAY_SIZE+1);
		}
		if(index>size) {
			throw new IllegalStateException(StringFormat.format("Ibvalid index in JSArray {0}", getClass()));
		}
		// We don't check the max array size on purpose
		for(long i=size-1; i>index; i--) {
			Object val = accessor.getProperty(object,Long.toString(i),null);
			accessor.setProperty(object,Long.toString(i+1),val, null, check);
		}
		accessor.setProperty(object,Long.toString(index), value, null, check);
		accessor.setProperty(object,"length", size+1, null, check);
		return this;
	}
	@Override
	public boolean arraySet(long index, Object value, DESC_CHECK check) {
		// No MAX_ARRAY_SIZE (2^31-1, a genuine Array exotic object's own
		// bound) cap here - this class wraps an arbitrary accessor (e.g. a
		// Proxy), which can and must accept a plain [[Set]] at ANY numeric
		// index (confirmed via
		// reverse/length-exceeding-integer-limit-with-proxy.js, whose
		// swap-loop sets an index near 2^53 and expects the write to
		// genuinely go through, not throw).
		accessor.setProperty(object,Long.toString(index), value, null,check);
		return true;
	}
	// See JSArray.arrayDefineDataProperty's doc comment - a genuine
	// [[DefineOwnProperty]] (CreateDataPropertyOrThrow), unlike arraySet's
	// plain [[Set]] above. Routes through the wrapped accessor's own
	// setOwnProperty (already correctly implementing [[DefineOwnProperty]]
	// for a Proxy target, including its "defineProperty" trap), not
	// arraySet's setProperty ([[Set]]).
	@Override
	public boolean arrayDefineDataProperty(long index, Object value, DESC_CHECK check) {
		return accessor.setOwnProperty(object, Long.toString(index), value, PropertyDescriptor.DESC_PROP_ARRAYINDEX, check, object);
	}
	@Override
	public boolean arrayDelete(long index, DESC_CHECK check) {
		// Spec [[Delete]] on a generic array-like object (a Proxy, or any
		// other non-JSArray accessor-backed object) is a plain single-
		// property delete - it must dispatch the "deleteProperty" trap and
		// must NOT touch "length" at all (that was this method's previous,
		// wrong behavior: replacing the value with undefined and shrinking
		// "length" instead of genuinely deleting - confirmed via
		// copyWithin/return-abrupt-from-delete-proxy-target.js, whose
		// deleteProperty trap must fire - and throw - for
		// DeletePropertyOrThrow's ReturnIfAbrupt to be observable at all).
		// No arrayLength() bound check either - DeletePropertyOrThrow
		// doesn't consult "length" at all, and doing so here was an extra,
		// unwanted "length" Get not present in the spec algorithm
		// (confirmed via the same reverse test above, whose expected trap
		// sequence has no "Get:length" between a swap's Get/Set pairs).
		if(index>=0) {
			accessor.deleteProperty(object,Long.toString(index),check);
			return true;
		}
		return false;
	}
	@Override
	public boolean arrayRemove(long index, DESC_CHECK check) {
		if(index>=0 && index<arrayLength()) {
			long size = arrayLength();
			for(long i=index; i<size-1; i++) {
				accessor.setProperty(object,Long.toString(i),accessor.getProperty(object,Long.toString(i+1),null), null, DESC_CHECK.CHECK);
			}
			accessor.deleteProperty(object,Long.toString(size-1),DESC_CHECK.CHECK);
			accessor.setProperty(object,"length", size-1, null, DESC_CHECK.CHECK);
			return true;
		}
		return false;
	}
}
