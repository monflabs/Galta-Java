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
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.builtins.standard.function.BuiltinFunctionConstructor;
import org.monflabs.galtajs.rt.builtins.standard.function.BuiltinFunctionPrototype;

/**
 * Contains helpers for callable objects.
 */
public abstract class BaseCallableObject extends BaseInternalObject implements Callable {

	protected BaseCallableObject(JSEnvironment env) {
		super(env);
	}
	
	@Override
	public String getClassName() {
		return BuiltinFunctionConstructor.CLASSNAME;
	}	
	
	@Override
	protected Object getDefaultPrototype() {
		return BuiltinFunctionPrototype.get(getEnvironment());
	}
	
	public String getFunctionName() {
		return (String)getProperty("name",null);
	}
	public void setFunctionName(String name) {
		setOwnProperty("name",name,PropertyDescriptor.DESC_PROP_FCTPROP);
	}

	@Override
	public String toString() {
		return "Function";
	}
	
	
	//
	// Specialized functions
	//

	// Getter
	public Object get(Object base, Object key) {
		return call(base,RuntimeUtil.EMPTY_PARAMS);
	}

	// Setter
	public boolean set(Object base, Object key, Object value) {
		return RuntimeUtil.toBoolean(getEnvironment(), call(base, new Object[]{value}));
	}
}
