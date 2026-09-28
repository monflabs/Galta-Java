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
package org.monflabs.galtajs.debug.api;

import java.io.IOException;

/**
 * A debugger UI/protocol implementation that can attach to a {@link Debugger}
 * (e.g. a CDP server, implemented in the separate {@code js-debugger-cdp} module).
 */
public interface DebuggerFrontend {

	/**
	 * The frontend's name, for messages.
	 */
	String name();

	/**
	 * Starts the frontend. With {@link DebugOptions#waitForDebugger()} set,
	 * returns only once a client has attached and asked execution to proceed.
	 *
	 * @param debugger the debugger to expose
	 * @param options where to listen
	 * @return a handle that stops the frontend
	 */
	AutoCloseable start(Debugger debugger, DebugOptions options) throws IOException;
}
