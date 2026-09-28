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
 * %GeneratorFunction.prototype% - a generator FUNCTION's own [[Prototype]]
 * (distinct from that function's own "prototype" PROPERTY, which generator
 * INSTANCES chain through - see BuiltinGeneratorPrototype for that one).
 * Also gets a genuine "constructor" back-link to %GeneratorFunction%
 * (BuiltinGeneratorFunctionConstructor) - a first attempt used a plain
 * non-callable placeholder instead, which satisfied tests reaching this
 * prototype via `someGeneratorFn.constructor.prototype` (e.g.
 * symbol-tag-non-str-proxy-function.js) but broke `Object.seal(new
 * (Object.getPrototypeOf(function*(){}).constructor)())`-style tests
 * (seal-generatorfunction.js/etc.), which need `.constructor` to be
 * genuinely constructible. BuiltinGeneratorFunctionConstructor is
 * genuinely constructible (satisfying both), while still not attempting
 * real dynamic source-text compilation - see its own javadoc.
 */
public class BuiltinGeneratorFunctionPrototype extends BasePrototype {

	public static BuiltinGeneratorFunctionPrototype get(JSEnvironment env) {
		BuiltinGeneratorFunctionPrototype proto = (BuiltinGeneratorFunctionPrototype)env.getRegisteredPrototype(BuiltinGeneratorFunctionPrototype.class);
		if(proto==null) {
			proto = new BuiltinGeneratorFunctionPrototype(env);
			// Registered BEFORE constructing the Constructor (which itself
			// calls back into this same get(env)) to avoid infinite
			// recursion / a duplicate prototype instance.
			env.registerPrototype(BuiltinGeneratorFunctionPrototype.class,proto);
			new BuiltinGeneratorFunctionConstructor(env);
		}
		return proto;
	}

	private BuiltinGeneratorFunctionPrototype(JSEnvironment env) {
		super(env);
		setOwnProperty(Symbol.TO_STRING_TAG,"GeneratorFunction",PropertyDescriptor.DESC_PROP_TOSTRINGTAG);
		// 27.3.3.3 GeneratorFunction.prototype.prototype - a data property
		// pointing at the shared %GeneratorPrototype% (every generator
		// instance's own "prototype" property chains through THIS object,
		// not this one directly - see BuiltinGeneratorPrototype's own doc).
		BuiltinGeneratorPrototype generatorPrototype = BuiltinGeneratorPrototype.get(env);
		setOwnProperty("prototype",generatorPrototype,PropertyDescriptor.DESC_PROP_READONLY_CONFIGURABLE);
		// 25.3.1 %GeneratorPrototype%.constructor - back-link to THIS object
		// (%GeneratorFunction.prototype%, not %GeneratorFunction% itself).
		// Set from here (using the already-constructed `this`), not from
		// BuiltinGeneratorPrototype's own constructor calling back into
		// get(env) - this class isn't registered in the environment's
		// prototype cache until AFTER this constructor returns, so a
		// call-back would recurse into building a second, duplicate instance.
		generatorPrototype.setOwnProperty("constructor",this,PropertyDescriptor.DESC_PROP_READONLY_CONFIGURABLE);
	}

	@Override
	protected Object getDefaultPrototype() {
		return BuiltinFunctionPrototype.get(getEnvironment());
	}
}
