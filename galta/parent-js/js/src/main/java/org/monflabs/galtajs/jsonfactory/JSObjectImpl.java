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
import java.util.function.Consumer;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.library.java.JSJavaLibrary;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.builtins.BaseCallableObject;
import org.monflabs.galtajs.rt.builtins.HomeObject;
import org.monflabs.galtajs.rt.builtins.PropertyDescriptor;
import org.monflabs.galtajs.rt.builtins.primitives.object.BuiltinObjectPrototype;
import org.monflabs.galtajs.rt.builtins.primitives.symbol.Symbol;
import org.monflabs.galtajs.rt.builtins.privatename.PrivateElementsHolder;
import org.monflabs.galtajs.rt.builtins.privatename.PrivateName;
import org.monflabs.util.StringUtil;
import org.monflabs.util.iterators.Iterators;


/**
 * Json Object implemented on top of a BaseJsonObjectMap
 */
public abstract class JSObjectImpl extends JsonObjectAsScriptMap implements PrivateElementsHolder {

	private JSEnvironment env;
	
	// JavaScript data
	private boolean prototypeSet;
	private Object prototype;
	private SymbolPropertyMap symbols;
	private PrivateElementsMap privateElements;

	protected JSObjectImpl(JSEnvironment env) {
		this.env = env;
	}
	protected JSObjectImpl(JSEnvironment env, Object prototype) {
		this.env = env;
		this.prototypeSet = true;
		this.prototype = prototype;
	}
	
	@Override
	public final JSEnvironment getEnvironment() {
		return env;
	}
	
	@Override
	public String toString() {
		return "[Object]";
	}
	
	@Override
	public GaltaJsJsonFactory factory() {
		return getEnvironment().getJsonFactory();
	}
	
	@Override
	public Object getPrototype() {
		// Find a better way to deal with prototypes
		if(!prototypeSet) {
			prototype = getDefaultPrototype();
		}
		return prototype;
	}
	protected Object getDefaultPrototype() {
		return BuiltinObjectPrototype.get(getEnvironment());
	}

	// This object's own String-keyed properties live directly in `this`
	// (inherited ObjectPropertiesMap<String> state via StringPropertyMap),
	// but Symbol-keyed properties live in the SEPARATE `symbols` map, whose
	// own [[Extensible]]/sealed/frozen flags and per-entry descriptors are
	// independent bookkeeping - cascade every WRITE (the flags always stay
	// in sync since both sides are only ever flipped together, here) so
	// freeze()/seal()/preventExtensions() on an object with symbol-keyed
	// properties doesn't leave those properties completely unaffected
	// (isExtensible/isSealed/isFrozen are `final` on ObjectPropertiesMap
	// and already correctly reflect the string side, which this keeps in
	// lock-step with the symbol side).
	@Override
	public boolean preventExtensions() {
		boolean ok = super.preventExtensions();
		if(symbols!=null) {
			ok = symbols.preventExtensions() && ok;
		}
		return ok;
	}
	@Override
	public void seal() {
		super.seal();
		if(symbols!=null) {
			symbols.seal();
		}
	}
	@Override
	public void freeze() {
		super.freeze();
		if(symbols!=null) {
			symbols.freeze();
		}
	}

	// OrdinarySetPrototypeOf: same-value fast path bypasses the extensibility
	// check entirely, and setting a prototype that (transitively) points back
	// to `this` must be rejected (cycle detection) rather than blindly wired
	// up, in addition to the extensibility check.
	@Override
	public boolean setPrototype(Object prototype) {
		Object current = getPrototype();
		if(current==prototype) {
			return true;
		}
		if(!isExtensible()) {
			return false;
		}
		Object p = prototype;
		while(p!=null) {
			if(p==this) {
				return false;
			}
			if(p instanceof JSObject jo) {
				p = jo.getPrototype();
			} else {
				break; // exotic object - stop the ordinary chain walk
			}
		}
		this.prototypeSet = true;
		this.prototype = prototype;
		return true;
	}


	
	@Override
	public boolean hasOwnProperty(String member) {
		return has(member);
	}
	@Override
	public boolean hasOwnProperty(long index) {
		// See getOwnPropertyDescriptor(long)'s matching comment - the
		// mayHaveNumberProp() flag doesn't track keys beyond
		// SparseList.MAX_INDEX, so it must not gate the lookup for those.
		if(mayHaveNumberProp() || !RuntimeUtil.isMemberIndex(index)) {
			return super.hasOwnProperty(index);
		}
		return false;
	}
	@Override
	public boolean hasOwnProperty(Symbol symbol) {
		if(symbols!=null) {
			return symbols.getEntry(symbol)!=null;
		}
		return false;
	}

	
	@Override
	public PropertyDescriptor getOwnPropertyDescriptor(String member) {
		EntryImpl<String> e = getEntry(member);
		return e!=null ? e.getPropertyDescriptor() : null;
	}
	@Override
	public PropertyDescriptor getOwnPropertyDescriptor(long index) {
		// mayHaveNumberProp()'s bookkeeping (StringPropertyMap.entryAdded,
		// keyed off EntryImpl.getLongKey()) only tracks keys within
		// SparseList.MAX_INDEX - a property genuinely keyed by a numeric
		// string BEYOND that (e.g. "9007199254740990", an ordinary string
		// property, not a real array index) is invisible to the flag,
		// making this fast-path wrongly assume "no numeric props at all"
		// and return null without ever checking the real map - confirmed
		// via reverse/length-exceeding-integer-limit-with-object.js: a
		// getter property keyed "9007199254740990" was never found this
		// way, so hasProperty/hasOwnProperty(long) always reported false
		// for it, and reverse()'s expected stop-iteration exception never
		// fired - an effectively infinite loop (~2^52 iterations) instead
		// of throwing after 4. Only trust the flag within its own tracked
		// range; beyond it, always do the real (String-keyed) lookup.
		if(mayHaveNumberProp() || !RuntimeUtil.isMemberIndex(index)) {
			return super.getOwnPropertyDescriptor(index);
		}
		return null;
	}
	@Override
	public PropertyDescriptor getOwnPropertyDescriptor(Symbol symbol) {
		if(symbols!=null) {
			EntryImpl<Symbol> e = symbols.getEntry(symbol);
			return e!=null ? e.getPropertyDescriptor() : null;
		}
		return null;
	}
	
	@Override
	public JSObject getOwnPropertyDescriptors(JSObject descriptors) {
		// entrySet() (no-arg) defaults to enumerable-only - wrong here, since
		// spec OwnPropertyKeys/[[GetOwnProperty]] (unlike Object.keys) must
		// include every own property regardless of enumerability (confirmed
		// via Object.isFrozen/isSealed relying on this to see a non-
		// enumerable Object.defineProperty'd property at all). The
		// iterator's own Map.Entry is NOT always the raw EntryImpl - an
		// accessor entry gets wrapped (its getValue() invokes the getter)
		// so re-look-up via getEntry(key) instead of casting blindly, which
		// threw ClassCastException for any accessor property.
		for(Iterator<Entry<String,Object>> it=entrySet(false).iterator(); it.hasNext(); ) {
			Entry<String,Object> entry = it.next();
			// The iterator only wraps an ACCESSOR entry (see EntrySet's own
			// comment) - an ordinary data property's entry already IS the
			// raw EntryImpl, so re-look-up via getEntry(key) - a second hash
			// lookup - is only actually needed for the rare accessor case.
			EntryImpl<String> e = entry instanceof EntryImpl ? (EntryImpl<String>)entry : getEntry(entry.getKey());
			descriptors.setOwnProperty(e.getKey(), e.getPropertyDescriptor());
		}
		if(symbols!=null) {
			for(Iterator<Entry<Symbol,Object>> it=symbols.entrySet(false).iterator(); it.hasNext(); ) {
				Entry<Symbol,Object> entry = it.next();
				EntryImpl<Symbol> e = entry instanceof EntryImpl ? (EntryImpl<Symbol>)entry : symbols.getEntry(entry.getKey());
				descriptors.setOwnProperty(e.getKey(), e.getPropertyDescriptor());
			}
		}
		return descriptors;
	}

	
	@Override
	public Object getOwnProperty(String member, Object defaultValue, Object receiver) {
		CustomLinkedMap.EntryImpl<String> e = getEntry(member);
		if(e!=null) {
			return e.resolveValue(receiver);
		}
		if(member!=null&& !member.isEmpty() && member.charAt(0)=='$') {
			JSEnvironment env = getEnvironment();
			if(env!=null&& env.supportJavaNative()) {
				JSJavaLibrary lib = getEnvironment().getCustomLibraries().getJavaLibrary();
				if(lib!=null) {
					// return lib.getAccessor(receiver).getMember(receiver, member, defaultValue);
					// Should we used the native class instead here, to limit to the exposed methods?
					return getEnvironment().getAccessor(receiver.getClass()).getProperty(receiver, member, defaultValue);
				}
			}
		}
		return defaultValue;
	}
	@Override
	public Object getOwnProperty(Symbol symbol, Object defaultValue, Object receiver) {
		if(symbols!=null) {
			CustomLinkedMap.EntryImpl<Symbol> e = symbols.getEntry(symbol);
			if(e!=null) {
				return e.resolveValue(receiver);
			}
		}
		return defaultValue;
	}
	

	@Override
	public boolean setOwnProperty(String key, Object value, PropertyDescriptor descriptor, DESC_CHECK check, Object receiver) {
		return put(key,value,descriptor,check,receiver);
	}
	@Override
	public boolean setOwnProperty(Symbol key, Object value, PropertyDescriptor descriptor, DESC_CHECK check, Object receiver) {
		// Mirrors setOwnProperty(String,...)'s delegation to put(...)'s own
		// boolean result - previously hardcoded `true` regardless of
		// whether put() actually rejected the write (e.g. redefining a
		// non-configurable symbol property), silently discarding the
		// rejection instead of reporting it to the caller (confirmed via
		// Object/prototype/toString/symbol-tag-*-builtin.js: redefining a
		// Symbol.toStringTag accessor on a shared prototype singleton
		// appeared to succeed but never actually took effect).
		if(symbols==null) {
			symbols = new SymbolPropertyMap(this);
		}
		return symbols.put(key,value,descriptor,check,receiver);
	}

	@Override
	public boolean deleteProperty(String key, DESC_CHECK check) {
		return remove(key,check);
	}
	@Override
	public boolean deleteProperty(Symbol key, DESC_CHECK check) {
		// Mirrors deleteProperty(String,...)'s delegation to remove(...)'s
		// own boolean result - previously hardcoded `true` regardless of
		// whether the underlying non-configurable-property rejection fired,
		// so `delete obj[sym]` on a non-configurable symbol property
		// incorrectly reported success (confirmed via
		// defineProperty/symbol-data-property-default-non-strict.js/-strict.js).
		if(symbols!=null) {
			return symbols.remove(key,check);
		}
		return true;
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Override
	public Iterator<Map.Entry<Object,Object>> ownPropertyEntries(boolean strings, boolean symbols, boolean enumerableOnly) {
		return Iterators.concat(
				(Iterator)(strings ? entrySet(enumerableOnly).iterator() : null),
				(Iterator)(symbols && this.symbols!=null? this.symbols.entrySet(enumerableOnly).iterator() : null)
		);
	}

	//
	// Private class fields/methods/accessors ([[PrivateElements]]).
	// Deliberately NOT exposed through JSAccessor/getOwnProperty/setOwnProperty -
	// callers (ASTMember, ASTIn) invoke these directly, so private access can
	// never be intercepted by a Proxy trap, never walks the prototype chain,
	// and is never blocked by [[Extensible]]/frozen/sealed state, per spec.
	//

	@Override
	public boolean hasPrivateElement(PrivateName name) {
		return name!=null && privateElements!=null && privateElements.getEntry(name)!=null;
	}

	// Used only to merge a getter and setter declared separately (get #x()/
	// set #x()) into the SAME accessor descriptor for the SAME PrivateName.
	@Override
	public PropertyDescriptor getPrivateElementDescriptor(PrivateName name) {
		if(privateElements!=null) {
			CustomLinkedMap.EntryImpl<PrivateName> e = privateElements.getEntry(name);
			if(e!=null) {
				return e.getPropertyDescriptor();
			}
		}
		return null;
	}

	// PrivateFieldGet/PrivateMethodOrAccessorGet: NOT_AVAILABLE means "this
	// object's class did not declare this private name" - the caller is
	// responsible for turning that into the spec-mandated brand-check TypeError.
	@Override
	public Object getPrivateElementValue(PrivateName name) {
		if(name!=null && privateElements!=null) {
			CustomLinkedMap.EntryImpl<PrivateName> e = privateElements.getEntry(name);
			if(e!=null) {
				// PrivateFieldGet step 6.b: a private ACCESSOR (unlike a plain
				// field, or a REGULAR public set-only accessor - OrdinaryGet
				// correctly returns undefined for those, so this check is
				// deliberately scoped here rather than in the shared
				// EntryImpl.resolveValue() every property read goes through)
				// with no [[Get]] - i.e. set-only, `set #x(v){}` with no
				// matching `get #x()` - must throw TypeError on read.
				PropertyDescriptor d = e.getPropertyDescriptor();
				if(d!=null && d.isAccessor() && d.getGetter()==null) {
					throw RuntimeUtil.typeError("'{0}' was defined without a getter", name);
				}
				return e.resolveValue(this);
			}
		}
		return RuntimeUtil.NOT_AVAILABLE;
	}

	// PrivateFieldAdd/PrivateMethodOrAccessorAdd (construction time only):
	// always defines a fresh element - DESC_CHECK.NONE since there's no
	// existing entry to reject against yet.
	@Override
	public void definePrivateElement(PrivateName name, Object value, PropertyDescriptor descriptor) {
		if(privateElements==null) {
			privateElements = new PrivateElementsMap();
		}
		privateElements.put(name, value, descriptor, DESC_CHECK.NONE, this);
	}

	// PrivateFieldSet/PrivateMethodOrAccessorSet (runtime write, e.g.
	// `this.#x = v`): must already be declared - the caller is responsible for
	// the brand-check TypeError when this returns false. DESC_CHECK.STRICT
	// forces the "not writable"/"no setter" rejection to always throw,
	// matching private members always behaving strict-mode-like.
	@Override
	public boolean setPrivateElementValue(PrivateName name, Object value) {
		if(name!=null && privateElements!=null && privateElements.getEntry(name)!=null) {
			privateElements.put(name, value, null, DESC_CHECK.STRICT, this);
			return true;
		}
		return false;
	}


	//
	// To support Object literal
	//
	
	public JSObjectImpl litProp(Object key, Object value) {
		// PropertyDefinitionEvaluation: an ordinary `key: value` entry (and a
		// shorthand `{ident}`/computed `{[expr]: value}` entry, even when its
		// key happens to be the STRING "__proto__" - see litProtoProp()'s doc
		// for why only the literal, non-computed `__proto__: value` syntactic
		// form is special) uses CreateDataPropertyOrThrow
		// ([[DefineOwnProperty]]) - it must always become an OWN property,
		// ignoring (not consulting the writability of) any same-named
		// inherited accessor/data property, exactly like a class field.
		setOwnProperty(key, value, PropertyDescriptor.DESC_DEFAULT, DESC_CHECK.NONE, this);
		return this;
	}
	// B.3.1 __proto__ Property Names in Object Initializers: ONLY the exact
	// syntactic form `PropertyDefinition : PropertyName : AssignmentExpression`
	// with a non-computed "__proto__" PropertyName triggers this - never a
	// computed `['__proto__']: value` key (test262 __proto__-duplicate-
	// computed.js/computed-__proto__.js) and never the shorthand `{__proto__}`
	// form (test262 __proto__-permitted-dup-shorthand.js), both of which are
	// ordinary own data properties via litProp() instead - see the call
	// site's own doc (ASTObjectLiteral.InitializerFieldNameExpression).
	// [[SetPrototypeOf]] is called DIRECTLY here, never the ordinary [[Set]]
	// property-lookup path litProp() used to (mistakenly) share with this
	// case - so a user-poisoned Object.prototype.__proto__ accessor setter is
	// never invoked (test262 __proto__-poisoned-object-prototype.js) - and
	// nothing at all happens (no error, no own property added) for any value
	// that isn't an Object or null, per spec step "If Type(propValue) is
	// either Object or Null, then Return object.[[SetPrototypeOf]](propValue)".
	// Also, unlike litFunction(), this never names an anonymous function
	// value - NamedEvaluation is specifically skipped for this form (test262
	// __proto__-fn-name.js).
	public JSObjectImpl litProtoProp(Object value) {
		if(value instanceof JSObject || value==null) {
			setPrototype(value);
		}
		return this;
	}
	public JSObjectImpl litPutAll(Iterator<Map.Entry<Object,Object>> it) {
		// Object literal spread (`{...source}`): CopyDataProperties always
		// uses CreateDataProperty, even for a "__proto__"-named key (unlike
		// a literal `__proto__: value` entry, a SPREAD "__proto__" becomes
		// an ordinary own data property, never changes the prototype).
		if(it!=null) {
	    	while(it.hasNext()) {
	    		Map.Entry<Object,Object> e = it.next();
	    		Object k = e.getKey();
	    		Object v = e.getValue();
	    		setOwnProperty(k, v, PropertyDescriptor.DESC_DEFAULT, DESC_CHECK.NONE, this);
	    	}
		}
		return this;
	}
	// `value`: a BaseCallableObject rather than the narrower BuiltinFunction -
	// NamedEvaluation (SetFunctionName) applies to an anonymous CLASS
	// expression value too (`{id: class {}}` must get `.name === "id"`), not
	// just a function one, and BuiltinClassConstructor is a BaseCallableObject
	// (via BaseConstructor/BaseNativeMethod) but NOT a BuiltinFunction -
	// confirmed via test262 fn-name-class.js/fn-name-cover.js (the latter
	// only exercises plain functions, but shares this same call site).
	public JSObjectImpl litFunction(Object key, BaseCallableObject value) {
		Object name = value.getProperty("name");
		if(RuntimeUtil.isNullOrUndefined(name) || StringUtil.isEmpty(name.toString())) {
			// key.toString() would produce Symbol.prototype.toString()'s
			// "Symbol(description)" for a Symbol key, not SetFunctionName's
			// own "[description]" - see propertyKeyToFunctionName's doc.
			value.setOwnProperty("name",PropertyDescriptor.propertyKeyToFunctionName(key),null,DESC_CHECK.NONE); // Bypass read-only...
		}
        // Only a function value (not a class - BuiltinClassConstructor isn't
        // a HomeObject) needs [[HomeObject]] wired to this object; a class
        // expression used as a property value has no `super` binding need
        // tied to the surrounding object literal.
        if(value instanceof HomeObject ho) {
        	ho.setHomeObject(this);
        }
        // PropertyDefinitionEvaluation ALWAYS uses CreateDataPropertyOrThrow
        // ([[DefineOwnProperty]]), exactly like litProp() above, regardless of
        // whether the value happens to be a function - a plain setProperty()
        // ([[Set]]) here was wrong: for a COMPUTED `['__proto__']: function(){}`
        // key specifically (never special per B.3.1 - only the non-computed
        // literal form is, and that's routed through litProtoProp() instead,
        // never reaching here), [[Set]] would walk the prototype chain and
        // find Object.prototype's own real "__proto__" accessor, actually
        // reassigning [[Prototype]] to the function instead of creating an
        // ordinary own data property (test262 computed-__proto__.js).
        setOwnProperty(key, value, PropertyDescriptor.DESC_DEFAULT, DESC_CHECK.NONE, this);
		return this;
	}
	public JSObjectImpl litGetter(Object key, Object value) {
		return litGetter(key, value, PropertyDescriptor.DESC_DEFAULT);
	}
	// defaultDesc: the descriptor to seed a BRAND NEW property with (only its
	// writable/configurable/enumerable flags matter here - the getter/setter
	// themselves are always overridden below). Object-literal getters/setters
	// (the 2-arg overload above) default to DESC_DEFAULT (enumerable:true,
	// spec: object literal properties are enumerable) - but a CLASS getter/
	// setter (BuiltinClassConstructor.addClass{Static,}Getter/Setter) must
	// default to DESC_METHOD's shape instead (enumerable:false, spec: class
	// methods/accessors are never enumerable) - sharing this same merge logic
	// (an existing get/set pair on the same key keeps its OWN enumerable
	// flag either way, via `d.isEnumerable()` below) rather than duplicating
	// the getter/setter-merge dance for a second, class-specific pair of
	// methods.
	public JSObjectImpl litGetter(Object key, Object value, PropertyDescriptor defaultDesc) {
        if(value instanceof HomeObject bf) {
        	bf.setHomeObject(this);
        }
		if(value instanceof BaseCallableObject c) {
			c.put("name", PropertyDescriptor.getterName(key));
			PropertyDescriptor d = getOwnPropertyDescriptor(key);
			if(d==null) {
				d = defaultDesc;
			}
			d = PropertyDescriptor.of(
				d.isWritable(), d.isConfigurable(), d.isEnumerable(),
				c,
	    		d.getSetter()
	    	);
			if(key instanceof CharSequence cs) {
				setOwnProperty(cs.toString(),RuntimeUtil.NOT_AVAILABLE,d,DESC_CHECK.CHECK);
			} else if(key instanceof Number n) {
				setOwnProperty(RuntimeUtil.memberIndex(n.longValue()),RuntimeUtil.NOT_AVAILABLE,d,DESC_CHECK.CHECK);
			} else if(key instanceof Symbol sym) {
				setOwnProperty(sym,RuntimeUtil.NOT_AVAILABLE,d,DESC_CHECK.CHECK);
			} else {
				throw RuntimeUtil.typeError("Key is not valid, {0}",value!=null?value.getClass():"null");
			}
			return this;
		}
		throw RuntimeUtil.typeError("Getter is not a function, {0}",value!=null?value.getClass():"null");
	}
	public JSObjectImpl litSetter(Object key, Object value) {
		return litSetter(key, value, PropertyDescriptor.DESC_DEFAULT);
	}
	// See litGetter(Object,Object,PropertyDescriptor)'s comment for defaultDesc.
	public JSObjectImpl litSetter(Object key, Object value, PropertyDescriptor defaultDesc) {
        if(value instanceof HomeObject bf) {
        	bf.setHomeObject(this);
        }
		if(value instanceof BaseCallableObject c) {
			c.put("name", PropertyDescriptor.setterName(key));
			PropertyDescriptor d = getOwnPropertyDescriptor(key);
			if(d==null) {
				d = defaultDesc;
			}
			d = PropertyDescriptor.of(
				d.isWritable(), d.isConfigurable(), d.isEnumerable(),
				d.getGetter(),
				c
			);
			if(key instanceof CharSequence cs) {
				setOwnProperty(cs.toString(),RuntimeUtil.NOT_AVAILABLE,d,DESC_CHECK.CHECK);
			} else if(key instanceof Number n) {
				setOwnProperty(RuntimeUtil.memberIndex(n.longValue()),RuntimeUtil.NOT_AVAILABLE,d,DESC_CHECK.CHECK);
			} else if(key instanceof Symbol sym) {
				setOwnProperty(sym,RuntimeUtil.NOT_AVAILABLE,d,DESC_CHECK.CHECK);
			} else {
				throw RuntimeUtil.typeError("Key is not valid, {0}",value!=null?value.getClass():"null");
			}
			return this;
		}
		throw RuntimeUtil.typeError("Setter is not a function, {0}",value!=null?value.getClass():"null");
	}
	

	// For the  transpiler and to use a different class or method 
	public JSObjectImpl litProps(Consumer<JSObjectImpl> builder) {
		builder.accept(this);
		return this;
	}	
}
