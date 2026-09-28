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
package org.monflabs.galtajs.rt.builtins.standard.module;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.builtins.BasePrototype;
import org.monflabs.galtajs.rt.builtins.primitives.symbol.Symbol;

/**
 * %AbstractModuleSource%.prototype (source-phase-imports proposal, 28.3.3):
 * [[Prototype]] is %Object.prototype%; owns `constructor` (installed by the
 * %AbstractModuleSource% constructor built here) and the @@toStringTag getter
 * that returns the receiver's [[ModuleSourceClassName]], or undefined when
 * the receiver is not a Module Source Object (the prototype itself included).
 *
 * The intrinsic constructor is created together with this prototype (not
 * registered as a global - the proposal defines no global for it; it is
 * reached through a Module Source Object's prototype chain, or by a host
 * such as test262's `$262.AbstractModuleSource`).
 */
public class AbstractModuleSourcePrototype extends BasePrototype {

	public static AbstractModuleSourcePrototype get(JSEnvironment env) {
		AbstractModuleSourcePrototype proto = (AbstractModuleSourcePrototype)env.getRegisteredPrototype(AbstractModuleSourcePrototype.class);
		if(proto==null) {
			proto = new AbstractModuleSourcePrototype(env);
			env.registerPrototype(AbstractModuleSourcePrototype.class,proto);
		}
		return proto;
	}

	private final AbstractModuleSourceConstructor constructor;

	private AbstractModuleSourcePrototype(JSEnvironment env) {
		super(env);
		setOwnProperty(Symbol.TO_STRING_TAG, true, false,
				(t,k) -> t instanceof ModuleSource ms ? ms.getModuleSourceClassName() : RuntimeUtil.UNDEFINED,
				null);
		// Installs this prototype's own "constructor" property too (see
		// BaseConstructor's constructor) - done last, once the prototype's
		// own properties are in place.
		this.constructor = new AbstractModuleSourceConstructor(env, this);
	}

	public AbstractModuleSourceConstructor getConstructor() {
		return constructor;
	}
}
