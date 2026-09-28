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
package org.monflabs.galtajs.rt.builtins.primitives.string;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.StaticConfiguration;
import org.monflabs.galtajs.jsonfactory.JSObject;
import org.monflabs.galtajs.jsonfactory.JSObject.DESC_CHECK;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.builtins.PropertyDescriptor;
import org.monflabs.galtajs.rt.builtins.primitives.BasePrimitiveAccessor;
import org.monflabs.galtajs.rt.util.strings.CharWrapper;
import org.monflabs.util.iterators.Iterators;

/**
 * 
 */
public class StringAccessor extends BasePrimitiveAccessor {

	// boxed Strings (a ConsString is never boxable)
	@Override
	protected org.monflabs.galtajs.rt.util.PrimitivePropertyMap propertyMap() {
		return getEnvironment().getStringProperties();
	}
	
	public StringAccessor(JSEnvironment env) {
		super(env);
	}

	@Override
	protected Object getDefaultPrototype() {
		return BuiltinStringPrototype.get(getEnvironment());
	}

	@Override
	public String getClassName(Object _this) {
		return BuiltinStringConstructor.CLASSNAME;
	}	
	
	// Spec StringGetOwnProperty (10.4.3.5): a String exotic object's own
	// character properties are non-writable, non-configurable data
	// properties (only "length" and the characters within bounds are
	// special-cased at all - anything else is an ordinary property).
	private static final PropertyDescriptor DESC_STRING_INDEX = PropertyDescriptor.DESC_READONLY_PROP;

	@Override
	public PropertyDescriptor getOwnPropertyDescriptor(Object _this, String member) {
		if("length".equals(member)) {
			return PropertyDescriptor.DESC_READONLY_HIDDEN_PROP;
		}
		long index = RuntimeUtil.memberIndex(member);
		if(index!=Long.MIN_VALUE) {
			return getOwnPropertyDescriptor(_this, index);
		}
		return super.getOwnPropertyDescriptor(_this,member);
	}
	@Override
	public PropertyDescriptor getOwnPropertyDescriptor(Object _this, long index) {
		// .length() alone (no character value needed here) - a CharSequence
		// cast avoids forcing a full flatten of a ConsString just to bounds-
		// check an index (its length is already a cached field).
		if(index>=0 && index<((CharSequence)_this).length()) {
			return DESC_STRING_INDEX;
		}
		return super.getOwnPropertyDescriptor(_this, index);
	}

	// Spec 10.4.3.2 [[DefineOwnProperty]]: an in-bounds character index only
	// accepts a redefinition that's compatible with its fixed, non-writable,
	// non-configurable descriptor+value (in practice, a genuine no-op) -
	// everything else (out-of-bounds numeric keys, non-canonical numeric-
	// looking keys, non-numeric keys) is an ordinary property, handled by
	// falling through to the inherited behavior unchanged.
	@Override
	public boolean setOwnProperty(Object _this, String member, Object value, PropertyDescriptor desc, DESC_CHECK check, Object receiver) {
		long index = RuntimeUtil.memberIndex(member);
		if(index!=Long.MIN_VALUE) {
			return setOwnProperty(_this, index, value, desc, check, receiver);
		}
		return super.setOwnProperty(_this, member, value, desc, check, receiver);
	}
	@Override
	public boolean setOwnProperty(Object _this, long index, Object value, PropertyDescriptor desc, DESC_CHECK check, Object receiver) {
		CharSequence s = (CharSequence)_this;
		if(index>=0 && index<s.length()) {
			// charAt(), not substring(1) - a single-character read, cheap on
			// a ConsString (see its own doc comment); substring() would force
			// a full flatten (ConsString.subSequence() always flattens).
			String charValue = String.valueOf(s.charAt((int)index));
			PropertyDescriptor next = desc!=null ? desc : DESC_STRING_INDEX;
			Object nextValue = value!=RuntimeUtil.NOT_AVAILABLE ? value : RuntimeUtil.NOT_AVAILABLE;
			boolean compatible = RuntimeUtil.isCompatiblePropertyDescriptor(getEnvironment(), isExtensible(_this), DESC_STRING_INDEX, () -> charValue, next, nextValue);
			if(!compatible && RuntimeUtil.isStrictCheck(check)) {
				throw RuntimeUtil.typeError("Cannot redefine property: {0}", index);
			}
			return compatible;
		}
		return super.setOwnProperty(_this, index, value, desc, check, receiver);
	}

	@Override
	public JSObject getOwnPropertyDescriptors(JSObject descriptors, Object _this) {
		// The base JSAccessor.getOwnPropertyDescriptors is a no-op stub -
		// unlike ObjectAccessor (which delegates to a real JSObject's own
		// getOwnPropertyDescriptors), a raw String isn't a JSObject, so
		// each per-character index descriptor must be populated directly
		// here, matching ownPropertyEntries' own enumeration (confirmed
		// via getOwnPropertyDescriptors/primitive-strings.js: a 3-char
		// string must report exactly 4 descriptors - "0"/"1"/"2"/"length" -
		// this previously reported only "length", having passed `this`
		// (the accessor object itself) instead of `_this` to the no-op
		// super call).
		CharSequence s = (CharSequence)_this;
		for(int i=0; i<s.length(); i++) {
			descriptors.setOwnProperty(Integer.toString(i),PropertyDescriptor.DESC_READONLY_PROP);
		}
		descriptors.setOwnProperty("length",PropertyDescriptor.DESC_READONLY_HIDDEN_PROP);
		// A boxed String (`new String(...)`) can carry extra own properties
		// beyond its intrinsic characters/length, stored via
		// ObjectWrapperAccessor's own properties side-object - must also be
		// reported here (confirmed via isFrozen/15.2.3.12-2-a-12.js: a
		// String object with a manually-added writable/configurable "len"
		// property must not read back as frozen once every OTHER own
		// property happens to be non-writable/non-configurable).
		super.getOwnPropertyDescriptors(descriptors, _this);
		return descriptors;
	}

	// Spec key ordering (10.4.3.11 StringExoticObjectOwnPropertyKeys / the
	// general OrdinaryOwnPropertyKeys shape it follows): (1) the string's own
	// character indices, ascending; (2) any EXTRA own property whose key is
	// itself an array index (>= the string's length), in ascending NUMERIC
	// order - grouped with the character indices, not with "length" (confirmed
	// via built-ins/Object/getOwnPropertyNames/15.2.3.4-4-44.js: `str[5]="de"`
	// on a 3-char string must report ["0","1","2","5","length"], "5" BEFORE
	// "length"); (3) "length" and every other non-index string key, in
	// ascending chronological order of creation - "length" is always created
	// at construction time, before any extra property user code adds
	// afterward, so it always sorts first in this bucket (confirmed via
	// built-ins/Reflect/ownKeys/order-after-define-property.js: later-added
	// "a"/"b" own properties must report ["length","a","b"]); (4) ALL symbol
	// keys, unconditionally after every string key. Symbol-keyed "extra" own
	// properties must therefore never be interleaved before "length" - fetch
	// the string-only and symbol-only members SEPARATELY so symbols always
	// land after "length", not wherever the shared super-call happened to
	// return them relative to it.
	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Override
	public Iterator<Map.Entry<Object,Object>> ownPropertyEntries(Object _this, boolean strings, boolean symbols, boolean enumerableOnly) {
		Iterator<Map.Entry<Object,Object>> stringPart = Iterators.empty();
		if(strings) {
			// charAt() below already avoids forcing a flatten per-index;
			// only .length() is needed up front, so avoid it here too.
			CharSequence s = (CharSequence)_this;
			List<Map.Entry<Object,Object>> extraIndexMembers = new ArrayList<>();
			List<Map.Entry<Object,Object>> extraStringMembers = new ArrayList<>();
			for(var it = super.ownPropertyEntries(_this,true,false,enumerableOnly); it.hasNext(); ) {
				Map.Entry<Object,Object> e = it.next();
				if(RuntimeUtil.isMemberIndex((String)e.getKey())) {
					extraIndexMembers.add(e);
				} else {
					extraStringMembers.add(e);
				}
			}
			extraIndexMembers.sort(Comparator.comparingLong(e -> RuntimeUtil.memberIndex((String)e.getKey())));
			stringPart = (Iterator)Iterators.<Map.Entry<Object, Object>>concat(
				Iterators.<Map.Entry<Object, Object>>map(Iterators.intSequence(0,s.length()), (v) -> {
					if(StaticConfiguration.ENABLE_CONSSTRING) {
						return newEntry(Integer.toString(v),new String(new char[] {s.charAt(v)}));
					} else {
						return newEntry(Integer.toString(v),new CharWrapper(s.charAt(v)));
					}
				}),
				(Iterator)extraIndexMembers.iterator(),
				!enumerableOnly ? Iterators.single(newEntry("length",s.length())) : null,
				(Iterator)extraStringMembers.iterator()
			);
		}
		Iterator<Map.Entry<Object,Object>> symbolPart = symbols ? super.ownPropertyEntries(_this,false,true,enumerableOnly) : Iterators.empty();
		return Iterators.concat(stringPart, symbolPart);
	}

	@Override
	public Object getOwnProperty(Object _this, String member,  Object defaultValue, Object receiver) {
		if("length".equals(member)) {
			return ((CharSequence)_this).length();
		}
		long index = RuntimeUtil.memberIndex(member);
		if(index!=Long.MIN_VALUE) {
            return getOwnProperty(_this,index,defaultValue,_this);
		}
		return super.getOwnProperty(_this, member,defaultValue,receiver);
	}

	@Override
	public Object getOwnProperty(Object _this, long index, Object defaultValue, Object receiver) {
		CharSequence s = (CharSequence)_this;
		if(index>=0 && index<s.length()) {
			// charAt(), not substring(1) - see setOwnProperty()'s own doc
			// comment on why: a single-character read is cheap on a
			// ConsString, a substring() call is not.
			return String.valueOf(s.charAt((int)index));
		}
		return super.getOwnProperty(_this,index,defaultValue,receiver);
	}
	
	@Override
	public boolean deleteProperty(Object _this, String member, DESC_CHECK check) {
		if("length".equals(member)) {
			if(RuntimeUtil.isStrictCheck(check)) {
				throw RuntimeUtil.typeError("Cannot delete property '{0}' of object '{1}'", member, RuntimeUtil.objectTypeName(getEnvironment(), _this));
			}
			return false;
		}
		return super.deleteProperty(_this,member,check);
	}
	@Override
	public boolean deleteProperty(Object _this, long index, DESC_CHECK check) {
		// Can't delete a character, strings are immutable
		CharSequence s = (CharSequence)_this;
		if(index>=0 && index<s.length()) {
			// isStrictCheck(check), not getEnvironment().isStrictMode() (a
			// static environment-wide config flag, not the current
			// execution context's actual strict-mode-ness) - was never
			// throwing regardless of the calling code's "use strict".
			if(RuntimeUtil.isStrictCheck(check)) {
				throw RuntimeUtil.typeError("Cannot delete property '{0}' of object '{1}'", RuntimeUtil.objectTypeName(getEnvironment(), _this));
			}
			return false;
		}
		return true;
	}
}
