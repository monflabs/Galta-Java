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
package org.monflabs.galtajs.rt.builtins;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.rt.builtins.primitives.symbol.Symbol;

public abstract class BaseMethod extends BaseNativeMethod {
	
	private Object id;

	protected BaseMethod(JSEnvironment env, Object id, int length) {
		super(env);
		this.id = id;
		String name;
		if(id instanceof Symbol sy) {
			name = "["+sy.getDescription()+"]";
		} else {
			name = id.toString();;
		}
		setOwnProperty("name",name,PropertyDescriptor.DESC_PROP_READONLY_CONFIGURABLE);
		setOwnProperty("length",length,PropertyDescriptor.DESC_PROP_READONLY_CONFIGURABLE);
		// Ordinary built-in methods (not constructors) must not have an own
		// "prototype" property at all - the inherited %Function.prototype%
		// has none either, so this is unobservable except via
		// hasOwnProperty()/getOwnPropertyDescriptor().
	}

	@Override
	public final Object getId() {
		return id;
	}

	@Override
	public abstract Object call(final Object obj, final Object[] args);
	
	
}