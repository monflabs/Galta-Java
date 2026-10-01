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
 *
 * Portions are derived from the TC39 Temporal proposal reference polyfill,
 * Copyright (c) 2017, 2018, 2019, 2020 Ecma International. All rights
 * reserved. Distributed under the BSD License, see LICENSE.txt in this
 * folder.
 */
package org.monflabs.galtajs.rt.builtins.standard.temporal;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.builtins.BaseMethod;

/**
 * A built-in Temporal function whose body is a lambda.
 */
public final class TemporalFn extends BaseMethod {

	@FunctionalInterface
	public interface Body {
		public Object call(Object thisObj, Object[] args);
	}

	private final Body body;

	public TemporalFn(JSEnvironment env, String name, int length, Body body) {
		super(env,name,length);
		this.body = body;
	}

	@Override
	protected Object invoke(Object obj, Object[] args) {
		return body.call(obj,args);
	}

	public static Object arg(Object[] args, int i) {
		return args!=null && i<args.length ? args[i] : RuntimeUtil.UNDEFINED;
	}

	public static <T> T receiver(Object o, Class<T> c, String typeName) {
		if(!c.isInstance(o)) {
			throw RuntimeUtil.typeError("{0}","invalid receiver: not a Temporal."+typeName);
		}
		return c.cast(o);
	}
}
