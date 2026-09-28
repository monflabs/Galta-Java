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
package org.monflabs.galtajs.rt.builtins.standard.finalizationregistry;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.builtins.BaseMethod;
import org.monflabs.galtajs.rt.builtins.BasePrototype;
import org.monflabs.galtajs.rt.builtins.PropertyDescriptor;
import org.monflabs.galtajs.rt.builtins.primitives.symbol.Symbol;

public class BuiltinFinalizationRegistryPrototype extends BasePrototype {

	public static BuiltinFinalizationRegistryPrototype get(JSEnvironment env) {
		BuiltinFinalizationRegistryPrototype proto = (BuiltinFinalizationRegistryPrototype)env.getRegisteredPrototype(BuiltinFinalizationRegistryPrototype.class);
		if(proto==null) {
			proto = new BuiltinFinalizationRegistryPrototype(env);
			env.registerPrototype(BuiltinFinalizationRegistryPrototype.class,proto);
		}
		return proto;
	}

	private BuiltinFinalizationRegistryPrototype(JSEnvironment env) {
		super(env);
		setOwnProperty(Symbol.TO_STRING_TAG,BuiltinFinalizationRegistryConstructor.CLASSNAME,PropertyDescriptor.DESC_PROP_TOSTRINGTAG);

		setOwnMethod(new Method(env,MethodId.register,2));
		setOwnMethod(new Method(env,MethodId.unregister,1));
	}

	@Override
	public String getClassName() {
		return BuiltinFinalizationRegistryConstructor.CLASSNAME;
	}

	private static enum MethodId {
		register,
		unregister,
	}

	private final static class Method extends BaseMethod {
		private MethodId methodId;

		private Method(JSEnvironment env, MethodId methodId, int length) {
			super(env,methodId.name(),length);
			this.methodId = methodId;
		}

		@Override
		public Object call(final Object obj, final Object[] args) {
			if(!(obj instanceof BuiltinFinalizationRegistry registry)) {
				throw RuntimeUtil.typeError("Method FinalizationRegistry.prototype.{0} called on incompatible receiver {1}", methodId.name(), obj!=null?obj.getClass():"null");
			}
			JSEnvironment env = getEnvironment();

			switch(methodId) {
				case register -> {
					Object target = param(args, 0, RuntimeUtil.UNDEFINED);
					if(!RuntimeUtil.canBeHeldWeakly(env, target)) {
						throw RuntimeUtil.typeError("FinalizationRegistry.prototype.register: target cannot be held weakly");
					}
					Object heldValue = param(args, 1, RuntimeUtil.UNDEFINED);
					if(target==heldValue) {
						throw RuntimeUtil.typeError("FinalizationRegistry.prototype.register: target and heldValue must not be the same value");
					}
					Object unregisterTokenArg = param(args, 2, RuntimeUtil.UNDEFINED);
					Object unregisterToken;
					if(unregisterTokenArg==RuntimeUtil.UNDEFINED) {
						unregisterToken = null;
					} else if(RuntimeUtil.canBeHeldWeakly(env, unregisterTokenArg)) {
						unregisterToken = unregisterTokenArg;
					} else {
						throw RuntimeUtil.typeError("FinalizationRegistry.prototype.register: unregisterToken cannot be held weakly");
					}
					registry.register(target, heldValue, unregisterToken);
					return RuntimeUtil.UNDEFINED;
				}
				case unregister -> {
					Object unregisterToken = param(args, 0, RuntimeUtil.UNDEFINED);
					if(!RuntimeUtil.canBeHeldWeakly(env, unregisterToken)) {
						throw RuntimeUtil.typeError("FinalizationRegistry.prototype.unregister: unregisterToken cannot be held weakly");
					}
					return registry.unregister(unregisterToken);
				}
				default -> {
					throw new IllegalStateException(); // Should never be here
				}
			}
		}
	}
}
