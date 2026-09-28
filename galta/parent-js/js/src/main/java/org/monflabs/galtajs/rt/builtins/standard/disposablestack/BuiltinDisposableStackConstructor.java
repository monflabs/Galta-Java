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
package org.monflabs.galtajs.rt.builtins.standard.disposablestack;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.builtins.Constructor;
import org.monflabs.galtajs.rt.builtins.primitives.BaseStandardConstructor;

/**
 * DisposableStack ( ) - explicit resource management proposal.
 */
public class BuiltinDisposableStackConstructor extends BaseStandardConstructor {

	public static final String CLASSNAME = "DisposableStack";

	public BuiltinDisposableStackConstructor(JSEnvironment env) {
		super(env,CLASSNAME,BuiltinDisposableStackPrototype.get(env),0);
	}

	@Override
	public Class<?> getNativeClass() {
		return BuiltinDisposableStack.class;
	}

	@Override
	public Object constructObject(Object[] parameters, Constructor topConstructor) {
		return applyNewTargetPrototype(new BuiltinDisposableStack(getEnvironment()), topConstructor);
	}

	@Override
	public Object call(Object _this, Object[] parameters) {
		throw RuntimeUtil.typeError("Constructor DisposableStack requires 'new'");
	}
}
