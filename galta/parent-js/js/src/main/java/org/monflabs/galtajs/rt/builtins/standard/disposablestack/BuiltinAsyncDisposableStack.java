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

import java.util.ArrayList;
import java.util.List;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.rt.DisposableResource;
import org.monflabs.galtajs.rt.builtins.NativeObject;

/**
 * Internal state for an AsyncDisposableStack instance - [[AsyncDisposableState]]
 * ("pending"/"disposed") and [[DisposeCapability]].[[DisposableResourceStack]].
 * A genuinely distinct native type from BuiltinDisposableStack (not a shared
 * base) so RequireInternalSlot cross-checks (e.g. calling
 * DisposableStack.prototype.use on an AsyncDisposableStack instance) correctly
 * fail via instanceof, exactly as the spec's distinct internal slots do.
 */
public class BuiltinAsyncDisposableStack extends NativeObject {

	private final List<DisposableResource> resources = new ArrayList<>();
	private boolean disposed;

	public BuiltinAsyncDisposableStack(JSEnvironment env) {
		super(env);
	}

	@Override
	public String getClassName() {
		return BuiltinAsyncDisposableStackConstructor.CLASSNAME;
	}

	@Override
	protected Object getDefaultPrototype() {
		return BuiltinAsyncDisposableStackPrototype.get(getEnvironment());
	}

	public List<DisposableResource> getResources() {
		return resources;
	}

	public boolean isDisposed() {
		return disposed;
	}

	public void setDisposed(boolean disposed) {
		this.disposed = disposed;
	}
}
