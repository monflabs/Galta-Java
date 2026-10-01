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

import org.monflabs.galtajs.rt.builtins.BuiltinUtil;
import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.builtins.Callable;
import org.monflabs.galtajs.rt.builtins.Constructor;
import org.monflabs.galtajs.rt.builtins.primitives.BaseStandardConstructor;

/**
 * FinalizationRegistry ( cleanupCallback )
 */
public class BuiltinFinalizationRegistryConstructor extends BaseStandardConstructor {

	public static final String CLASSNAME = "FinalizationRegistry";

	public BuiltinFinalizationRegistryConstructor(JSEnvironment env) {
		super(env,CLASSNAME,BuiltinFinalizationRegistryPrototype.get(env),1);
	}

	@Override
	public Class<?> getNativeClass() {
		return BuiltinFinalizationRegistry.class;
	}

	@Override
	public Object constructObject(Object[] parameters, Constructor topConstructor) {
		Object cleanupCallback = param(parameters, 0, RuntimeUtil.UNDEFINED);
		if(!BuiltinUtil.isCallable(cleanupCallback)) {
			throw RuntimeUtil.typeError("FinalizationRegistry: cleanupCallback must be a function");
		}
		return applyNewTargetPrototype(new BuiltinFinalizationRegistry(getEnvironment(),cleanupCallback), topConstructor);
	}

	@Override
	public Object call(Object _this, Object[] parameters) {
		throw RuntimeUtil.typeError("Constructor FinalizationRegistry requires 'new'");
	}
}
