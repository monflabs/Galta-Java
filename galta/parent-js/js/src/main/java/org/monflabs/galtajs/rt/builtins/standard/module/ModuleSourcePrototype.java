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

/**
 * Prototype of {@link ModuleSource} objects. Its own [[Prototype]] is
 * %AbstractModuleSource%.prototype, as the proposal requires of every
 * Module Source Object's prototype ("an object whose initial [[Prototype]]
 * is %AbstractModuleSource%.prototype"). Adds the GaltaJS-specific `source`
 * getter (the module's text) on top of the inherited @@toStringTag.
 */
public class ModuleSourcePrototype extends BasePrototype {

	public static ModuleSourcePrototype get(JSEnvironment env) {
		ModuleSourcePrototype proto = (ModuleSourcePrototype)env.getRegisteredPrototype(ModuleSourcePrototype.class);
		if(proto==null) {
			proto = new ModuleSourcePrototype(env);
			env.registerPrototype(ModuleSourcePrototype.class,proto);
		}
		return proto;
	}

	private ModuleSourcePrototype(JSEnvironment env) {
		super(env);
		setOwnProperty("source", true, false, (t,k) -> asModuleSource(t).getSourceText(), null);
	}

	private static ModuleSource asModuleSource(Object o) {
		if(!(o instanceof ModuleSource ms)) {
			throw RuntimeUtil.typeError("ModuleSource.prototype.source called on incompatible receiver {0}", o!=null?o.getClass():"null");
		}
		return ms;
	}

	@Override
	protected Object getDefaultPrototype() {
		return AbstractModuleSourcePrototype.get(getEnvironment());
	}
}
