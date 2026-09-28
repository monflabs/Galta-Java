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

import java.util.List;
import java.util.concurrent.Callable;

/**
 * The debugger attached to one GaltaJS script execution. Obtained via a
 * GaltaJS-specific attach point (see {@code debug.api.impl}) for a script
 * compiled by a {@link org.monflabs.galtajs.JSEnvironment} built with
 * {@code debug(true)}; every method is safe to call from any thread.
 *
 * Shaped after Chrome DevTools Protocol concepts so a CDP bridge can sit on
 * top of it with minimal translation.
 */
public interface Debugger {

	/**
	 * Adds a listener.
	 */
	void addListener(DebugListener listener);

	/**
	 * Removes a listener.
	 */
	void removeListener(DebugListener listener);

	/**
	 * The execution contexts - one per global context the script has created.
	 */
	List<ExecutionContext> executionContexts();

	/**
	 * The scripts compiled so far.
	 */
	List<DebugScript> scripts();

	/**
	 * The event describing the current pause, or null if nothing is paused.
	 * Lets a client that attaches after the pause already happened (e.g. one
	 * that connects well after {@link #pauseOnStart()} froze the script)
	 * discover the pause it missed instead of never learning about it.
	 */
	PausedEvent currentPause();

	/**
	 * Whether script code is executing right now on the debugged thread -
	 * started, not finished, and not paused. While it is, the script's objects
	 * must not be touched from another thread.
	 * @return true when running
	 */
	default boolean isRunning() {
		return false;
	}

	/**
	 * Sets a breakpoint. A breakpoint whose url names a script not yet
	 * compiled stays pending - its {@link Breakpoint#locations()} is empty -
	 * and resolves when that script arrives, reported via
	 * {@link DebugListener#breakpointResolved}.
	 */
	Breakpoint setBreakpoint(BreakpointRequest request);

	/**
	 * Removes a breakpoint.
	 */
	void removeBreakpoint(String breakpointId);

	/**
	 * Activates or deactivates all breakpoints without removing them.
	 */
	void setBreakpointsActive(boolean active);

	/**
	 * Makes the debugger skip every pause - breakpoints, steps, exceptions -
	 * or honour them again.
	 */
	void setSkipAllPauses(boolean skip);

	/**
	 * Sets which exceptions pause execution.
	 */
	void setPauseOnExceptions(PauseOnExceptions mode);

	/**
	 * Pauses at the next statement any script thread reaches.
	 */
	void pause();

	/**
	 * Pauses at the first statement of the script, which is how a debugger
	 * that was waited for gets to see the beginning.
	 */
	void pauseOnStart();

	/**
	 * Evaluates an expression in a context while nothing is paused.
	 */
	Object evaluate(ExecutionContext context, String expression) throws DebugException;

	/**
	 * Runs an operation on the calling thread with a context bound, so it may
	 * read the context's objects while no thread is paused.
	 */
	<T> T call(ExecutionContext context, Callable<T> operation) throws Exception;

	/**
	 * The value model, for interpreting the objects the other methods hand out.
	 */
	DebugValues values();

	/**
	 * Resumes every paused thread, removes every breakpoint and detaches
	 * every listener. Does not stop the underlying script.
	 */
	void close();
}
