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
package org.monflabs.galtajs.rt.builtins.primitives.array;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.jsonfactory.JSArrayImpl;
import org.monflabs.galtajs.util.SparseList;


/**
 * Builtin Array
 */
@SuppressWarnings("serial")
public class BuiltinArray extends JSArrayImpl {

	public BuiltinArray(JSEnvironment env) {
		super(env);
	}
	public BuiltinArray(JSEnvironment env, int initialCapacity) {
		super(env,initialCapacity);
	}
	public BuiltinArray(JSEnvironment env, SparseList<Object> sparseArray) {
		super(env,sparseArray);
	}
}
