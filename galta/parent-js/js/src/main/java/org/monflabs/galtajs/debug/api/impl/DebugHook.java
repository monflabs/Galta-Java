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

import org.monflabs.galtajs.rt.JSGlobalContext;
import org.monflabs.galtajs.rt.JSGlobalContext.RunningState;
import org.monflabs.galtajs.rt.JSRuntimeContext;
import org.monflabs.galtajs.rt.JSRuntimeException;

/**
 * The engine-facing callback an instrumented statement calls into - from
 * {@code ASTDebugHook} in interpreted mode, or directly from generated
 * Java (via {@code JSTranspiledUnit.debugStatement()}) in transpiled mode.
 * This is the low-level hook contract behind the {@code debug.api} facade
 * ({@code DebuggerImpl} implements this and translates into
 * {@code PausedEvent}/{@code DebugListener} calls).
 */
public interface DebugHook {

	boolean onStatement(JSRuntimeContext context, DebugLocation location);

	boolean onExit(JSRuntimeContext context, DebugLocation location);

	boolean onExceptionThrown(JSRuntimeContext context, JSRuntimeException t, DebugLocation location);

	void onStateChanged(JSGlobalContext context, RunningState state);

	// Called by the PAUSED thread itself, still holding this hook's own
	// monitor, immediately after a notify() wakes its wait() call - lets a
	// DIFFERENT thread (the CDP session, evaluating a watch expression or
	// dispatching Debugger.evaluateOnCallFrame) hand work to the paused
	// thread instead of racing it by touching the live paused context
	// directly from another thread (see PausedEvent.call()'s own contract).
	// Returns true to keep waiting (a pending callable was run, or the
	// wakeup was some other hook's unrelated notify()), false to actually
	// resume execution.
	boolean onWoken();
}
