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
package org.monflabs.galtajs.debug.api.impl;

import org.monflabs.galtajs.debug.api.ExecutionContext;
import org.monflabs.galtajs.rt.JSGlobalContext;

/**
 * Wraps one {@link JSGlobalContext} (interpreted or transpiled - both
 * implement it) - v1 supports exactly one per {@code DebuggerImpl} (the
 * root script's own global context), same scope restriction as the target
 * script itself.
 */
public class ExecutionContextImpl implements ExecutionContext {

	private final int id;
	private final String name;
	private final JSGlobalContext globalContext;

	public ExecutionContextImpl(int id, String name, JSGlobalContext globalContext) {
		this.id = id;
		this.name = name;
		this.globalContext = globalContext;
	}

	public JSGlobalContext getGlobalContext() {
		return globalContext;
	}

	@Override
	public int id() {
		return id;
	}

	@Override
	public String name() {
		return name;
	}

	@Override
	public Object global() {
		return globalContext.getGlobalThis();
	}
}
