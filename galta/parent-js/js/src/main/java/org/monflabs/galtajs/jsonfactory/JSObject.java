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
package org.monflabs.galtajs.jsonfactory;

import java.util.Iterator;
import java.util.Map;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.jsonfactory.internal.ObjectAccessorWrapper;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.builtins.BaseGetter;
import org.monflabs.galtajs.rt.builtins.BaseMethod;
import org.monflabs.galtajs.rt.builtins.BaseSetter;
import org.monflabs.galtajs.rt.builtins.JSAccessor;
import org.monflabs.galtajs.rt.builtins.PropertyDescriptor;
import org.monflabs.galtajs.rt.builtins.primitives.object.BuiltinObject;
import org.monflabs.galtajs.rt.builtins.primitives.symbol.Symbol;
import org.monflabs.json.JsonObject;
import org.monflabs.util.iterators.Iterators;

public interface JSObject {
	
	// Indicate how the methods should work with the descriptor settings
	// Descriptor are generally not checked unless used from Script code.
	public static enum DESC_CHECK {
		NONE,			// Don't check, update 
		CHECK,			// Check the update rights
		STRICT,			// Strict check (throw exception)
		NO_EXCEPTION,	// No exception, like sloppy mode (!= strict)  
	}

	public static JSObjectImpl create(JSEnvironment env) {
		return new BuiltinObject(env);
	}
	public static JSObjectImpl createWithPrototype(JSEnvironment env, Object prototype) {
		if(RuntimeUtil.isNullOrUndefined(prototype)) {
			return new BuiltinObject(env,prototype);
		}
		if(RuntimeUtil.isPrimitiveType(prototype)) {
			// Not sure that this test is actually needed.
			if(!RuntimeUtil.isValidPrototype(JSEnvironment.getEnvironment(),prototype)) {
				throw RuntimeUtil.typeError("Function has non-object prototype '{0}' in instanceof check",prototype);
			}
		}
		return new BuiltinObject(env,prototype);
	}
	public static JSObjectImpl of(JSEnvironment env, Object...values) {
		BuiltinObject o = new BuiltinObject(env);
		for(int i=0; i<values.length; i+=2) {
			String key = (String)values[i];
			Object value = (i+1)<values.length ? values[i+1] : null;
			o.putValue(key, value);
		}
		return o;
	}
	public static JSObjectImpl of(JSEnvironment env, String k1, Object v1) {
		BuiltinObject o = new BuiltinObject(env);
		o.putValue(k1, v1);
		return o;
	}
	public static JSObjectImpl of(JSEnvironment env, String k1, Object v1, String k2, Object v2) {
		BuiltinObject o = new BuiltinObject(env);
		o.putValue(k1, v1);
		o.putValue(k2, v2);
		return o;
	}
	public static JSObjectImpl of(JSEnvironment env, String k1, Object v1, String k2, Object v2, String k3, Object v3) {
		BuiltinObject o = new BuiltinObject(env);
		o.putValue(k1, v1);
		o.putValue(k2, v2);
		o.putValue(k3, v3);
		return o;
	}
	
	public static JSObject from(JSEnvironment env, Object value) {
		if(value instanceof JSObject jo) {
			return jo;
		}
		return new ObjectAccessorWrapper(env.getAccessor(value), value);
	}
	
	public JSEnvironment getEnvironment();
	
	public default String getClassName() {
		return "Object";
	}

	
	//
	// Object prototype
	//
	
	public default Object getPrototype() {
		return null;
	}
	public default boolean setPrototype(Object prototype) {
		return false;
	}
	
	
	//
	// Object access rights
	//
	
	public default boolean isExtensible() {
		return true;
	}
	public default boolean isSealed() {
		return false;
	}
	public default boolean isFrozen() {
		return false;
	}

	public default boolean preventExtensions() {
		return false;
	}
	public default void seal() {
	}
	public default void freeze() {
	}

	
	
	// -----------------------------------------------------------------------------------
	//
	// [[HasProperty]]
	//
	// -----------------------------------------------------------------------------------
	
	// Spec OrdinaryHasProperty: an object with no own property delegates to
	// its prototype's OWN [[HasProperty]] (not [[GetOwnProperty]]) - a
	// single recursive step, not a manual own-property-only walk up the
	// chain. This matters whenever some ancestor is a Proxy (or any other
	// exotic object with its own [[HasProperty]] override): delegating via
	// [[GetOwnProperty]] instead would silently skip that ancestor's `has`
	// trap and consult its raw own-property state directly.
	public default boolean hasProperty(String key) {
		if(hasOwnProperty(key)) {
			return true;
		}
		Object p=getPrototype();
		if(p==null) {
			return false;
		}
		if(p instanceof JSObject jo) { // Optimization
			return jo.hasProperty(key);
		}
		return getEnvironment().getAccessor(p).hasProperty(p,key);
	}
	public default boolean hasProperty(long index) {
		if(hasOwnProperty(index)) {
			return true;
		}
		Object p=getPrototype();
		if(p==null) {
			return false;
		}
		if(p instanceof JSObject jo) { // Optimization
			return jo.hasProperty(index);
		}
		return getEnvironment().getAccessor(p).hasProperty(p,index);
	}
	public default boolean hasProperty(Symbol symbol) {
		if(hasOwnProperty(symbol)) {
			return true;
		}
		Object p=getPrototype();
		if(p==null) {
			return false;
		}
		if(p instanceof JSObject jo) { // Optimization
			return jo.hasProperty(symbol);
		}
		return getEnvironment().getAccessor(p).hasProperty(p,symbol);
	}
	public default /*final*/ boolean hasProperty(Object member) {
		if(member instanceof String s) {
			return hasProperty(s);
		} else if(member instanceof Number l) {
			long idx = RuntimeUtil.numberAsMemberIndex(l);
			if(idx>=0) {
				return hasProperty(idx);
			}
		} else if(member instanceof Symbol sym) {
			return hasProperty(sym);
		}
		return hasProperty(RuntimeUtil.toString(getEnvironment(),member));
	}


	// Extra
	public default boolean hasOwnProperty(String member) {
		return getOwnPropertyDescriptor(member)!=null;
	}
	public default boolean hasOwnProperty(long index) {
		return getOwnPropertyDescriptor(index)!=null;
	}
	public default boolean hasOwnProperty(Symbol symbol) {
		return getOwnPropertyDescriptor(symbol)!=null;
	}
	public default boolean hasOwnProperty(Object member) {
		if(member instanceof String s) {
			return hasOwnProperty(s);
		} else if(member instanceof Number l) {
			long idx = RuntimeUtil.numberAsMemberIndex(l);
			if(idx>=0) {
				return hasOwnProperty(idx);
			}
		} else if(member instanceof Symbol sym) {
			return hasOwnProperty(sym);
		}
		JSEnvironment env = getEnvironment();
		return hasOwnProperty(RuntimeUtil.toString(env,member));
	}
	
	
	
	// -----------------------------------------------------------------------------------
	//
	// [[GetOwnProperty]]
	//
	// -----------------------------------------------------------------------------------

	public PropertyDescriptor getOwnPropertyDescriptor(String member);
	public default PropertyDescriptor getOwnPropertyDescriptor(long index) {
		return getOwnPropertyDescriptor(RuntimeUtil.memberIndex(index));
	}
	public PropertyDescriptor getOwnPropertyDescriptor(Symbol symbol);
	public default /*final*/ PropertyDescriptor getOwnPropertyDescriptor(Object member) {
		if(member instanceof String s) {
			return getOwnPropertyDescriptor(s);
		} else if(member instanceof Number l) {
			long idx = RuntimeUtil.numberAsMemberIndex(l);
			if(idx>=0) {
				return getOwnPropertyDescriptor(idx);
			}
		} else if(member instanceof Symbol sym) {
			return getOwnPropertyDescriptor(sym);
		}
		return getOwnPropertyDescriptor(RuntimeUtil.toString(getEnvironment(),member));
	}
	
	public JSObject getOwnPropertyDescriptors(JSObject descriptors);
	

	public default Object getOwnProperty(String key) {
		return getOwnProperty(key, RuntimeUtil.UNDEFINED, this);
	}
	public default Object getOwnProperty(long index) {
		return getOwnProperty(index, RuntimeUtil.UNDEFINED, this);
	}
	public default Object getOwnProperty(Symbol symbol) {
		return getOwnProperty(symbol, RuntimeUtil.UNDEFINED, this);
	}
	public default Object getOwnProperty(Object member) {
		return getOwnProperty(member, RuntimeUtil.UNDEFINED, this);
	}

	public default Object getOwnProperty(String key, Object defaultValue) {
		return getOwnProperty(key, defaultValue, this);
	}
	public default Object getOwnProperty(long index, Object defaultValue) {
		return getOwnProperty(index, defaultValue, this);
	}
	public default Object getOwnProperty(Symbol symbol, Object defaultValue) {
		return getOwnProperty(symbol, defaultValue, this);
	}
	public default Object getOwnProperty(Object member, Object defaultValue) {
		return getOwnProperty(member, defaultValue, this);
	}

	public Object getOwnProperty(String key, Object defaultValue, Object receiver);
	public default Object getOwnProperty(long index, Object defaultValue, Object receiver) {
		return getOwnProperty(RuntimeUtil.memberIndex(index),defaultValue, receiver);
	}
	public default Object getOwnProperty(Symbol key, Object defaultValue, Object receiver) {
		return defaultValue;
	}
	public default /*final*/ Object getOwnProperty(Object member, Object defaultValue, Object receiver) {
		if(member instanceof String s) {
			return getOwnProperty(s,defaultValue,receiver);
		} else if(member instanceof Number l) {
			long idx = RuntimeUtil.numberAsMemberIndex(l);
			if(idx>=0) {
				return getOwnProperty(idx,defaultValue,receiver);
			}
		} else if(member instanceof Symbol sym) {
			return getOwnProperty(sym,defaultValue,receiver);
		}
		return getOwnProperty(RuntimeUtil.toString(getEnvironment(),member),defaultValue,receiver);
	}
	

	
	// -----------------------------------------------------------------------------------
	//
	// [[Get]]
	//
	// -----------------------------------------------------------------------------------
	
	public default Object getProperty(String key) {
		return getProperty(key,RuntimeUtil.UNDEFINED);
	}
	public default Object getProperty(long index) {
		return getProperty(index,RuntimeUtil.UNDEFINED);
	}
	public default Object getProperty(Symbol symbol) {
		return getProperty(symbol,RuntimeUtil.UNDEFINED);
	}
	public default /*final*/ Object getProperty(Object member) {
		return getProperty(member,RuntimeUtil.UNDEFINED);
	}
	
	public default Object getProperty(String key, Object defaultValue) {
		return getProperty(key, defaultValue, this);
	}
	public default Object getProperty(long index, Object defaultValue) {
		return getProperty(index, defaultValue, this);
	}
	public default Object getProperty(Symbol key, Object defaultValue) {
		return getProperty(key, defaultValue, this);
	}
	public default /*final*/ Object getProperty(Object member, Object defaultValue) {
		return getProperty(member, defaultValue, this);
	}

	// Receiver-aware [[Get]]: see JSAccessor.getProperty(...,receiver) for why the
	// search-start object and the accessor `this` binding can differ (SuperProperty).
	public default Object getProperty(String key, Object defaultValue, Object receiver) {
		Object v = getOwnProperty(key, RuntimeUtil.NOT_AVAILABLE, receiver);
		if(v!=RuntimeUtil.NOT_AVAILABLE) {
			return v;
		}
		Object p=getPrototype();
		while(p!=null) {
			if(p instanceof JSObject jo) { // Optimization
				v = jo.getOwnProperty(key, RuntimeUtil.NOT_AVAILABLE, receiver);
				if(v!=RuntimeUtil.NOT_AVAILABLE) {
					return v;
				}
				p = jo.getPrototype();
			} else {
				JSAccessor a = getEnvironment().getAccessor(p);
				v = a.getOwnProperty(p, key, RuntimeUtil.NOT_AVAILABLE, receiver);
				if(v!=RuntimeUtil.NOT_AVAILABLE) {
					return v;
				}
				p = a.getPrototype(p);
			}
		}
		return defaultValue;
	}
	public default Object getProperty(long index, Object defaultValue, Object receiver) {
		Object v = getOwnProperty(index, RuntimeUtil.NOT_AVAILABLE, receiver);
		if(v!=RuntimeUtil.NOT_AVAILABLE) {
			return v;
		}
		Object p=getPrototype();
		while(p!=null) {
			if(p instanceof JSObject jo) { // Optimization
				v = jo.getOwnProperty(index, RuntimeUtil.NOT_AVAILABLE, receiver);
				if(v!=RuntimeUtil.NOT_AVAILABLE) {
					return v;
				}
				p = jo.getPrototype();
			} else {
				JSAccessor a = getEnvironment().getAccessor(p);
				v = a.getOwnProperty(p, index, RuntimeUtil.NOT_AVAILABLE, receiver);
				if(v!=RuntimeUtil.NOT_AVAILABLE) {
					return v;
				}
				p = a.getPrototype(p);
			}
		}
		return defaultValue;
	}
	public default Object getProperty(Symbol key, Object defaultValue, Object receiver) {
		Object v = getOwnProperty(key, RuntimeUtil.NOT_AVAILABLE, receiver);
		if(v!=RuntimeUtil.NOT_AVAILABLE) {
			return v;
		}
		Object p=getPrototype();
		while(p!=null) {
			if(p instanceof JSObject jo) { // Optimization
				v = jo.getOwnProperty(key, RuntimeUtil.NOT_AVAILABLE, receiver);
				if(v!=RuntimeUtil.NOT_AVAILABLE) {
					return v;
				}
				p = jo.getPrototype();
			} else {
				JSAccessor a = getEnvironment().getAccessor(p);
				v = a.getOwnProperty(p, key, RuntimeUtil.NOT_AVAILABLE, receiver);
				if(v!=RuntimeUtil.NOT_AVAILABLE) {
					return v;
				}
				p = a.getPrototype(p);
			}
		}
		return defaultValue;
	}

	public default /*final*/ Object getProperty(Object member, Object defaultValue, Object receiver) {
		// A BOXED String/Number/Symbol (e.g. `obj[Object(Symbol())]`) must go through
		// ToPropertyKey (ToPrimitive first) rather than being used as-is just because
		// its underlying Java type matches - otherwise an overridden/removed
		// Symbol.prototype[Symbol.toPrimitive] on the wrapper is never consulted.
		if(member instanceof String || member instanceof Number || member instanceof Symbol) {
			JSEnvironment env0 = getEnvironment();
			if(RuntimeUtil.hasPropertyMap(env0, member)) {
				member = RuntimeUtil.toPropertyKey(env0, member);
			}
		}
		if(member instanceof String s) {
			return getProperty(s, defaultValue, receiver);
		} else if(member instanceof Number l) {
			long idx = RuntimeUtil.numberAsMemberIndex(l);
			if(idx>=0) {
				return getProperty(idx, defaultValue, receiver);
			}
		} else if(member instanceof Symbol sym) {
			return getProperty(sym, defaultValue, receiver);
		}
		return getProperty(RuntimeUtil.toString(getEnvironment(),member), defaultValue, receiver);
	}



	// -----------------------------------------------------------------------------------
	//
	// [[Set]]
	//
	// -----------------------------------------------------------------------------------
	
	public default boolean setProperty(String key, Object value) {
		return setProperty(key,value,null,DESC_CHECK.CHECK);
	}
	public default boolean setProperty(long index, Object value) {
		return setProperty(index,value,null,DESC_CHECK.CHECK);
	}
	public default boolean setProperty(Symbol key, Object value) {
		return setProperty(key,value,null,DESC_CHECK.CHECK);
	}
	public default /*final*/ boolean setProperty(Object key, Object value) {
		return setProperty(key,value,null,DESC_CHECK.CHECK);
	}

	public default boolean setProperty(String key, Object value, PropertyDescriptor desc) {
		return setProperty(key,value,desc,DESC_CHECK.CHECK);
	}
	public default boolean setProperty(long index, Object value, PropertyDescriptor desc) {
		return setProperty(index,value,desc,DESC_CHECK.CHECK);
	}
	public default boolean setProperty(Symbol key, Object value, PropertyDescriptor desc) {
		return setProperty(key,value,desc,DESC_CHECK.CHECK);
	}
	public default /*final*/ boolean setProperty(Object key, Object value, PropertyDescriptor desc) {
		return setProperty(key,value,desc,DESC_CHECK.CHECK);
	}

	
	// Spec OrdinarySet / OrdinarySetWithOwnDescriptor - see
	// JSAccessor.setProperty(...,receiver) for the full rationale (mirrored
	// here exactly, just dispatching through the JSObject/JSAccessor pair
	// instead of JSAccessor alone): when this level has no own descriptor,
	// delegate the WHOLE [[Set]] to the prototype's own [[Set]] (not just
	// its [[GetOwnProperty]]) so a Proxy ancestor's `set` trap fires
	// correctly, with the ORIGINAL receiver preserved through the
	// recursion. Once an own descriptor IS found, an accessor's setter is
	// invoked at the level it was found (bound to `receiver`, via
	// setOwnProperty's own setter-detection); a writable data descriptor -
	// own OR inherited - always creates/overwrites the property on
	// `receiver`, never mutates the ancestor it was found on. This single
	// shape also correctly handles the SuperProperty (`receiver != this`)
	// case with no separate implementation needed.
	public default boolean setProperty(String member, Object value, PropertyDescriptor desc, DESC_CHECK check, Object receiver) {
		PropertyDescriptor pd = getOwnPropertyDescriptor(member);
		if(pd!=null) {
			if(pd.getSetter()!=null) {
				// A plain [[Set]] invoking an EXISTING accessor's setter is
				// not a [[DefineOwnProperty]] request to change the
				// property's shape - passing the caller's data-shaped
				// `desc` template through here made putEntry() (via
				// CustomLinkedMap) treat `descriptor.isData()==true` as
				// license to silently overwrite the accessor with a plain
				// value instead of invoking its setter (confirmed via
				// push/set-length-array-is-frozen.js: pushing into an
				// array whose prototype has an index accessor must invoke
				// that setter, not clobber it). `null` here means "don't
				// touch the shape, use whatever setOwnProperty finds".
				return setOwnProperty(member, value, null, check, receiver);
			}
			if(pd.getGetter()!=null || !pd.isWritable()) {
				return rejectSet(check, member);
			}
		} else {
			Object p = getPrototype();
			if(p!=null) {
				if(p instanceof JSObject jo) {
					return jo.setProperty(member, value, desc, check, receiver);
				}
				return getEnvironment().getAccessor(p).setProperty(p, member, value, desc, check, receiver);
			}
		}
		// See JSAccessor.setProperty(...,receiver) for the full rationale on
		// this final step: a non-object Receiver must fail here (spec's
		// OrdinarySetWithOwnDescriptor data-descriptor branch), BEFORE the
		// RECEIVER's OWN existing descriptor is consulted independently (an
		// observable step, e.g. a Proxy receiver's own
		// getOwnPropertyDescriptor trap) - reusing `pd` when receiver==this
		// avoids a redundant duplicate lookup on the hot, common path.
		if(!RuntimeUtil.isObject(getEnvironment(), receiver)) {
			return rejectSet(check, member);
		}
		PropertyDescriptor existing = (receiver==this) ? pd
				: (receiver instanceof JSObject jo ? jo.getOwnPropertyDescriptor(member) : getEnvironment().getAccessor(receiver).getOwnPropertyDescriptor(receiver, member));
		if(existing!=null && (existing.getGetter()!=null || existing.getSetter()!=null || !existing.isWritable())) {
			return rejectSet(check, member);
		}
		if(receiver instanceof JSObject jo) {
			return jo.setOwnProperty(member, value, desc, check, receiver);
		}
		// Spec OrdinarySetWithOwnDescriptor 2d-2e: a non-JSObject receiver
		// (e.g. a Proxy) has no other way to recover `existing`'s
		// attributes itself without a duplicate getOwnPropertyDescriptor
		// trap dispatch, so - unlike the JSObject branch above, where a
		// null desc means "preserve shape as-is" to its own setOwnProperty -
		// they must be threaded through explicitly: a value-only merge
		// (existing's own writable/configurable/enumerable, untouched)
		// when the receiver already owns this property, else full
		// CreateDataProperty defaults when it doesn't (confirmed via
		// ProxyHandlerTest.js's passthrough push() case: re-deriving a
		// blanket "full true/true/true" default for a null desc made a
		// non-configurable/non-enumerable "length" look configurable to
		// the 'defineProperty' trap, which our own length setter then
		// legitimately rejected as a redefinition).
		PropertyDescriptor putDesc = desc!=null ? desc
				: (existing!=null ? PropertyDescriptor.of(existing.isWritable(), existing.isConfigurable(), existing.isEnumerable()) : PropertyDescriptor.DESC_DEFAULT);
		return getEnvironment().getAccessor(receiver).setOwnProperty(receiver, member, value, putDesc, check, receiver);
	}
	public default boolean setProperty(long index, Object value, PropertyDescriptor desc, DESC_CHECK check, Object receiver) {
		PropertyDescriptor pd = getOwnPropertyDescriptor(index);
		if(pd!=null) {
			if(pd.getSetter()!=null) {
				// See the matching String-keyed overload's comment above.
				return setOwnProperty(index, value, null, check, receiver);
			}
			if(pd.getGetter()!=null || !pd.isWritable()) {
				return rejectSet(check, index);
			}
		} else {
			Object p = getPrototype();
			// Optimize past prototypes known to never have a numeric setter -
			// avoids a wasted getOwnPropertyDescriptor call per level for the
			// very common numeric-index-write case (array pushes, etc).
			while(p instanceof JSObjectImpl bp && !bp.mayHaveNumberPropSetter()) {
				p = bp.getPrototype();
			}
			if(p!=null) {
				if(p instanceof JSObject jo) {
					return jo.setProperty(index, value, desc, check, receiver);
				}
				return getEnvironment().getAccessor(p).setProperty(p, index, value, desc, check, receiver);
			}
		}
		if(!RuntimeUtil.isObject(getEnvironment(), receiver)) {
			return rejectSet(check, index);
		}
		PropertyDescriptor existing = (receiver==this) ? pd
				: (receiver instanceof JSObject jo ? jo.getOwnPropertyDescriptor(index) : getEnvironment().getAccessor(receiver).getOwnPropertyDescriptor(receiver, index));
		if(existing!=null && (existing.getGetter()!=null || existing.getSetter()!=null || !existing.isWritable())) {
			return rejectSet(check, index);
		}
		if(receiver instanceof JSObject jo) {
			return jo.setOwnProperty(index, value, desc, check, receiver);
		}
		// See setProperty(String,...)'s matching comment above.
		PropertyDescriptor putDesc = desc!=null ? desc
				: (existing!=null ? PropertyDescriptor.of(existing.isWritable(), existing.isConfigurable(), existing.isEnumerable()) : PropertyDescriptor.DESC_DEFAULT);
		return getEnvironment().getAccessor(receiver).setOwnProperty(receiver, index, value, putDesc, check, receiver);
	}
	public default boolean setProperty(Symbol symbol, Object value, PropertyDescriptor desc, DESC_CHECK check, Object receiver) {
		PropertyDescriptor pd = getOwnPropertyDescriptor(symbol);
		if(pd!=null) {
			if(pd.getSetter()!=null) {
				// See setProperty(String,...)'s matching comment above.
				return setOwnProperty(symbol, value, null, check, receiver);
			}
			if(pd.getGetter()!=null || !pd.isWritable()) {
				return rejectSet(check, symbol);
			}
		} else {
			Object p = getPrototype();
			if(p!=null) {
				if(p instanceof JSObject jo) {
					return jo.setProperty(symbol, value, desc, check, receiver);
				}
				return getEnvironment().getAccessor(p).setProperty(p, symbol, value, desc, check, receiver);
			}
		}
		if(!RuntimeUtil.isObject(getEnvironment(), receiver)) {
			return rejectSet(check, symbol);
		}
		PropertyDescriptor existing = (receiver==this) ? pd
				: (receiver instanceof JSObject jo ? jo.getOwnPropertyDescriptor(symbol) : getEnvironment().getAccessor(receiver).getOwnPropertyDescriptor(receiver, symbol));
		if(existing!=null && (existing.getGetter()!=null || existing.getSetter()!=null || !existing.isWritable())) {
			return rejectSet(check, symbol);
		}
		if(receiver instanceof JSObject jo) {
			return jo.setOwnProperty(symbol, value, desc, check, receiver);
		}
		// See setProperty(String,...)'s matching comment above.
		PropertyDescriptor putDesc = desc!=null ? desc
				: (existing!=null ? PropertyDescriptor.of(existing.isWritable(), existing.isConfigurable(), existing.isEnumerable()) : PropertyDescriptor.DESC_DEFAULT);
		return getEnvironment().getAccessor(receiver).setOwnProperty(receiver, symbol, value, putDesc, check, receiver);
	}
	private boolean rejectSet(DESC_CHECK check, Object member) {
		if(RuntimeUtil.isStrictCheck(check)) {
			throw RuntimeUtil.typeError("Property {0} is not writable", member);
		}
		return false;
	}
	public default /*final*/ boolean setProperty(Object member, Object value, PropertyDescriptor descriptor, DESC_CHECK check, Object receiver) {
		if(member instanceof String s) {
			return setProperty(s, value, descriptor, check, receiver);
		} else if(member instanceof Number l) {
			long idx = RuntimeUtil.numberAsMemberIndex(l);
			if(idx>=0) {
				return setProperty(idx, value, descriptor, check, receiver);
			}
		} else if(member instanceof Symbol sym) {
			return setProperty(sym, value, descriptor, check, receiver);
		}
		return setProperty(RuntimeUtil.toString(getEnvironment(),member), value, descriptor, check, receiver);
	}

	// No-receiver convenience forms: receiver defaults to `this`.
	public default boolean setProperty(String member, Object value, PropertyDescriptor desc, DESC_CHECK check) {
		return setProperty(member, value, desc, check, this);
	}
	public default boolean setProperty(long index, Object value, PropertyDescriptor desc, DESC_CHECK check) {
		return setProperty(index, value, desc, check, this);
	}
	public default boolean setProperty(Symbol symbol, Object value, PropertyDescriptor desc, DESC_CHECK check) {
		return setProperty(symbol, value, desc, check, this);
	}
	public default /*final*/ boolean setProperty(Object member, Object value, PropertyDescriptor descriptor, DESC_CHECK check) {
		return setProperty(member, value, descriptor, check, this);
	}

	
	
	public default boolean setOwnProperty(String key, Object value) {
		return setOwnProperty(key,value,null,DESC_CHECK.CHECK, this);
	}
	public default boolean setOwnProperty(long index, Object value) {
		return setOwnProperty(index,value,null,DESC_CHECK.CHECK, this);
	}
	public default boolean setOwnProperty(Symbol key, Object value) {
		return setOwnProperty(key,value,null,DESC_CHECK.CHECK, this);
	}
	public default /*final*/ boolean setOwnProperty(Object key, Object value) {
		return setOwnProperty(key,value,null,DESC_CHECK.CHECK, this);
	}

	public default boolean setOwnProperty(String key, Object value, PropertyDescriptor desc) {
		return setOwnProperty(key,value,desc,DESC_CHECK.CHECK, this);
	}
	public default boolean setOwnProperty(long index, Object value, PropertyDescriptor desc) {
		return setOwnProperty(index,value,desc,DESC_CHECK.CHECK, this);
	}
	public default boolean setOwnProperty(Symbol key, Object value, PropertyDescriptor desc) {
		return setOwnProperty(key,value,desc,DESC_CHECK.CHECK, this);
	}
	public default /*final*/ boolean setOwnProperty(Object key, Object value, PropertyDescriptor desc) {
		return setOwnProperty(key,value,desc,DESC_CHECK.CHECK, this);
	}

	public default boolean setOwnProperty(String key, Object value, PropertyDescriptor desc, DESC_CHECK check) {
		return setOwnProperty(key,value,desc,check, this);
	}
	public default boolean setOwnProperty(long index, Object value, PropertyDescriptor desc, DESC_CHECK check) {
		return setOwnProperty(RuntimeUtil.memberIndex(index),value,desc,check, this);
	}
	public default boolean setOwnProperty(Symbol key, Object value, PropertyDescriptor desc, DESC_CHECK check) {
		return setOwnProperty(key,value,desc,check, this);
	}
	public default /*final*/ boolean setOwnProperty(Object key, Object value, PropertyDescriptor desc, DESC_CHECK check) {
		return setOwnProperty(key,value,desc,check, this);
	}
	
	
	public boolean setOwnProperty(String key, Object value, PropertyDescriptor desc, DESC_CHECK check, Object receiver);
	public default boolean setOwnProperty(long index, Object value, PropertyDescriptor desc, DESC_CHECK check, Object receiver) {
		return setOwnProperty(RuntimeUtil.memberIndex(index),value,desc,check, receiver);
	}
	public default boolean setOwnProperty(Symbol key, Object value, PropertyDescriptor descriptor, DESC_CHECK check, Object receiver) {
		throw RuntimeUtil.typeError("Cannot assign symbol to object of type {0}", getClass());
	}
	public default /*final*/ boolean setOwnProperty(Object member, Object value, PropertyDescriptor descriptor, DESC_CHECK check, Object receiver) {
		if(member instanceof String s) {
			return setOwnProperty(s, value, descriptor, check, receiver);
		} else if(member instanceof Number l) {
			long idx = RuntimeUtil.numberAsMemberIndex(l);
			if(idx>=0) {
				return setOwnProperty(idx, value, descriptor, check, receiver);
			}
		} else if(member instanceof Symbol sym) {
			return setOwnProperty(sym, value, descriptor, check, receiver);
		}
		return setOwnProperty(RuntimeUtil.toString(getEnvironment(),member), value, descriptor, check, receiver);
	}
	

	public default boolean deleteProperty(String key) {
		return deleteProperty(key,DESC_CHECK.CHECK);
	}
	public default boolean deleteProperty(long index) {
		return deleteProperty(index,DESC_CHECK.CHECK);
	}
	public default boolean deleteProperty(Symbol symbol) {
		return deleteProperty(symbol,DESC_CHECK.CHECK);
	}
	public default /*final*/ boolean deleteProperty(Object symbol) {
		return deleteProperty(symbol,DESC_CHECK.CHECK);
	}
	
	public boolean deleteProperty(String key, DESC_CHECK check);
	public default boolean deleteProperty(long index, DESC_CHECK check) {
		return deleteProperty(RuntimeUtil.memberIndex(index),check);
	}
	public default boolean deleteProperty(Symbol symbol, DESC_CHECK check) {
		throw RuntimeUtil.typeError("Cannot delete symbol '{0}' to object of type '{1}'",symbol,getClass());
	}
	public default /*final*/ boolean deleteProperty(Object member, DESC_CHECK check) {
		if(member instanceof String s) {
			return deleteProperty(s, check);
		} else if(member instanceof Number l) {
			long idx = RuntimeUtil.numberAsMemberIndex(l);
			if(idx>=0) {
				return deleteProperty(idx, check);
			}
		} else if(member instanceof Symbol sym) {
			return deleteProperty(sym, check);
		}
		return deleteProperty(RuntimeUtil.toString(getEnvironment(),member), check);
	}
	

	public Iterator<Map.Entry<Object,Object>> ownPropertyEntries(boolean strings, boolean symbols, boolean enumerableOnly);

	public default JsonObject toJsonObject() {
		if(this instanceof JsonObject o) {
			return o;
		}
		JsonObject o = JsonObject.create();
		for(Iterator<Map.Entry<String,Object>> it=ownPropertyEntries(false); it.hasNext(); ) {
			Map.Entry<String,Object> e=it.next();
			o.put(e.getKey(),e.getValue());
		}
		return o;
	}
	
	
	
	//
	// Helpers
	//
	
	// Some helpers
    public default void setOwnProperty(Object id, boolean configurable, boolean enumerable, BaseGetter.Getter getter, BaseSetter.Setter setter) {
        BaseGetter bg = null;
        if(getter!=null) {
            bg = new BaseGetter(getEnvironment(),id,getter);
            bg.setFunctionName(PropertyDescriptor.getterName(id));
        }
        BaseSetter bs = null;
        if(setter!=null) {
            bs = new BaseSetter(getEnvironment(),id,setter);
            bs.setFunctionName(PropertyDescriptor.setterName(id));
        }
        PropertyDescriptor d = PropertyDescriptor.of(true, configurable, enumerable, bg, bs);
        setOwnProperty(id, RuntimeUtil.NOT_AVAILABLE, d);
    }
	
	public default void setOwnMethod(BaseMethod method) {
		setOwnMethod(method,PropertyDescriptor.DESC_METHOD);
	}	
	public default void setOwnMethod(BaseMethod method, PropertyDescriptor desc) {
		Object id = method.getId();
		setOwnMethodById(id,method,desc);
	}	

	public default void setOwnAlias(Object method, Object alias) {
		setOwnAlias(method,alias,PropertyDescriptor.DESC_METHOD);
	}	
	public default void setOwnAlias(Object method, Object alias, PropertyDescriptor desc) {
		BaseMethod m = getMethodById(method);
		if(m==null) {
			throw new IllegalStateException("Method not found: "+method);
		}
		setOwnMethodById(alias,m,desc);
	}	

	private BaseMethod getMethodById(Object id) {
		if(id instanceof String str) {
			return (BaseMethod)getProperty(str);
		} else if (id instanceof Symbol sym) {
			return (BaseMethod)getProperty(sym);
		} else {
			throw new IllegalStateException();
		}
	}	
	private void setOwnMethodById(Object id, BaseMethod method, PropertyDescriptor desc) {
		if(id instanceof String str) {
			setOwnProperty(str,method,desc);
		} else if (id instanceof Symbol sym) {
			setOwnProperty(sym,method,desc);
		} else {
			throw new IllegalStateException();
		}
	}	
	
	
	public default Iterator<String> ownPropertyKeys(boolean enumerableOnly) {
		return Iterators.map(ownPropertyEntries(true,false,enumerableOnly), (e) -> (String)e.getKey() );
	}
	public default Iterator<Object> ownPropertyValues(boolean enumerableOnly) {
		return Iterators.map(ownPropertyEntries(true,false,enumerableOnly), (e) -> e.getValue() );
	}
	public default Iterator<Symbol> ownPropertySymbols(boolean enumerableOnly) {
		return Iterators.map(ownPropertyEntries(false,true,enumerableOnly), (e) -> (Symbol)e.getKey() );
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public default Iterator<Map.Entry<String, Object>> ownPropertyEntries(boolean enumerableOnly) {
		return (Iterator)ownPropertyEntries(true, false, enumerableOnly);
	}
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public default Iterator<Map.Entry<Symbol, Object>> ownPropertySymbolEntries(boolean enumerableOnly) {
		return (Iterator)ownPropertyEntries(false, true, enumerableOnly);
	}
}
