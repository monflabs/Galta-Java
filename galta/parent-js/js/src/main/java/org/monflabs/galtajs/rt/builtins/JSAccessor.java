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
package org.monflabs.galtajs.rt.builtins;	

import java.util.Iterator;
import java.util.Map;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.jsonfactory.JSObject;
import org.monflabs.galtajs.jsonfactory.JSObject.DESC_CHECK;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.builtins.primitives.symbol.Symbol;
import org.monflabs.util.iterators.Iterators;

/**
 * JavaScript internal properties accessor.
 */
public abstract class JSAccessor {
	
	private JSEnvironment env;
	
	
	//
	// Methods that should be overridden for a specific object type
	// Some methods are directly mapping JavaScript slots from the specs, some are extensions
	// and some are more granular methods.
	//

	
	// -----------------------------------------------------------------------------------
	//
	// GaltaJS extension: get the name
	public abstract String getClassName(Object _this);

	
	// -----------------------------------------------------------------------------------
	//
	// [[GetPrototypeOf]]
	//
	// -----------------------------------------------------------------------------------
	
	public Object getPrototype(Object _this) {
		return null;
	}	
	
	
	// -----------------------------------------------------------------------------------
	//
	// [[SetPrototypeOf]]
	//
	// -----------------------------------------------------------------------------------
	
	public boolean setPrototype(Object _this, Object prototype) {
		return false;
	}
	
	
	// -----------------------------------------------------------------------------------
	//
	// [[IsExtensible]]
	//
	// -----------------------------------------------------------------------------------
	
	public boolean isExtensible(Object _this) {
		return false;
	}
	public boolean isSealed(Object _this) {
		return true;
	}
	public boolean isFrozen(Object _this) {
		return true;
	}

	
	// -----------------------------------------------------------------------------------
	//
	// [[PreventExtensions]]
	//
	// -----------------------------------------------------------------------------------
	
	public boolean preventExtensions(Object _this) {
		return false;
	}
	public void seal(Object _this) {
	}
	public void freeze(Object _this) {
	}
	
	
	// -----------------------------------------------------------------------------------
	//
	// [[GetOwnProperty]]
	//
	// -----------------------------------------------------------------------------------

	public PropertyDescriptor getOwnPropertyDescriptor(Object _this, String member) {
		Object v = getOwnProperty(_this, member, RuntimeUtil.NOT_AVAILABLE, _this);
		if(v!=RuntimeUtil.NOT_AVAILABLE) {
			return PropertyDescriptor.DESC_DEFAULT;
		}
		return null;
	}
	public PropertyDescriptor getOwnPropertyDescriptor(Object _this, long index) {
		Object v = getOwnProperty(_this, index, RuntimeUtil.NOT_AVAILABLE, _this);
		if(v!=RuntimeUtil.NOT_AVAILABLE) {
			return PropertyDescriptor.DESC_DEFAULT;
		}
		return null;
	}
	public PropertyDescriptor getOwnPropertyDescriptor(Object _this, Symbol symbol) {
		Object v = getOwnProperty(_this, symbol, RuntimeUtil.NOT_AVAILABLE, _this);
		if(v!=RuntimeUtil.NOT_AVAILABLE) {
			return PropertyDescriptor.DESC_DEFAULT;
		}
		return null;
	}

	public final PropertyDescriptor getOwnPropertyDescriptor(Object _this, Object member) {
		if(member instanceof String s) {
			return getOwnPropertyDescriptor(_this, s);
		} else if(member instanceof Number l) {
			long idx = RuntimeUtil.numberAsMemberIndex(l);
			if(idx>=0) {
				return getOwnPropertyDescriptor(_this, idx);
			}
		} else if(member instanceof Symbol sym) {
			return getOwnPropertyDescriptor(_this, sym);
		}
		JSEnvironment env = getEnvironment();
		return getOwnPropertyDescriptor(_this, RuntimeUtil.toString(env,member));
	}
	
	public final PropertyDescriptor getPropertyDescriptor(Object _this, Object member) {
		PropertyDescriptor v = getOwnPropertyDescriptor(_this, member);
		if(v!=null) {
			return v;
		}
		JSEnvironment env = getEnvironment();
		Object p=getPrototype(_this);
		while(p!=null) {
			JSAccessor a = env.getAccessor(p); 
			v = a.getOwnPropertyDescriptor(p, member);
			if(v!=null) {
				return v;
			}
			p = a.getPrototype(p);
		}
		return null;
	}

	
	public JSObject getOwnPropertyDescriptors(JSObject descriptors, Object _this) {
		return descriptors;
	}

	// FromPropertyDescriptor (6.2.6.4): converts a resolved PropertyDescriptor
	// into the wire-format JSObject Object.getOwnPropertyDescriptor(s) returns.
	// Default (ordinary-object) implementation re-fetches the value via a live
	// getOwnProperty call - harmless for a plain data property (no side
	// effects, same value the descriptor's own [[Value]] would be) - but NOT
	// valid for an exotic accessor like Proxy, whose [[GetOwnProperty]] trap
	// already returned a fully-resolved descriptor object with its own
	// "value" field; re-fetching via a live [[Get]] there is an extra,
	// spec-incorrect observable trap call (see ProxyAccessor's override,
	// confirmed via getOwnPropertyDescriptors/observable-operations.js).
	public JSObject getOwnPropertyDescriptorAsJSObject(Object _this, Object member) {
		PropertyDescriptor d = getOwnPropertyDescriptor(_this, member);
		if(d==null) {
			return null;
		}
		return d.asJSObject(getEnvironment(), () -> getOwnProperty(_this, member, RuntimeUtil.NOT_AVAILABLE, _this));
	}

	// Object.getOwnPropertyDescriptors' wire-format result (one
	// getOwnPropertyDescriptorAsJSObject-shaped entry per own key). Default
	// (ordinary-object) implementation: collect the PropertyDescriptor map
	// first, then resolve each entry's JSObject via a live value re-fetch -
	// safe for ordinary objects (see getOwnPropertyDescriptorAsJSObject's
	// own doc comment). ProxyAccessor overrides this to do a single
	// "ownKeys" trap call followed by exactly one "getOwnPropertyDescriptor"
	// trap call per key, with no separate "get" trap calls at all.
	public JSObject getOwnPropertyDescriptorsAsJSObject(Object _this) {
		JSObject descriptors = getOwnPropertyDescriptors(JSObject.create(getEnvironment()), _this);
		JSObject r = JSObject.create(getEnvironment());
		for(Iterator<Map.Entry<String,Object>> it=descriptors.ownPropertyEntries(true); it.hasNext(); ) {
			Map.Entry<String,Object> e = it.next();
			r.setOwnProperty(e.getKey(), ((PropertyDescriptor)e.getValue()).asJSObject(getEnvironment(), () -> getOwnProperty(_this, e.getKey(), RuntimeUtil.NOT_AVAILABLE, _this)));
		}
		for(Iterator<Map.Entry<Symbol,Object>> it=descriptors.ownPropertySymbolEntries(true); it.hasNext(); ) {
			Map.Entry<Symbol,Object> e = it.next();
			r.setOwnProperty(e.getKey(), ((PropertyDescriptor)e.getValue()).asJSObject(getEnvironment(), () -> getOwnProperty(_this, e.getKey(), RuntimeUtil.NOT_AVAILABLE, _this)));
		}
		return r;
	}

	
	
	// -----------------------------------------------------------------------------------
	//
	// [[HasProperty]]
	//
	// -----------------------------------------------------------------------------------

	
	// Spec OrdinaryHasProperty: delegates to the prototype's OWN
	// [[HasProperty]] (not [[GetOwnProperty]]) when this level has no own
	// property - a Proxy ancestor's `has` trap only returns a boolean, not a
	// full descriptor, so this can't be built on top of getPropertyDescriptor
	// (which needs a real descriptor at every level and would silently
	// bypass the trap by calling getOwnPropertyDescriptor on the ancestor
	// instead).
	public boolean hasProperty(Object _this, String member) {
		if(hasOwnProperty(_this,member)) {
			return true;
		}
		Object p = getPrototype(_this);
		if(p==null) {
			return false;
		}
		return getEnvironment().getAccessor(p).hasProperty(p,member);
	}
	public boolean hasProperty(Object _this, long index) {
		if(hasOwnProperty(_this,index)) {
			return true;
		}
		Object p = getPrototype(_this);
		if(p==null) {
			return false;
		}
		return getEnvironment().getAccessor(p).hasProperty(p,index);
	}
	public boolean hasProperty(Object _this, Symbol symbol) {
		if(hasOwnProperty(_this,symbol)) {
			return true;
		}
		Object p = getPrototype(_this);
		if(p==null) {
			return false;
		}
		return getEnvironment().getAccessor(p).hasProperty(p,symbol);
	}
	public final boolean hasProperty(Object _this, Object member) {
		if(member instanceof String s) {
			return hasProperty(_this, s);
		} else if(member instanceof Number l) {
			long idx = RuntimeUtil.numberAsMemberIndex(l);
			if(idx>=0) {
				return hasProperty(_this, idx);
			}
		} else if(member instanceof Symbol sym) {
			return hasProperty(_this, sym);
		}
		JSEnvironment env = getEnvironment();
		return hasProperty(_this, RuntimeUtil.toString(env,member));
	}

	
	// Extra
	public final boolean hasOwnProperty(Object _this, String member) {
		return getOwnPropertyDescriptor(_this,member)!=null;
	}
	public final boolean hasOwnProperty(Object _this, long index) {
		return getOwnPropertyDescriptor(_this,index)!=null;
	}
	public final boolean hasOwnProperty(Object _this, Symbol symbol) {
		return getOwnPropertyDescriptor(_this,symbol)!=null;
	}
	public final boolean hasOwnProperty(Object _this, Object member) {
		if(member instanceof String s) {
			return hasOwnProperty(_this, s);
		} else if(member instanceof Number l) {
			long idx = RuntimeUtil.numberAsMemberIndex(l);
			if(idx>=0) {
				return hasOwnProperty(_this, idx);
			}
		} else if(member instanceof Symbol sym) {
			return hasOwnProperty(_this, sym);
		}
		JSEnvironment env = getEnvironment();
		return hasOwnProperty(_this, RuntimeUtil.toString(env,member));
	}

	
	
	// -----------------------------------------------------------------------------------
	//
	// [[GetOwnProperty]]
	//
	// -----------------------------------------------------------------------------------
	
	public Object getOwnProperty(Object _this, String member, Object defaultValue, Object receiver) {
		return defaultValue;
	}
	public Object getOwnProperty(Object _this, long index, Object defaultValue, Object receiver) {
		return defaultValue;
	}
	public Object getOwnProperty(Object _this, Symbol symbol, Object defaultValue, Object receiver) {
		return defaultValue;
	}
	public final Object getOwnProperty(Object _this, Object member, Object defaultValue, Object receiver) {
		if(member instanceof String s) {
			return getOwnProperty(_this, s, defaultValue, receiver);
		} else if(member instanceof Number l) {
			long idx = RuntimeUtil.numberAsMemberIndex(l);
			if(idx>=0) {
				return getOwnProperty(_this, idx, defaultValue, receiver);
			}
		} else if(member instanceof Symbol sym) {
			return getOwnProperty(_this, sym, defaultValue, receiver);
		}
		JSEnvironment env = getEnvironment();
		return getOwnProperty(_this, RuntimeUtil.toString(env,member), defaultValue, receiver);
	}


	
	// -----------------------------------------------------------------------------------
	//
	// [[Get]]
	//
	// -----------------------------------------------------------------------------------
	
	public Object getProperty(Object _this, String member, Object defaultValue) {
		return getProperty(_this, member, defaultValue, _this);
	}
	public Object getProperty(Object _this, long index, Object defaultValue) {
		return getProperty(_this, index, defaultValue, _this);
	}
	public Object getProperty(Object _this, Symbol symbol, Object defaultValue) {
		return getProperty(_this, symbol, defaultValue, _this);
	}
	public final Object getProperty(Object _this, Object member, Object defaultValue) {
		return getProperty(_this, member, defaultValue, _this);
	}

	// Receiver-aware [[Get]]: `_this` is where the property search starts (and
	// walks up the prototype chain from), but `receiver` is what's bound as
	// `this` if an accessor's getter is invoked - they differ for a SuperProperty
	// access (search starts at the home object's prototype, but `this` for the
	// getter call remains the actual `this`).
	public Object getProperty(Object _this, String member, Object defaultValue, Object receiver) {
		Object v = getOwnProperty(_this, member, RuntimeUtil.NOT_AVAILABLE, receiver);
		if(v!=RuntimeUtil.NOT_AVAILABLE) {
			return v;
		}
		JSEnvironment env = getEnvironment();
		Object p=getPrototype(_this);
		while(p!=null) {
			if(p instanceof JSObject jo) { // Optimization
				v = jo.getOwnProperty(member, RuntimeUtil.NOT_AVAILABLE, receiver);
				if(v!=RuntimeUtil.NOT_AVAILABLE) {
					return v;
				}
				p = jo.getPrototype();
			} else {
				JSAccessor a = env.getAccessor(p);
				v = a.getOwnProperty(p, member, RuntimeUtil.NOT_AVAILABLE, receiver);
				if(v!=RuntimeUtil.NOT_AVAILABLE) {
					return v;
				}
				p = a.getPrototype(p);
			}
		}
		return defaultValue;
	}
	public Object getProperty(Object _this, long index, Object defaultValue, Object receiver) {
		Object v = getOwnProperty(_this, index, RuntimeUtil.NOT_AVAILABLE, receiver);
		if(v!=RuntimeUtil.NOT_AVAILABLE) {
			return v;
		}
		JSEnvironment env = getEnvironment();
		Object p=getPrototype(_this);
		while(p!=null) {
			if(p instanceof JSObject jo) { // Optimization
				// Optimize if the prototype has no number property
				if(p instanceof BasePrototype bp && !bp.mayHaveNumberProp()) {
					p = bp.getPrototype();
					continue;
				}
				v = jo.getOwnProperty(index, RuntimeUtil.NOT_AVAILABLE, receiver);
				if(v!=RuntimeUtil.NOT_AVAILABLE) {
					return v;
				}
				p = jo.getPrototype();
			} else {
				JSAccessor a = env.getAccessor(p);
				v = a.getOwnProperty(p, index, RuntimeUtil.NOT_AVAILABLE, receiver);
				if(v!=RuntimeUtil.NOT_AVAILABLE) {
					return v;
				}
				p = a.getPrototype(p);
			}
		}
		return defaultValue;
	}
	public Object getProperty(Object _this, Symbol symbol, Object defaultValue, Object receiver) {
		Object v = getOwnProperty(_this, symbol, RuntimeUtil.NOT_AVAILABLE, receiver);
		if(v!=RuntimeUtil.NOT_AVAILABLE) {
			return v;
		}
		JSEnvironment env = getEnvironment();
		Object p=getPrototype(_this);
		while(p!=null) {
			if(p instanceof JSObject jo) { // Optimization
				v = jo.getOwnProperty(symbol, RuntimeUtil.NOT_AVAILABLE, receiver);
				if(v!=RuntimeUtil.NOT_AVAILABLE) {
					return v;
				}
				p = jo.getPrototype();
			} else {
				JSAccessor a = env.getAccessor(p);
				v = a.getOwnProperty(p, symbol, RuntimeUtil.NOT_AVAILABLE, receiver);
				if(v!=RuntimeUtil.NOT_AVAILABLE) {
					return v;
				}
				p = a.getPrototype(p);
			}
		}
		return defaultValue;
	}
	public final Object getProperty(Object _this, Object member, Object defaultValue, Object receiver) {
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
			return getProperty(_this, s, defaultValue, receiver);
		} else if(member instanceof Number l) {
			long idx = RuntimeUtil.numberAsMemberIndex(l);
			if(idx>=0) {
				return getProperty(_this, idx, defaultValue, receiver);
			}
		} else if(member instanceof Symbol sym) {
			return getProperty(_this, sym, defaultValue, receiver);
		}
		JSEnvironment env = getEnvironment();
		return getProperty(_this, RuntimeUtil.toString(env,member), defaultValue, receiver);
	}


	
	// -----------------------------------------------------------------------------------
	//
	// [[Set]]
	//
	// -----------------------------------------------------------------------------------
	
	// Should be overridden by the subclasses
	public boolean setOwnProperty(Object _this, String member, Object value, PropertyDescriptor desc, DESC_CHECK check, Object receiver) {
		return false;
	}
	public boolean setOwnProperty(Object _this, long index, Object value, PropertyDescriptor desc, DESC_CHECK check, Object receiver) {
		return false;
	}
	public boolean setOwnProperty(Object _this, Symbol symbol, Object value, PropertyDescriptor desc, DESC_CHECK check, Object receiver) {
		return false;
	}
	public final boolean setOwnProperty(Object _this, Object member, Object value, PropertyDescriptor desc, DESC_CHECK check, Object receiver) {
		if(member instanceof String s) {
			return setOwnProperty(_this, s, value, desc, check, receiver);
		} else if(member instanceof Number l) {
			long idx = RuntimeUtil.numberAsMemberIndex(l);
			if(idx>=0) {
				return setOwnProperty(_this, idx, value, desc, check, receiver);
			}
		} else if(member instanceof Symbol sym) {
			return setOwnProperty(_this, sym, value, desc, check, receiver);
		}
		JSEnvironment env = getEnvironment();
		return setOwnProperty(_this, RuntimeUtil.toString(env,member),value,desc,check,receiver);
	}

	// Spec OrdinarySet / OrdinarySetWithOwnDescriptor: when `_this` has no own
	// descriptor, delegate the WHOLE [[Set]] to the prototype's own [[Set]]
	// (not just consult its [[GetOwnProperty]], as the previous
	// implementation did) - so a Proxy ancestor's `set` trap fires correctly,
	// with the ORIGINAL receiver preserved through the recursion, exactly
	// like hasProperty()'s delegation to [[HasProperty]] above. Once an own
	// descriptor IS found (at whatever level), an accessor's setter is
	// invoked at the level it was found (bound to `receiver`, via
	// setOwnProperty's own setter-detection); a writable data descriptor -
	// own OR inherited - always creates/overwrites the property on
	// `receiver` (never mutates the ancestor it happened to be found on -
	// the previous implementation's fallback got this right only when
	// NOTHING was found anywhere, not for an inherited writable data
	// property, which it silently walked past entirely - the other half of
	// this same bug). This single shape also correctly handles the
	// SuperProperty (`receiver != _this`) case with no separate
	// implementation needed - `receiver` is threaded through uniformly.
	public boolean setProperty(Object _this, String member, Object value, PropertyDescriptor desc, DESC_CHECK check, Object receiver) {
		PropertyDescriptor pd = getOwnPropertyDescriptor(_this,member);
		if(pd!=null) {
			if(pd.getSetter()!=null) {
				// A plain [[Set]] invoking an EXISTING accessor's setter is
				// not a [[DefineOwnProperty]] request to change the
				// property's shape - passing the caller's data-shaped
				// `desc` template through here made the underlying
				// property map treat a data-shaped descriptor as license
				// to silently overwrite the accessor with a plain value
				// instead of invoking its setter (confirmed via
				// push/set-length-array-is-frozen.js: pushing into an
				// array whose prototype has an index accessor must invoke
				// that setter, not clobber it). `null` here means "don't
				// touch the shape, use whatever setOwnProperty finds".
				return setOwnProperty(_this, member, value, null, check, receiver);
			}
			if(pd.getGetter()!=null || !pd.isWritable()) {
				return rejectSet(check, member);
			}
		} else {
			Object p = getPrototype(_this);
			if(p!=null) {
				return getEnvironment().getAccessor(p).setProperty(p, member, value, desc, check, receiver);
			}
		}
		// A writable data descriptor was found (own, or via delegation with
		// no further ancestor to check), or nothing was found anywhere in
		// the chain (treated as a default, fully-permissive absent
		// descriptor) - either way the write targets the RECEIVER. Per spec
		// (OrdinarySetWithOwnDescriptor's data-descriptor branch), a
		// non-object Receiver must fail here (BEFORE consulting its own
		// descriptor) - via rejectSet, same as the other rejection reasons
		// below, so a strict CALLER still gets PutValue's usual throw (e.g.
		// plain `"abc".foo = 1` in strict mode - `_this` starts as the
		// primitive but shifts to an ancestor prototype as the chain is
		// walked, while `receiver` stays the ORIGINAL non-object primitive,
		// so this check fires here rather than via `receiver==_this`);
		// Reflect.set's NO_EXCEPTION check never throws regardless, so an
		// arbitrary non-object receiver there (e.g. Reflect.set(target, key,
		// value, nonObjectReceiver)) just returns false as expected. Once
		// past that, the RECEIVER's OWN existing descriptor must be
		// consulted independently - an observable step (e.g. invokes a Proxy
		// receiver's own getOwnPropertyDescriptor trap when `receiver`
		// differs from `_this`, as when delegating through a Proxy ancestor
		// or a SuperProperty assignment) - reusing `pd` when receiver==_this
		// avoids a redundant duplicate lookup on the hot, common
		// (non-Proxy, non-super) path.
		if(!RuntimeUtil.isObject(getEnvironment(), receiver)) {
			return rejectSet(check, member);
		}
		JSAccessor receiverAcc = getEnvironment().getAccessor(receiver);
		PropertyDescriptor existing = (receiver==_this) ? pd : receiverAcc.getOwnPropertyDescriptor(receiver, member);
		if(existing!=null && (existing.getGetter()!=null || existing.getSetter()!=null || !existing.isWritable())) {
			return rejectSet(check, member);
		}
		return receiverAcc.setOwnProperty(receiver, member, value, desc, check, receiver);
	}
	public boolean setProperty(Object _this, long index, Object value, PropertyDescriptor desc, DESC_CHECK check, Object receiver) {
		PropertyDescriptor pd = getOwnPropertyDescriptor(_this,index);
		if(pd!=null) {
			if(pd.getSetter()!=null) {
				// See setProperty(Object,String,...)'s matching comment above.
				return setOwnProperty(_this, index, value, null, check, receiver);
			}
			if(pd.getGetter()!=null || !pd.isWritable()) {
				return rejectSet(check, index);
			}
		} else {
			Object p = getPrototype(_this);
			// Optimize past prototypes known to never have a numeric setter -
			// avoids a wasted getOwnPropertyDescriptor call per level for the
			// very common numeric-index-write case (array pushes, etc).
			while(p instanceof BasePrototype bp && !bp.mayHaveNumberPropSetter()) {
				p = bp.getPrototype();
			}
			if(p!=null) {
				return getEnvironment().getAccessor(p).setProperty(p, index, value, desc, check, receiver);
			}
		}
		if(!RuntimeUtil.isObject(getEnvironment(), receiver)) {
			return rejectSet(check, index);
		}
		JSAccessor receiverAcc = getEnvironment().getAccessor(receiver);
		PropertyDescriptor existing = (receiver==_this) ? pd : receiverAcc.getOwnPropertyDescriptor(receiver, index);
		if(existing!=null && (existing.getGetter()!=null || existing.getSetter()!=null || !existing.isWritable())) {
			return rejectSet(check, index);
		}
		return receiverAcc.setOwnProperty(receiver, index, value, desc, check, receiver);
	}
	public boolean setProperty(Object _this, Symbol symbol, Object value, PropertyDescriptor desc, DESC_CHECK check, Object receiver) {
		PropertyDescriptor pd = getOwnPropertyDescriptor(_this,symbol);
		if(pd!=null) {
			if(pd.getSetter()!=null) {
				// See setProperty(Object,String,...)'s matching comment above.
				return setOwnProperty(_this, symbol, value, null, check, receiver);
			}
			if(pd.getGetter()!=null || !pd.isWritable()) {
				return rejectSet(check, symbol);
			}
		} else {
			Object p = getPrototype(_this);
			if(p!=null) {
				return getEnvironment().getAccessor(p).setProperty(p, symbol, value, desc, check, receiver);
			}
		}
		if(!RuntimeUtil.isObject(getEnvironment(), receiver)) {
			return rejectSet(check, symbol);
		}
		JSAccessor receiverAcc = getEnvironment().getAccessor(receiver);
		PropertyDescriptor existing = (receiver==_this) ? pd : receiverAcc.getOwnPropertyDescriptor(receiver, symbol);
		if(existing!=null && (existing.getGetter()!=null || existing.getSetter()!=null || !existing.isWritable())) {
			return rejectSet(check, symbol);
		}
		return receiverAcc.setOwnProperty(receiver, symbol, value, desc, check, receiver);
	}
	private boolean rejectSet(DESC_CHECK check, Object member) {
		if(RuntimeUtil.isStrictCheck(check)) {
			throw RuntimeUtil.typeError("Property {0} is not writable", member);
		}
		return false;
	}
	public final boolean setProperty(Object _this, Object member, Object value, PropertyDescriptor desc, DESC_CHECK check, Object receiver) {
		if(member instanceof String s) {
			return setProperty(_this, s, value, desc, check, receiver);
		} else if(member instanceof Number l) {
			long idx = RuntimeUtil.numberAsMemberIndex(l);
			if(idx>=0) {
				return setProperty(_this, idx, value, desc, check, receiver);
			}
		} else if(member instanceof Symbol sym) {
			return setProperty(_this, sym, value, desc, check, receiver);
		}
		JSEnvironment env = getEnvironment();
		return setProperty(_this, RuntimeUtil.toString(env,member),value,desc,check,receiver);
	}

	// No-receiver convenience forms: receiver defaults to `_this`.
	public boolean setProperty(Object _this, String member, Object value, PropertyDescriptor desc, DESC_CHECK check) {
		return setProperty(_this, member, value, desc, check, _this);
	}
	public boolean setProperty(Object _this, long index, Object value, PropertyDescriptor desc, DESC_CHECK check) {
		return setProperty(_this, index, value, desc, check, _this);
	}
	public boolean setProperty(Object _this, Symbol symbol, Object value, PropertyDescriptor desc, DESC_CHECK check) {
		return setProperty(_this, symbol, value, desc, check, _this);
	}
	public final boolean setProperty(Object _this, Object member, Object value, PropertyDescriptor desc, DESC_CHECK check) {
		return setProperty(_this, member, value, desc, check, _this);
	}
	
	
	// -----------------------------------------------------------------------------------
	//
	// [[Delete]]
	//
	// -----------------------------------------------------------------------------------

	public boolean deleteProperty(Object _this, String member, DESC_CHECK check) {
		return true;
	}
	public boolean deleteProperty(Object _this, long index, DESC_CHECK check) {
		return true;
	}
	public boolean deleteProperty(Object _this, Symbol symbol, DESC_CHECK check) {
		return true;
	}
	public final boolean deleteProperty(Object _this, Object member, DESC_CHECK check) {
		if(member instanceof String s) {
			return deleteProperty(_this, s, check);
		} else if(member instanceof Number l) {
			long idx = RuntimeUtil.numberAsMemberIndex(l);
			if(idx>=0) {
				return deleteProperty(_this, idx, check);
			}
		} else if(member instanceof Symbol sym) {
			return deleteProperty(_this, sym, check);
		}
		JSEnvironment env = getEnvironment();
		return deleteProperty(_this, RuntimeUtil.toString(env, member), check);
	}

	
	// -----------------------------------------------------------------------------------
	//
	// [[OwnPropertyKeys]]
	//
	// -----------------------------------------------------------------------------------

	public Iterator<Map.Entry<Object,Object>> ownPropertyEntries(Object _this, boolean strings, boolean symbols, boolean enumerableOnly) {
		return Iterators.empty();
	}


	
	//
	// Helpers
	// These are implemented on top of the other APIs and cannot be overridden.
	//
	
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public final Iterator<Map.Entry<String, Object>> ownStringEntries(Object _this, boolean enumerableOnly) {
		return (Iterator)ownPropertyEntries(_this, true, false, enumerableOnly);
	}
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public final Iterator<Map.Entry<Symbol, Object>> ownSymbolEntries(Object _this, boolean enumerableOnly) {
		return (Iterator)ownPropertyEntries(_this, false, true, enumerableOnly);
	}
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public final Iterator<Map.Entry<Object, Object>> ownEntries(Object _this, boolean enumerableOnly) {
		return (Iterator)ownPropertyEntries(_this, true, true, enumerableOnly);
	}
	
	
	
	
	// Helper: Map.entry() fails with null values
	// Should we move this utilities elsewhere?
	public static <K,V>  Map.Entry<K,V> newEntry(K key, V value) {
		return new Map.Entry<K,V>() {
			@Override
			public K getKey() {
				return key;
			}
			@Override
			public V getValue() {
				return value;
			}
			@Override
			public V setValue(Object value) {
				throw new IllegalStateException();
			}
		};
	}
	
	
	
	//
	// Object itself
	// All the functions below are helpers are consumed the functions above
	//
	
	protected JSAccessor(JSEnvironment env) {
		this.env = env;
	}
	
	public final JSEnvironment getEnvironment() {
		return env;
	}
}
