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
package org.monflabs.galtajs.rt.builtins.primitives;

import java.util.Iterator;
import java.util.Map;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.jsonfactory.JSObject;
import org.monflabs.galtajs.jsonfactory.JSObject.DESC_CHECK;
import org.monflabs.galtajs.jsonfactory.internal.JSObjectInternal;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.builtins.JSAccessor;
import org.monflabs.galtajs.rt.builtins.PropertiesHolder;
import org.monflabs.galtajs.rt.builtins.PropertyDescriptor;
import org.monflabs.galtajs.rt.builtins.primitives.object.BuiltinObjectPrototype;
import org.monflabs.galtajs.rt.builtins.primitives.symbol.Symbol;
import org.monflabs.galtajs.rt.util.PrimitivePropertyMap;
import org.monflabs.util.iterators.Iterators;

/**
 * 
 */
public abstract class ObjectWrapperAccessor extends JSAccessor {

	protected ObjectWrapperAccessor(JSEnvironment env) {
		super(env);
	}
	
	//protected abstract JSObject getPropertiesObject(Object _this, boolean forWrite);
	protected JSObject getPropertiesObject(Object _this, boolean forWrite) {
		// If the Object contains its properties, ask it
		if(_this instanceof PropertiesHolder ph) {
			JSObjectInternal props = ph.getPropertiesObject();
			if(props==null && forWrite) {
				props = JSObject.createWithPrototype(getEnvironment(),getDefaultPrototype());
				ph.setPropertiesObject(props);
			}
			return props;
		}
		
		// Else use the weak side table for Java objects that carry no storage
		// of their own (java.util.Date, HashMap, any host object). Kept
		// separate from the boxed-primitive tables so that expandos on host
		// objects - by far the most common traffic here - can never disable
		// the primitive fast paths elsewhere in the engine.
		JSEnvironment env = getEnvironment();
		PrimitivePropertyMap map = env.getObjectProperties(forWrite);
		if(map!=null) {
			// We auto create the properties for non primitive objects
			// For example, a HashMap, as we never use create() for these objects
			return forWrite ? map.getOrCreate(_this, () -> JSObject.createWithPrototype(getEnvironment(),getDefaultPrototype())) : map.get(_this);
		}
		return null;
	}
	protected Object getDefaultPrototype() {
		return BuiltinObjectPrototype.get(getEnvironment());
	}
	
	
	// -----------------------------------------------------------------------------------
	// GaltaJS extension: get the name
	//public abstract String getClassName(Object _this);

	
	// -----------------------------------------------------------------------------------
	// [[GetPrototypeOf]]

	@Override
	public Object getPrototype(Object _this) {
		JSObject props = getPropertiesObject(_this,false);
		if(props!=null) {
			return props.getPrototype();
		}
		return getDefaultPrototype();
	}	
	
	
	// -----------------------------------------------------------------------------------
	// [[SetPrototypeOf]]
	
	@Override
	public boolean setPrototype(Object _this, Object prototype) {
		// forWrite=true: unlike a read, setting the prototype must not be a
		// no-op just because no property has been set on this instance yet
		// (e.g. a freshly-constructed Date/ArrayBuffer/WeakRef).
		JSObject props = getPropertiesObject(_this,true);
		if(props!=null) {
			return props.setPrototype(prototype);
		}
		return false;
	}
	
	
	// -----------------------------------------------------------------------------------
	// [[IsExtensible]]
	@Override
	public boolean isExtensible(Object _this) {
		JSObject props = getPropertiesObject(_this,false);
		if(props!=null) {
			return props.isExtensible();
		}
		return true;
	}
	@Override
	public boolean isSealed(Object _this) {
		JSObject props = getPropertiesObject(_this,false);
		if(props!=null) {
			return props.isSealed();
		}
		return false;
	}
	@Override
	public boolean isFrozen(Object _this) {
		JSObject props = getPropertiesObject(_this,false);
		if(props!=null) {
			return props.isFrozen();
		}
		return false;
	}

	
	// -----------------------------------------------------------------------------------
	// [[PreventExtensions]]
	@Override
	public boolean preventExtensions(Object _this) {
		JSObject props = getPropertiesObject(_this,true);
		if(props!=null) { // we still test because primitive do not auot-create a property object
			return props.preventExtensions();
		}
		return false;
	}
	@Override
	public void seal(Object _this) {
		JSObject props = getPropertiesObject(_this,true);
		if(props!=null) { // we still test because primitive do not auot-create a property object
			props.seal();
		}
	}
	@Override
	public void freeze(Object _this) {
		JSObject props = getPropertiesObject(_this,true);
		if(props!=null) { // we still test because primitive do not auot-create a property object
			props.freeze();
		}
	}
	
	
	// -----------------------------------------------------------------------------------
	// [[GetOwnProperty]]
	
	@Override
	public PropertyDescriptor getOwnPropertyDescriptor(Object _this, String member) {
		JSObject props = getPropertiesObject(_this,false);
		if(props!=null) {
			return props.getOwnPropertyDescriptor(member);
		}
		return null;
	}
	@Override
	public PropertyDescriptor getOwnPropertyDescriptor(Object _this, long index) {
		JSObject props = getPropertiesObject(_this,false);
		if(props!=null) {
			return props.getOwnPropertyDescriptor(index);
		}
		return null;
	}
	@Override
	public PropertyDescriptor getOwnPropertyDescriptor(Object _this, Symbol symbol) {
		JSObject props = getPropertiesObject(_this,false);
		if(props!=null) {
			return props.getOwnPropertyDescriptor(symbol);
		}
		return null;
	}
	
	@Override
	public JSObject getOwnPropertyDescriptors(JSObject descriptors, Object _this) {
		JSObject props = getPropertiesObject(_this,false);
		if(props!=null) {
			return props.getOwnPropertyDescriptors(descriptors);
		}
		return descriptors;
	}


	// -----------------------------------------------------------------------------------
	// [[HasProperty]]
	
	// Default is fine
	
	
	// -----------------------------------------------------------------------------------
	// [[GetOwnProperty]]
	
	@Override
	public Object getOwnProperty(Object _this, String member, Object defaultValue, Object receiver) {
		JSObject c = getPropertiesObject(_this,false);
		if(c!=null) {
			return c.getOwnProperty(member,defaultValue,receiver);
		}
		return defaultValue;
	}
	@Override
	public Object getOwnProperty(Object _this, long index, Object defaultValue, Object receiver) {
		JSObject c = getPropertiesObject(_this,false);
		if(c!=null) {
			return c.getOwnProperty(index,defaultValue,receiver);
		}
		return defaultValue;
	}
	@Override
	public Object getOwnProperty(Object _this, Symbol symbol, Object defaultValue, Object receiver) {
		JSObject c = getPropertiesObject(_this,false);
		if(c!=null) {
			return c.getOwnProperty(symbol,defaultValue,receiver);
		}
		return defaultValue;
	}
	
	
	// -----------------------------------------------------------------------------------
	// [[Get]]
	
	// Direct access to getPropertiesObject().getProperty() fails because getOwnProperty
	// may implement virtual properties (ex: String.length) so we rely on the generic
	// implementation
	
	
	// -----------------------------------------------------------------------------------
	// [[Set]]
	
	@Override
	public boolean setOwnProperty(Object _this, String member, Object value, PropertyDescriptor desc, DESC_CHECK check, Object receiver) {
		JSObject c = getPropertiesObject(_this,true);
		if(c!=null) { // we still test because primitive do not auot-create a property object
			return c.setOwnProperty(member, value, desc, check, receiver);
		}
		if(RuntimeUtil.isStrictMode()) {
			throw RuntimeUtil.typeError("Cannot assign property {0} to object {1}", member, _this);
		}
		return false;
	}
	@Override
	public boolean setOwnProperty(Object _this, long index, Object value, PropertyDescriptor desc, DESC_CHECK check, Object receiver) {
		JSObject c = getPropertiesObject(_this,true);
		if(c!=null) { // we still test because primitive do not auot-create a property object
			return c.setOwnProperty(Long.toString(index), value, desc, check, receiver);
		}
		if(RuntimeUtil.isStrictMode()) {
			throw RuntimeUtil.typeError("Cannot assign property {0} to object {1}", index, _this);
		}
		return false;
	}
	@Override
	public boolean setOwnProperty(Object _this, Symbol symbol, Object value, PropertyDescriptor desc, DESC_CHECK check, Object receiver) {
		JSObject c = getPropertiesObject(_this,true);
		if(c!=null) { // we still test because primitive do not auot-create a property object
			return c.setOwnProperty(symbol, value, desc, check, receiver);
		}
		if(RuntimeUtil.isStrictMode()) {
			throw RuntimeUtil.typeError("Cannot assign property {0} to object {1}", symbol, _this);
		}
		return false;
	}

	// Direct access to getPropertiesObject().setProperty() fails because getOwnProperty
	// may implement virtual properties (ex: TypedArrayAccessor[]) so we rely on the generic
	// implementation

	
	// -----------------------------------------------------------------------------------
	// [[Delete]]
	
	@Override
	public boolean deleteProperty(Object _this, String member, DESC_CHECK check) {
		JSObject props = getPropertiesObject(_this,false);
		if(props!=null) {
			return props.deleteProperty(member,check);
		}
		return true;
	}
	@Override
	public boolean deleteProperty(Object _this, long index, DESC_CHECK check) {
		JSObject props = getPropertiesObject(_this,false);
		if(props!=null) {
			return props.deleteProperty(index,check);
		}
		return true;
	}
	@Override
	public boolean deleteProperty(Object _this, Symbol member, DESC_CHECK check) {
		JSObject props = getPropertiesObject(_this,false);
		if(props!=null) {
			return props.deleteProperty(member,check);
		}
		return true;
	}
	
	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Override
	public Iterator<Map.Entry<Object,Object>> ownPropertyEntries(Object _this, boolean strings, boolean symbols, boolean enumerableOnly) {
		JSObject props = getPropertiesObject(_this,false);
		if(props!=null) {
			return Iterators.concat(
					strings ? (Iterator)props.ownPropertyEntries(enumerableOnly) : null,
					symbols ? props.ownPropertySymbolEntries(enumerableOnly) : null );
		}
		return Iterators.empty();
	}
	
	
	// -----------------------------------------------------------------------------------
	// [[OwnPropertyKeys]]
}
