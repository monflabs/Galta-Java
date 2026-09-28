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
package org.monflabs.galtajs.rt.builtins.standard.set;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.rt.builtins.PropertyDescriptor;
import org.monflabs.galtajs.rt.builtins.primitives.symbol.Symbol;
import org.monflabs.galtajs.rt.builtins.standard.iterator.BuiltinIteratorHelperPrototype;

/**
 * Set iterator prototype.
 */
public class BuiltinSetIteratorPrototype extends BuiltinIteratorHelperPrototype {

	public static final String CLASSNAME = "Set Iterator";

	public static BuiltinSetIteratorPrototype get(JSEnvironment env) {
		BuiltinSetIteratorPrototype proto = (BuiltinSetIteratorPrototype)env.getRegisteredPrototype(BuiltinSetIteratorPrototype.class);
		if(proto==null) {
			proto = new BuiltinSetIteratorPrototype(env);
			env.registerPrototype(BuiltinSetIteratorPrototype.class,proto);
		}
		return proto;
	}
	
	private BuiltinSetIteratorPrototype(JSEnvironment env) {
		super(env);
		setOwnProperty(Symbol.TO_STRING_TAG,CLASSNAME,PropertyDescriptor.DESC_PROP_TOSTRINGTAG);
	}
}