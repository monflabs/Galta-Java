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
package org.monflabs.galtajs.rt.builtins.standard.typedarrays.uint32;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.rt.builtins.BasePrototype;
import org.monflabs.galtajs.rt.builtins.PropertyDescriptor;
import org.monflabs.galtajs.rt.builtins.standard.typedarrays.TypedArrayPrototype;

public class Uint32ArrayPrototype extends BasePrototype {

	public static Uint32ArrayPrototype get(JSEnvironment env) {
		Uint32ArrayPrototype proto = (Uint32ArrayPrototype)env.getRegisteredPrototype(Uint32ArrayPrototype.class);
		if(proto==null) {
			proto = new Uint32ArrayPrototype(env);
			env.registerPrototype(Uint32ArrayPrototype.class,proto);
		}
		return proto;
	}
	
	private Uint32ArrayPrototype(JSEnvironment env) {
		super(env);
		setOwnProperty("BYTES_PER_ELEMENT",Uint32ArrayConstructor.BYTES_PER_ELEMENT,PropertyDescriptor.DESC_READONLY_HIDDEN_PROP);
	}

	@Override
	protected Object getDefaultPrototype() {
		return TypedArrayPrototype.get(getEnvironment());
	}
}