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
package org.monflabs.galtajs.rt.builtins.primitives.number;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.rt.builtins.primitives.BasePrimitiveAccessor;

/**
 * 
 */
public class NumberAccessor extends BasePrimitiveAccessor {

	// boxed Numbers
	@Override
	protected org.monflabs.galtajs.rt.util.PrimitivePropertyMap propertyMap() {
		return getEnvironment().getNumberProperties();
	}
	
	public NumberAccessor(JSEnvironment env) {
		super(env);
	}
	
	@Override
	protected Object getDefaultPrototype() {
		return BuiltinNumberPrototype.get(getEnvironment());
	}

	@Override
	public String getClassName(Object _this) {
		return BuiltinNumberConstructor.CLASSNAME;
	}	
}
