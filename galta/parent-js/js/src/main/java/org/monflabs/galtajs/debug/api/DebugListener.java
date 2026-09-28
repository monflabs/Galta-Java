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

/**
 * Receives events from a {@link Debugger}.
 */
public interface DebugListener {

	/**
	 * A global context was created.
	 */
	default void executionContextCreated(final ExecutionContext context) {
	}

	/**
	 * A script was compiled and can be executed and broken in.
	 */
	default void scriptParsed(final DebugScript script) {
	}

	/**
	 * A pending breakpoint found its script.
	 */
	default void breakpointResolved(final Breakpoint breakpoint, final Location location) {
	}

	/**
	 * A thread paused. Called on that thread, which blocks after the call
	 * until the event is resumed.
	 */
	default void paused(final PausedEvent event) {
	}

	/**
	 * A paused thread resumed.
	 */
	default void resumed(final PausedEvent event) {
	}

	/**
	 * An exception escaped the outermost script frame of a thread.
	 */
	default void exceptionThrown(final ExceptionEvent event) {
	}

	/**
	 * A script called a {@code console} function.
	 */
	default void consoleCalled(final ConsoleEvent event) {
	}

	/**
	 * The debugged script's execution thread finished - ran to completion,
	 * threw uncaught, or was stopped - so nothing further will ever pause or
	 * resume. Mirrors a real Node/V8 inspector closing its connection when
	 * the debugged process exits.
	 */
	default void executionFinished() {
	}
}
