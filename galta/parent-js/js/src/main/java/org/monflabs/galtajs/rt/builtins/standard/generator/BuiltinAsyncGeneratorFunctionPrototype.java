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
package org.monflabs.galtajs.rt.builtins.standard.generator;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.rt.builtins.BasePrototype;
import org.monflabs.galtajs.rt.builtins.PropertyDescriptor;
import org.monflabs.galtajs.rt.builtins.primitives.symbol.Symbol;
import org.monflabs.galtajs.rt.builtins.standard.function.BuiltinFunctionPrototype;

/**
 * %AsyncGeneratorFunction.prototype% - an async generator function's own
 * [[Prototype]] (distinct from that function's own "prototype" PROPERTY,
 * which async generator INSTANCES chain through - see
 * BuiltinAsyncGeneratorPrototype). Mirrors BuiltinGeneratorFunctionPrototype
 * exactly - see its javadoc for the full rationale.
 */
public class BuiltinAsyncGeneratorFunctionPrototype extends BasePrototype {

	public static BuiltinAsyncGeneratorFunctionPrototype get(JSEnvironment env) {
		BuiltinAsyncGeneratorFunctionPrototype proto = (BuiltinAsyncGeneratorFunctionPrototype)env.getRegisteredPrototype(BuiltinAsyncGeneratorFunctionPrototype.class);
		if(proto==null) {
			proto = new BuiltinAsyncGeneratorFunctionPrototype(env);
			// Registered BEFORE constructing the Constructor (which itself
			// calls back into this same get(env)) to avoid infinite
			// recursion / a duplicate prototype instance.
			env.registerPrototype(BuiltinAsyncGeneratorFunctionPrototype.class,proto);
			new BuiltinAsyncGeneratorFunctionConstructor(env);
		}
		return proto;
	}

	private BuiltinAsyncGeneratorFunctionPrototype(JSEnvironment env) {
		super(env);
		setOwnProperty(Symbol.TO_STRING_TAG,"AsyncGeneratorFunction",PropertyDescriptor.DESC_PROP_TOSTRINGTAG);
		// 27.4.3.3 AsyncGeneratorFunction.prototype.prototype - a data
		// property pointing at the shared %AsyncGeneratorPrototype%.
		BuiltinAsyncGeneratorPrototype asyncGeneratorPrototype = BuiltinAsyncGeneratorPrototype.get(env);
		setOwnProperty("prototype",asyncGeneratorPrototype,PropertyDescriptor.DESC_PROP_READONLY_CONFIGURABLE);
		// 27.4.3.1 %AsyncGeneratorPrototype%.constructor - back-link to THIS
		// object, set from here (using the already-constructed `this`) for
		// the same reason as BuiltinGeneratorFunctionPrototype's matching
		// comment - this class isn't registered in the environment's
		// prototype cache until AFTER this constructor returns.
		asyncGeneratorPrototype.setOwnProperty("constructor",this,PropertyDescriptor.DESC_PROP_READONLY_CONFIGURABLE);
	}

	@Override
	protected Object getDefaultPrototype() {
		return BuiltinFunctionPrototype.get(getEnvironment());
	}
}
