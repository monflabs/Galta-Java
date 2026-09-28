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

import java.util.NoSuchElementException;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.jsonfactory.JSObject;
import org.monflabs.galtajs.rt.JSRuntimeException;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.YieldStarDelegateResult;
import org.monflabs.galtajs.rt.builtins.BaseMethod;
import org.monflabs.galtajs.rt.builtins.BasePrototype;
import org.monflabs.galtajs.rt.builtins.PropertyDescriptor;
import org.monflabs.galtajs.rt.builtins.primitives.symbol.Symbol;
import org.monflabs.galtajs.rt.builtins.standard.iterator.BuiltinIteratorHelperPrototype;
import org.monflabs.util.generators.Generator;
import org.monflabs.util.generators.GeneratorExecutingException;

/**
 * Generator prototype.
 */
public class BuiltinGeneratorPrototype extends BasePrototype {

	public static BuiltinGeneratorPrototype get(JSEnvironment env) {
		BuiltinGeneratorPrototype proto = (BuiltinGeneratorPrototype)env.getRegisteredPrototype(BuiltinGeneratorPrototype.class);
		if(proto==null) {
			proto = new BuiltinGeneratorPrototype(env);
			env.registerPrototype(BuiltinGeneratorPrototype.class,proto);
		}
		return proto;
	}

	private BuiltinGeneratorPrototype(JSEnvironment env) {
		super(env);
		// %GeneratorPrototype%.next must be its OWN property (per
		// property-descriptor.js) with length 1 (per length.js) - the
		// inherited BuiltinIteratorHelperPrototype "next" is already
		// generator-aware behaviorally (it special-cases a wrapped
		// Generator), but is a SINGLE shared length-0 method object reused
		// by many unrelated iterator prototypes (Array/String/Map/Set/
		// RegExp/TypedArray), so it can't also be Generator's own,
		// differently-shaped ("next(value)") property. This own "next"
		// duplicates just the generator-resume behavior (see isDefaultNextMethod
		// below for why the duplication is necessary, not just an oversight).
		setOwnMethod(new Method(env,MethodId._next,1));
		setOwnMethod(new Method(env,MethodId._throw,1));
		setOwnMethod(new Method(env,MethodId._return,1));
		setOwnProperty(Symbol.TO_STRING_TAG,"Generator",PropertyDescriptor.DESC_PROP_TOSTRINGTAG);
	}

	// Mirrors BuiltinIteratorHelperPrototype.isDefaultNextMethod()'s own
	// purpose: RuntimeUtil.valueIteratorUnchecked's Java-fast-path (bypassing
	// the JS-level "next" property entirely for an unmodified generator used
	// in for-of/spread/etc.) must still recognize THIS "next" as the genuine,
	// unmodified default - otherwise EVERY ordinary generator iteration would
	// wrongly look "monkey-patched" the moment GeneratorPrototype got its own
	// "next" instead of inheriting the shared one.
	public static boolean isDefaultNextMethod(Object m) {
		return m instanceof Method me && me.methodId==MethodId._next;
	}

	@Override
	protected Object getDefaultPrototype() {
		return BuiltinIteratorHelperPrototype.get(getEnvironment());
	}

	@Override
	public String getClassName() {
		return BuiltinGeneratorConstructor.CLASSNAME;
	}

	private static enum MethodId {
		_next("next"),
		_throw("throw"),
		_return("return"),
		;
		final String id;
		MethodId(String id) {
			this.id = id;
		}
	}

	private static Generator<Object,Object> asGenerator(Object obj) {
		if(!(obj instanceof BuiltinGenerator bg) || bg.isAsync()) {
			throw RuntimeUtil.typeError("Method called on incompatible receiver {0}", obj!=null?obj.getClass():"null");
		}
		return bg.getGenerator();
	}

	private final static class Method extends BaseMethod {
		private MethodId methodId;

		private Method(JSEnvironment env, MethodId methodId, int length) {
			super(env,methodId.id,length);
			this.methodId = methodId;
		}

		@Override
		public Object call(final Object obj, final Object[] args) {
			Generator<Object,Object> gen = asGenerator(obj);
			Object arg = args.length>0 ? args[0] : RuntimeUtil.UNDEFINED;
			switch(methodId) {
				case _next -> {
					try {
						Object yielded = gen.next(arg);
						if(yielded instanceof YieldStarDelegateResult ysr) {
							return ysr.rawResult();
						}
						return JSObject.of(getEnvironment(),"value",yielded,"done",false);
					} catch(NoSuchElementException e) {
						return JSObject.of(getEnvironment(),"value",((BuiltinGenerator)obj).getDoneValue(),"done",true);
					} catch(GeneratorExecutingException e) {
						// Spec: GeneratorValidate - "If state is executing, throw a
						// TypeError exception" (the generator's own body reentrantly
						// calling back into this same generator's next/throw/return).
						throw RuntimeUtil.typeError("Generator is already running");
					}
				}
				case _throw -> {
					try {
						Object yielded = gen.throwInto(JSRuntimeException.asJavascriptException(null,arg));
						if(yielded instanceof YieldStarDelegateResult ysr) {
							return ysr.rawResult();
						}
						return JSObject.of(getEnvironment(),"value",yielded,"done",false);
					} catch(NoSuchElementException e) {
						return JSObject.of(getEnvironment(),"value",((BuiltinGenerator)obj).getDoneValue(),"done",true);
					} catch(GeneratorExecutingException e) {
						throw RuntimeUtil.typeError("Generator is already running");
					}
				}
				case _return -> {
					try {
						Object yielded = gen.returnWith(arg);
						if(yielded instanceof YieldStarDelegateResult ysr) {
							return ysr.rawResult();
						}
						return JSObject.of(getEnvironment(),"value",yielded,"done",false);
					} catch(NoSuchElementException e) {
						((BuiltinGenerator)obj).resetConsumedReturnedValue();
						return JSObject.of(getEnvironment(),"value",((BuiltinGenerator)obj).getDoneValue(),"done",true);
					} catch(GeneratorExecutingException e) {
						throw RuntimeUtil.typeError("Generator is already running");
					}
				}
				default -> {
					throw new IllegalStateException(); // Should never be here
				}
			}
		}
	}
}