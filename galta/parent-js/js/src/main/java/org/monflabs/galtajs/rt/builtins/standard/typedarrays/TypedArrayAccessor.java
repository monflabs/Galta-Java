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
import java.util.Map;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.jsonfactory.JSObject.DESC_CHECK;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.builtins.PropertyDescriptor;
import org.monflabs.galtajs.rt.builtins.primitives.BaseObjectWrapperAccessor;
import org.monflabs.util.iterators.Iterators;

public abstract class TypedArrayAccessor extends BaseObjectWrapperAccessor {

	public TypedArrayAccessor(JSEnvironment env) {
		super(env);
	}

	// Spec: [[PreventExtensions]] on a TypedArray backed by a RESIZABLE
	// ArrayBuffer always fails (unconditionally - regardless of current
	// length, offset, or whether the view happens to be zero-length right
	// now), since the buffer could grow again later, making "extensions
	// prevented" an unstable guarantee this engine can't actually enforce.
	// A fixed-length (non-resizable) buffer's view is unaffected. The
	// generic Object.freeze/seal/preventExtensions machinery already
	// throws TypeError on a `false` return here (SetIntegrityLevel's own
	// required ? O.[[PreventExtensions]]() step, round 2/3's
	// `accessor.preventExtensions(o)` fix) - no separate change needed
	// there (confirmed via freeze/typedarray-backed-by-resizable-buffer.js).
	@Override
	public boolean preventExtensions(Object _this) {
		if(_this instanceof TypedArray ta && ta.getArrayBuffer().isResizable()) {
			return false;
		}
		return super.preventExtensions(_this);
	}

	// Spec 10.4.5.9 IsValidIntegerIndex: a canonical numeric index only
	// addresses a real element if the buffer isn't detached, the index is a
	// (non -0) integer, and it's in [0, length). Any other canonical numeric
	// string (out-of-bounds, negative, fractional, -0, NaN, Infinity, or a
	// detached buffer) is NEVER an ordinary property either - it's simply
	// "not present" - so this same check gates [[GetOwnProperty]],
	// [[HasProperty]] (derived from it), [[DefineOwnProperty]], [[Set]] and
	// [[Delete]] uniformly.
	private static boolean isValidIntegerIndex(TypedArray ta, double index) {
		if(ta.getArrayBuffer().isDetached()) {
			return false;
		}
		if(Double.isInfinite(index) || index!=Math.floor(index)) {
			return false;
		}
		if(Double.compare(index,-0.0)==0) {
			return false;
		}
		return index>=0 && index<ta.getLength();
	}

	// ObjectWrapperAccessor overrides getOwnPropertyDescriptor(...) itself
	// (consulting only the "extra properties" bag, not getOwnProperty(...)),
	// so - unlike JSAccessor's generic derive-from-getOwnProperty default -
	// a valid index needs an explicit override here too, or
	// Object.getOwnPropertyDescriptor(typedArray,"0") would wrongly see
	// nothing at all.
	@Override
	public PropertyDescriptor getOwnPropertyDescriptor(Object _this, String member) {
		Double canonical = RuntimeUtil.canonicalNumericIndexString(getEnvironment(), member);
		if(canonical!=null) {
			return isValidIntegerIndex((TypedArray)_this,canonical) ? PropertyDescriptor.DESC_DEFAULT : null;
		}
		return super.getOwnPropertyDescriptor(_this, member);
	}
	@Override
	public PropertyDescriptor getOwnPropertyDescriptor(Object _this, long index) {
		return isValidIntegerIndex((TypedArray)_this,index) ? PropertyDescriptor.DESC_DEFAULT : super.getOwnPropertyDescriptor(_this, index);
	}

	@Override
	public Object getOwnProperty(Object _this, String member, Object defaultValue, Object receiver) {
		Double canonical = RuntimeUtil.canonicalNumericIndexString(getEnvironment(), member);
		if(canonical!=null) {
			TypedArray ta = (TypedArray) _this;
			return isValidIntegerIndex(ta,canonical) ? ta.get(canonical.longValue()) : defaultValue;
		}
		return super.getOwnProperty(_this, member, defaultValue, receiver);
	}

	@Override
	public Object getOwnProperty(Object _this, long index, Object defaultValue, Object receiver) {
		TypedArray ta = (TypedArray) _this;
		return isValidIntegerIndex(ta,index) ? ta.get(index) : defaultValue;
	}

	// Spec 10.4.5.4 [[Get]] / 10.4.5.2 [[HasProperty]]: a canonical numeric
	// key NEVER falls through to the prototype chain, even when it's an
	// invalid index on THIS instance (out of bounds, negative, -0,
	// fractional, or a detached buffer) - unlike an ordinary own-property
	// miss, which would otherwise walk up looking for an inherited
	// property/trap. Reusing getOwnProperty(...) above is exactly right
	// here since it already returns defaultValue for an invalid index.
	@Override
	public Object getProperty(Object _this, String member, Object defaultValue, Object receiver) {
		if(RuntimeUtil.canonicalNumericIndexString(getEnvironment(), member)!=null) {
			return getOwnProperty(_this, member, defaultValue, receiver);
		}
		return super.getProperty(_this, member, defaultValue, receiver);
	}
	@Override
	public Object getProperty(Object _this, long index, Object defaultValue, Object receiver) {
		return getOwnProperty(_this, index, defaultValue, receiver);
	}

	@Override
	public boolean hasProperty(Object _this, String member) {
		Double canonical = RuntimeUtil.canonicalNumericIndexString(getEnvironment(), member);
		if(canonical!=null) {
			return isValidIntegerIndex((TypedArray)_this, canonical);
		}
		return super.hasProperty(_this, member);
	}
	@Override
	public boolean hasProperty(Object _this, long index) {
		return isValidIntegerIndex((TypedArray)_this, index);
	}

	// Spec 10.4.5.3 [[DefineOwnProperty]] (reached only via
	// RuntimeUtil.defineProperty - a plain [[Set]] never reaches here, see
	// the setProperty overrides below, which implement that separate
	// algorithm directly): a valid integer-indexed element is a
	// fixed-shape {writable:true,enumerable:true,configurable:true} data
	// property - any Desc requesting otherwise (non-configurable,
	// non-enumerable, an accessor, or non-writable) is rejected outright,
	// even though the "current" descriptor (PropertyDescriptor.DESC_DEFAULT,
	// per the base getOwnPropertyDescriptor's generic derivation from
	// getOwnProperty above) is nominally configurable.
	private boolean writeIndexed(TypedArray ta, long index, Object value, PropertyDescriptor desc, DESC_CHECK check) {
		if(desc!=null && (!desc.isConfigurable() || !desc.isEnumerable() || desc.isAccessor() || !desc.isWritable())) {
			if(RuntimeUtil.isStrictCheck(check)) {
				throw RuntimeUtil.typeError("Cannot redefine property: {0}", index);
			}
			return false;
		}
		if(value!=RuntimeUtil.NOT_AVAILABLE) {
			integerIndexedElementSet(ta, index, value);
		}
		return true;
	}

	@Override
	public boolean setOwnProperty(Object _this, String member, Object value, PropertyDescriptor desc, DESC_CHECK check, Object receiver) {
		Double canonical = RuntimeUtil.canonicalNumericIndexString(getEnvironment(), member);
		if(canonical!=null) {
			TypedArray ta = (TypedArray) _this;
			if(!isValidIntegerIndex(ta,canonical)) {
				return false;
			}
			return writeIndexed(ta, canonical.longValue(), value, desc, check);
		}
		return super.setOwnProperty(_this, member, value, desc, check, receiver);
	}

	@Override
	public boolean setOwnProperty(Object _this, long index, Object value, PropertyDescriptor desc, DESC_CHECK check, Object receiver) {
		TypedArray ta = (TypedArray) _this;
		if(!isValidIntegerIndex(ta,index)) {
			return false;
		}
		return writeIndexed(ta, index, value, desc, check);
	}

	// Spec 10.4.5.5 [[Set]]: a genuinely different algorithm from
	// [[DefineOwnProperty]] above, not just "the same write with a null
	// Desc" - a canonical numeric key never falls through to the
	// prototype/receiver machinery when `_this` IS the receiver (the
	// common assignment case): TypedArraySetElement always converts the
	// value (ToNumber/ToBigInt, for its side effects) and ALWAYS returns
	// true, even when the index is invalid (out of bounds, negative, -0,
	// fractional, detached) - unlike [[DefineOwnProperty]], which returns
	// false for an invalid index. When the receiver DIFFERS and the index
	// is valid, the write targets the RECEIVER via ordinary [[Set]]
	// semantics (no coercion, no typed-array-specific rule) - delegating
	// to the inherited implementation is exactly right there, since its
	// own getOwnPropertyDescriptor(_this,member) call already sees this
	// index as a normal writable data property (DESC_DEFAULT) and hands
	// the actual write off to the receiver's own accessor.
	@Override
	public boolean setProperty(Object _this, String member, Object value, PropertyDescriptor desc, DESC_CHECK check, Object receiver) {
		Double canonical = RuntimeUtil.canonicalNumericIndexString(getEnvironment(), member);
		if(canonical!=null) {
			TypedArray ta = (TypedArray) _this;
			if(_this==receiver) {
				integerIndexedElementSet(ta, canonical, value);
				return true;
			}
			if(!isValidIntegerIndex(ta,canonical)) {
				return true;
			}
			return super.setProperty(_this, member, value, desc, check, receiver);
		}
		return super.setProperty(_this, member, value, desc, check, receiver);
	}
	@Override
	public boolean setProperty(Object _this, long index, Object value, PropertyDescriptor desc, DESC_CHECK check, Object receiver) {
		TypedArray ta = (TypedArray) _this;
		if(_this==receiver) {
			integerIndexedElementSet(ta, index, value);
			return true;
		}
		if(!isValidIntegerIndex(ta,index)) {
			return true;
		}
		return super.setProperty(_this, index, value, desc, check, receiver);
	}
	private void integerIndexedElementSet(TypedArray ta, double index, Object value) {
		Number converted = RuntimeUtil.toTypedArrayElement(getEnvironment(),ta,value);
		// Re-validate AFTER conversion (a poisoned valueOf can detach/resize
		// the buffer mid-call) - the write is silently dropped, never an
		// error, if that happened.
		if(isValidIntegerIndex(ta,index)) {
			ta.set((long)index,converted);
		}
	}

	// Spec: no custom [[Delete]] override exists for Integer-Indexed exotic
	// objects other than this canonical-numeric-index short-circuit
	// (sec-integer-indexed-exotic-objects-delete-p) - a valid index can
	// never be deleted (returns false, and the `delete` OPERATOR - unlike
	// Reflect.deleteProperty - converts that into a thrown TypeError when
	// the calling code is strict); anything else (invalid index, detached
	// buffer, non-canonical/ordinary key) falls back to the ordinary
	// behavior.
	@Override
	public boolean deleteProperty(Object _this, String member, DESC_CHECK check) {
		Double canonical = RuntimeUtil.canonicalNumericIndexString(getEnvironment(), member);
		if(canonical!=null) {
			return deleteIndexed((TypedArray)_this, canonical, member, check);
		}
		return super.deleteProperty(_this, member, check);
	}
	@Override
	public boolean deleteProperty(Object _this, long index, DESC_CHECK check) {
		return deleteIndexed((TypedArray)_this, index, index, check);
	}
	private boolean deleteIndexed(TypedArray ta, double index, Object member, DESC_CHECK check) {
		if(isValidIntegerIndex(ta,index)) {
			if(RuntimeUtil.isStrictCheck(check)) {
				throw RuntimeUtil.typeError("Cannot delete property '{0}' of object '{1}'", member, RuntimeUtil.objectTypeName(getEnvironment(), ta));
			}
			return false;
		}
		return true;
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Override
	public Iterator<Map.Entry<Object,Object>> ownPropertyEntries(Object _this, boolean strings, boolean symbols, boolean enumerableOnly) {
		TypedArray ta = (TypedArray) _this;
		// Spec 10.4.5.6 [[OwnPropertyKeys]]: a detached buffer contributes NO
		// integer-indexed keys at all (not even attempting to read values).
		boolean hasIndices = strings && !ta.getArrayBuffer().isDetached();
		return (Iterator)Iterators.<Map.Entry<Object, Object>>concat(
			hasIndices ? Iterators.<Map.Entry<Object, Object>>map(Iterators.longSequence(0,ta.getLength()), (v) -> {
				return newEntry(Long.toString(v),ta.get(v));
			}) : null,
			(Iterator)super.ownPropertyEntries(_this, strings, symbols, enumerableOnly)
		);
	}
}
