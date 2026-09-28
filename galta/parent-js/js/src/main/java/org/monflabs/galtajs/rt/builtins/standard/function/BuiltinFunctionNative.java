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
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.builtins.Constructor;


/**
 * Runtime script function.
 */
public abstract class BuiltinFunctionNative extends BuiltinFunction {
	
	public BuiltinFunctionNative(JSEnvironment env, String name, int length) {
		super(env, name,0,length);
	}

	@Override
	public Object call(Object _this, Object[] parameters, Constructor newTarget) {
		throw RuntimeUtil.typeError("Builtin Function is not a callable");
	}
	
	@Override
	public boolean isConstructor() {
		return false;
	}

	@Override
	public Object constructObject(Object[] parameters, Constructor topConstructor) {
		throw RuntimeUtil.typeError("Builtin Function is not a constructor");
	}

	@Override
	public Object constructArray(int dimensions, long size) {
		throw RuntimeUtil.typeError("Builtin Function is not an array constructor");
	}
}
