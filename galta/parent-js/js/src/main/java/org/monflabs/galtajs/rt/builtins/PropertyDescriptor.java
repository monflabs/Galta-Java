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

import java.util.function.Supplier;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.jsonfactory.JSObject;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.builtins.primitives.symbol.Symbol;

/**
 * Object Property descriptor.
 */
public class PropertyDescriptor implements Cloneable {
	
	private static PropertyDescriptor[] DESCRIPTORS = new PropertyDescriptor[] {
		new PropertyDescriptor(false,false,false), 
		new PropertyDescriptor(true,false,false),
		new PropertyDescriptor(false,true,false),
		new PropertyDescriptor(true,true,false),
		new PropertyDescriptor(false,false,true),
		new PropertyDescriptor(true,false,true),
		new PropertyDescriptor(false,true,true),
		new PropertyDescriptor(true,true,true)
	};
	
	// Data descriptor
	public static PropertyDescriptor of(boolean writable, boolean configurable, boolean enumerable) {
		int idx = (writable ? 1 : 0) + (configurable ? 2 : 0) + (enumerable ? 4 : 0);
		return DESCRIPTORS[idx];
	}
	// Accessor descriptor
	public static PropertyDescriptor of(boolean writable, boolean configurable, boolean enumerable, BaseCallableObject getter, BaseCallableObject setter) {
		return new PropertyDescriptor(writable, configurable, enumerable, getter, setter);
	}

	// Data descriptor with explicit field-PRESENCE tracking - for converting
	// FROM a real, user-supplied JS descriptor object (ToPropertyDescriptor,
	// spec 6.2.6.5), where a field being ABSENT from the source object is
	// observably different from it being explicitly set to false (e.g.
	// `Reflect.defineProperty(proxy,"p",{})` - an EMPTY descriptor - must
	// NOT be treated as "configurable explicitly set to false" by
	// ProxyAccessor.validateDefineOwnPropertyResult()'s settingConfigFalse
	// check, spec 10.5.6 step 8). Every OTHER factory above builds a
	// descriptor for the ENGINE'S OWN properties (never derived from user
	// input), where every field is implicitly fully present - hence the
	// hasWritable/hasConfigurable/hasEnumerable fields below default to
	// `true` and only this factory (and its accessor-descriptor sibling)
	// ever pass `false` for one.
	public static PropertyDescriptor of(boolean writable, boolean configurable, boolean enumerable,
			boolean hasWritable, boolean hasConfigurable, boolean hasEnumerable) {
		PropertyDescriptor d = new PropertyDescriptor(writable, configurable, enumerable);
		d.hasWritable = hasWritable;
		d.hasConfigurable = hasConfigurable;
		d.hasEnumerable = hasEnumerable;
		return d;
	}
	// Accessor descriptor with explicit field-presence tracking - same
	// rationale as above. An accessor descriptor never has a [[Writable]]
	// field at the spec level at all, so there's no hasWritable parameter.
	public static PropertyDescriptor of(boolean configurable, boolean enumerable, BaseCallableObject getter, BaseCallableObject setter,
			boolean hasConfigurable, boolean hasEnumerable) {
		PropertyDescriptor d = new PropertyDescriptor(false, configurable, enumerable, getter, setter);
		d.hasConfigurable = hasConfigurable;
		d.hasEnumerable = hasEnumerable;
		return d;
	}

	
	public static final PropertyDescriptor DESC_DEFAULT = PropertyDescriptor.of(true,true,true);
	public static final PropertyDescriptor DESC_DEFAULT_DEFINE = PropertyDescriptor.of(false,false,false);
	
	public static final PropertyDescriptor DESC_READONLY_PROP = PropertyDescriptor.of(false,false,true);
	public static final PropertyDescriptor DESC_READONLY_HIDDEN_PROP = PropertyDescriptor.of(false,false,false);
	public static final PropertyDescriptor DESC_HIDDEN_PROP = PropertyDescriptor.of(true,false,false);
	public static final PropertyDescriptor DESC_FIXED_PROP = PropertyDescriptor.of(true,false,true);
	public static final PropertyDescriptor DESC_FIXED_METHOD = PropertyDescriptor.of(false,false,true);

	public static final PropertyDescriptor DESC_OLD_METHOD = PropertyDescriptor.of(true,true,false);
	public static final PropertyDescriptor DESC_METHOD = PropertyDescriptor.of(true,true,false);
	// Spec: a function's own "prototype" property is { writable: true, enumerable: false, configurable: false }.
	public static final PropertyDescriptor DESC_FUNCTION_PROTOTYPE = PropertyDescriptor.of(true,false,false);
	public static final PropertyDescriptor DESC_STATICFIELDS = DESC_READONLY_HIDDEN_PROP;

	public static final PropertyDescriptor DESC_JAVA_FIELD = PropertyDescriptor.of(true,false,true);
	public static final PropertyDescriptor DESC_JAVA_METHOD = PropertyDescriptor.of(false,false,true);

	public static final PropertyDescriptor DESC_PROP_BUILTIN_PROTOTYPE = DESC_READONLY_HIDDEN_PROP;
	public static final PropertyDescriptor DESC_PROP_DEFINE_DEFAULT = DESC_READONLY_HIDDEN_PROP;
	public static final PropertyDescriptor DESC_PROP_PROTOTYPE = DESC_HIDDEN_PROP;
	public static final PropertyDescriptor DESC_PROP_CONSTRUCTOR = DESC_OLD_METHOD;
	public static final PropertyDescriptor DESC_PROP_ARRAYINDEX = PropertyDescriptor.of(true,true,true);
	public static final PropertyDescriptor DESC_PROP_GLOBALTHIS = PropertyDescriptor.of(true,true,false);
	
	public static final PropertyDescriptor DESC_PROP_READONLY_CONFIGURABLE = PropertyDescriptor.of(false,true,false);
	public static final PropertyDescriptor DESC_PROP_TOSTRINGTAG = DESC_PROP_READONLY_CONFIGURABLE;
	public static final PropertyDescriptor DESC_PROP_UNSCOPABLE = DESC_PROP_READONLY_CONFIGURABLE;
	public static final PropertyDescriptor DESC_PROP_TOPRIMITIVE = DESC_PROP_READONLY_CONFIGURABLE;
	public static final PropertyDescriptor DESC_PROP_FCTPROP = DESC_PROP_READONLY_CONFIGURABLE;
	
	
	private boolean writable;
	private boolean configurable;
	private boolean enumerable;

	// Field-presence tracking (see the two presence-aware `of(...)` factories
	// above) - defaults to `true` so every pre-existing construction path
	// (the interned DESCRIPTORS[], the plain 3-/5-arg factories used
	// throughout the engine for its OWN properties) is unaffected: those
	// descriptors were always "fully specified" anyway.
	private boolean hasWritable = true;
	private boolean hasConfigurable = true;
	private boolean hasEnumerable = true;

	private BaseCallableObject getter;
	private BaseCallableObject setter;
	// Kind is tracked explicitly, NOT inferred from getter/setter nullness -
	// an accessor property's getter and setter can both be explicitly
	// undefined (Object.defineProperty(o,"p",{get:undefined,set:undefined,...}))
	// while still genuinely being an ACCESSOR descriptor, distinct from a
	// plain data property (which also has getter==null&&setter==null). Only
	// the 5-arg accessor factory sets this true; the 3-arg data factory
	// (whose instances are the ONLY ones ever interned, via DESCRIPTORS[])
	// always sets it false - safe to add without disturbing that sharing,
	// since the 5-arg factory never interned anyway (confirmed via
	// defineProperty/15.2.3.6-4-{254,430,439,448,457}.js).
	private final boolean accessor;

	private PropertyDescriptor(boolean writable, boolean configurable, boolean enumerable) {
		this.writable = writable;
		this.configurable = configurable;
		this.enumerable = enumerable;
		this.accessor = false;
	}
	private PropertyDescriptor(boolean writable, boolean configurable, boolean enumerable, BaseCallableObject getter, BaseCallableObject setter) {
		this.writable = writable;
		this.configurable = configurable;
		this.enumerable = enumerable;
		this.getter = getter;
		this.setter = setter;
		this.accessor = true;
	}
	private PropertyDescriptor(PropertyDescriptor d) {
		this.writable = d.writable;
		this.configurable = d.configurable;
		this.enumerable = d.enumerable;
		this.hasWritable = d.hasWritable;
		this.hasConfigurable = d.hasConfigurable;
		this.hasEnumerable = d.hasEnumerable;
		this.getter = d.getter;
		this.setter = d.setter;
		this.accessor = d.accessor;
	}

	@Override
	public boolean equals(Object o) {
		if(o==this) {
			return true;
		}
		if(o instanceof PropertyDescriptor d) {
			return     d.writable==writable
					&& d.configurable==configurable
					&& d.enumerable==enumerable
					&& d.hasWritable==hasWritable
					&& d.hasConfigurable==hasConfigurable
					&& d.hasEnumerable==hasEnumerable
					&& d.accessor==accessor
					&& d.getter==getter
					&& d.setter==setter;
		}
		return false;
	}

	public boolean isData() {
		return !accessor;
	}

	public boolean isAccessor() {
		return accessor;
	}
	
	@Override
	public PropertyDescriptor clone() {
		return new PropertyDescriptor(this);
	}

	// To be removed!
	public boolean isWritable() {
		return writable;
	}

	public boolean isConfigurable() {
		return configurable;
	}

	public boolean isEnumerable() {
		return enumerable;
	}

	// Field-presence: whether the ORIGINAL descriptor object this was built
	// from actually mentioned this field, vs it being defaulted/absent - see
	// the presence-aware `of(...)` factories above. Always `true` for a
	// descriptor the engine built for one of its own properties.
	public boolean hasWritable() {
		return hasWritable;
	}

	public boolean hasConfigurable() {
		return hasConfigurable;
	}

	public boolean hasEnumerable() {
		return hasEnumerable;
	}

	public BaseCallableObject getGetter() {
		return getter;
	}

	public BaseCallableObject getSetter() {
		return setter;
	}
	
	// FromPropertyDescriptor's CreateDataProperty order is spec-mandated
	// (value/writable/enumerable/configurable, or get/set/enumerable/configurable)
	// - it's observable via Object.getOwnPropertyNames()/for-in on the result.
	public JSObject asJSObject(JSEnvironment env, Supplier<Object> valueProvider) {
		JSObject o = JSObject.create(env);
		if(accessor) {
			o.setOwnProperty("get",getter!=null ? getter : RuntimeUtil.UNDEFINED);
			o.setOwnProperty("set",setter!=null ? setter : RuntimeUtil.UNDEFINED);
		} else {
			if(valueProvider!=null) {
				Object value = valueProvider.get();
				if(value!=RuntimeUtil.NOT_AVAILABLE) {
					o.setOwnProperty("value",value);
				}
			}
			o.setOwnProperty("writable",writable);
		}
		o.setOwnProperty("enumerable",enumerable);
		o.setOwnProperty("configurable",configurable);
		return o;
	}
	
    // SetFunctionName's plain (non-getter/setter) property-key-to-name
    // conversion (spec 10.2.11 step 1.b): a Symbol key with no description
    // becomes "", one with a description becomes "[description]" - NOT
    // Symbol.prototype.toString()'s own "Symbol(description)" format, which
    // Object.toString()-based callers (e.g. Java's key.toString()) would
    // otherwise produce.
    public static String propertyKeyToFunctionName(Object key) {
    	if(key instanceof Symbol sym) {
    		Object d = sym.getDescription();
    		if(RuntimeUtil.isNullOrUndefined(d)) {
    			return "";
    		}
    		return "["+d.toString()+"]";
    	}
    	return key.toString();
    }

    public static String getterName(Object prop) {
    	if(prop instanceof Symbol sym) {
    		Object d = sym.getDescription();
    		if(RuntimeUtil.isNullOrUndefined(d)) {
    			return "get ";
    		}
    		return "get ["+d.toString()+"]";
    	}
    	return "get "+prop.toString();
    }
    public static String setterName(Object prop) {
    	if(prop instanceof Symbol sym) {
    		Object d = sym.getDescription();
    		if(RuntimeUtil.isNullOrUndefined(d)) {
    			return "set ";
    		}
    		return "set ["+d.toString()+"]";
    	}
    	return "set "+prop.toString();
    }

}
