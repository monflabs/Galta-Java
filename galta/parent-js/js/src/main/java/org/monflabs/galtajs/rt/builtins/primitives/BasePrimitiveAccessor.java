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

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.jsonfactory.JSObject;
import org.monflabs.galtajs.jsonfactory.internal.JSObjectInternal;
import org.monflabs.galtajs.rt.util.PrimitivePropertyMap;

/**
 * 
 */
public abstract class BasePrimitiveAccessor extends ObjectWrapperAccessor {
	
	protected BasePrimitiveAccessor(JSEnvironment env) {
		super(env);
	}

	// The side table this accessor's values live in, or null when nothing of
	// this type has been boxed yet. Every primitive accessor knows its own
	// type statically, so each implementation is a direct field read with no
	// type dispatch at all.
	protected abstract PrimitivePropertyMap propertyMap();

	@Override
	protected JSObjectInternal getPropertiesObject(Object _this, boolean forWrite) {
		// Don't need to check if the object holds its own properties.
		//
		// `forWrite` is deliberately ignored. We never auto create the
		// properties for a primitive type - they are only created when the
		// primitive is actually wrapped, as we don't want to assign an object
		// to an existing primitive (that changes the primitive->object) - so
		// materialising the table here would flip it non-null without ever
		// storing anything, and a non-null table is precisely what the
		// arithmetic/comparison inline caches test to take their fast path.
		PrimitivePropertyMap map = propertyMap();
		if(map!=null) {
			return map.get(_this);
		}
		return null;
	}

	
	// -----------------------------------------------------------------------------------
	// [[IsExtensible]]
	
	// There is a special treatment of the default values for primitives!
	
	@Override
	public boolean isExtensible(Object _this) {
		JSObject props = getPropertiesObject(_this,false);
		if(props!=null) {
			return props.isExtensible();
		}
		return false;
	}
	@Override
	public boolean isSealed(Object _this) {
		JSObject props = getPropertiesObject(_this,false);
		if(props!=null) {
			return props.isSealed();
		}
		return true;
	}
	@Override
	public boolean isFrozen(Object _this) {
		JSObject props = getPropertiesObject(_this,false);
		if(props!=null) {
			return props.isFrozen();
		}
		return true;
	}	
}
