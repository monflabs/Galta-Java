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
package org.monflabs.galtajs.rt.builtins.standard.map;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.rt.builtins.PropertyDescriptor;
import org.monflabs.galtajs.rt.builtins.primitives.symbol.Symbol;
import org.monflabs.galtajs.rt.builtins.standard.iterator.BuiltinIteratorHelperPrototype;

/**
 * Map iterator prototype.
 */
public class BuiltinMapIteratorPrototype extends BuiltinIteratorHelperPrototype {

	public static final String CLASSNAME = "Map Iterator";

	public static BuiltinMapIteratorPrototype get(JSEnvironment env) {
		BuiltinMapIteratorPrototype proto = (BuiltinMapIteratorPrototype)env.getRegisteredPrototype(BuiltinMapIteratorPrototype.class);
		if(proto==null) {
			proto = new BuiltinMapIteratorPrototype(env);
			env.registerPrototype(BuiltinMapIteratorPrototype.class,proto);
		}
		return proto;
	}
	
	private BuiltinMapIteratorPrototype(JSEnvironment env) {
		super(env);
		setOwnProperty(Symbol.TO_STRING_TAG,CLASSNAME,PropertyDescriptor.DESC_PROP_TOSTRINGTAG);
	}
}