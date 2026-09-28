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
package org.monflabs.galtajs.rt.builtins.standard.function;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.rt.builtins.BasePrototype;
import org.monflabs.galtajs.rt.builtins.PropertyDescriptor;
import org.monflabs.galtajs.rt.builtins.primitives.symbol.Symbol;

/**
 * %AsyncFunction.prototype% - an async function's own [[Prototype]] (an
 * async function has no separate own "prototype" property/instance chain
 * the way a generator function does - it just returns a Promise directly).
 * Also gets a genuine "constructor" back-link to %AsyncFunction%
 * (BuiltinAsyncFunctionConstructor) - see BuiltinGeneratorFunctionPrototype's
 * javadoc for the full rationale (same pattern, mirrored for async
 * functions).
 */
public class BuiltinAsyncFunctionPrototype extends BasePrototype {

	public static BuiltinAsyncFunctionPrototype get(JSEnvironment env) {
		BuiltinAsyncFunctionPrototype proto = (BuiltinAsyncFunctionPrototype)env.getRegisteredPrototype(BuiltinAsyncFunctionPrototype.class);
		if(proto==null) {
			proto = new BuiltinAsyncFunctionPrototype(env);
			// Registered BEFORE constructing the Constructor (which itself
			// calls back into this same get(env)) to avoid infinite
			// recursion / a duplicate prototype instance.
			env.registerPrototype(BuiltinAsyncFunctionPrototype.class,proto);
			new BuiltinAsyncFunctionConstructor(env);
		}
		return proto;
	}

	private BuiltinAsyncFunctionPrototype(JSEnvironment env) {
		super(env);
		setOwnProperty(Symbol.TO_STRING_TAG,"AsyncFunction",PropertyDescriptor.DESC_PROP_TOSTRINGTAG);
	}

	@Override
	protected Object getDefaultPrototype() {
		return BuiltinFunctionPrototype.get(getEnvironment());
	}
}
