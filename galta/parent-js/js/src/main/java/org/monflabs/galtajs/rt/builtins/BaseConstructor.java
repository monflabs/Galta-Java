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

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.library.java.JavaClass;
import org.monflabs.galtajs.rt.RuntimeUtil;

/**
 * 
 */
public abstract class BaseConstructor extends BaseNativeMethod implements Constructor, JavaClass {
		
	protected BaseConstructor(JSEnvironment env, String name, BaseInternalObject prototypeOfConstructed, int ctorLength) {
		super(env);
		// Own-property insertion order matters for Object.getOwnPropertyNames();
		// spec-created function/constructor objects get "length" before "name".
		setOwnProperty("length",ctorLength,PropertyDescriptor.DESC_PROP_READONLY_CONFIGURABLE);
		setOwnProperty("name",name,PropertyDescriptor.DESC_PROP_READONLY_CONFIGURABLE);
		setOwnProperty(Constructor.PROTOTYPE, prototypeOfConstructed, PropertyDescriptor.DESC_PROP_BUILTIN_PROTOTYPE);
		prototypeOfConstructed.setOwnProperty(Constructor.CONSTRUCTOR,this,PropertyDescriptor.DESC_PROP_CONSTRUCTOR);
	}
	
	@Override
	public abstract Class<?> getNativeClass();
	
	@Override
	public String getId() {
		return (String)getOwnProperty("name");
	}

	public Object getCreationPrototype() {
		return getOwnProperty(Constructor.PROTOTYPE, null);
	}

	// GetPrototypeFromConstructor applied to an already-constructed instance:
	// when called via a subclass's super(...) (or Reflect.construct's explicit
	// newTarget), the created instance's prototype must come from newTarget's
	// own "prototype" property rather than this base constructor's fixed one.
	// No-op when newTarget is this constructor itself (the common case).
	protected <T> T applyNewTargetPrototype(T instance, Constructor newTarget) {
		if(newTarget!=null && newTarget!=this) {
			Object proto = RuntimeUtil.getPrototypeFromConstructor(getEnvironment(), newTarget, getCreationPrototype());
			RuntimeUtil.setPrototype(getEnvironment(), instance, proto);
		}
		return instance;
	}

	@Override
	public Object call(Object _this, Object[] parameters) {
		// Make the function call equivalent to the ctor
		return constructObject(parameters);
	}

	@Override
	public Object constructObject(Object[] parameters, Constructor topConstructor) {
		String className = getEnvironment().getAccessor(this).getClassName(this);
		throw RuntimeUtil.typeError("{0} is not a constructor",className);
	}
	
	@Override
	public Object constructArray(int dimensions, long size) {
		String className = getEnvironment().getAccessor(this).getClassName(this);
		throw RuntimeUtil.typeError("{0} is not an array constructor",className);
	}
	
}
