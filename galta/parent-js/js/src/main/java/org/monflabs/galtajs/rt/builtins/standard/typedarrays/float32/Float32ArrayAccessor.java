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
package org.monflabs.galtajs.rt.builtins.standard.typedarrays.float32;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.rt.builtins.standard.typedarrays.TypedArrayAccessor;

public class Float32ArrayAccessor extends TypedArrayAccessor {
	
	public Float32ArrayAccessor(JSEnvironment env) {
		super(env);
	}

	@Override
	protected Object getDefaultPrototype() {
		return Float32ArrayPrototype.get(getEnvironment());
	}

	@Override
	public String getClassName(Object _this) {
		return Float32ArrayConstructor.CLASSNAME;
	}	
}
