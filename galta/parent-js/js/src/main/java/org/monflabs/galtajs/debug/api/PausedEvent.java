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
 * A thread paused in script. The thread stays paused until one of the
 * resuming methods is called - from any thread - and meanwhile runs whatever
 * {@link #call} is handed, so that script objects are only ever touched by
 * the thread that owns them.
 */
public interface PausedEvent {

	/**
	 * The paused thread.
	 */
	Thread thread();

	/**
	 * Why it paused.
	 */
	PauseReason reason();

	/**
	 * The call frames, innermost first.
	 */
	List<DebugFrame> frames();

	/**
	 * The breakpoints that caused the pause, if any.
	 */
	List<String> hitBreakpoints();

	/**
	 * The exception being thrown, for a pause on an exception; otherwise null.
	 */
	Object exception();

	/**
	 * The context the thread is executing in.
	 */
	ExecutionContext context();

	/**
	 * Whether the thread has been resumed.
	 */
	boolean isResumed();

	/**
	 * Resumes execution.
	 */
	void resume();

	/**
	 * Resumes until the next statement, entering calls.
	 */
	void stepInto();

	/**
	 * Resumes until the next statement in this frame or a caller's.
	 */
	void stepOver();

	/**
	 * Resumes until the next statement in a caller's frame.
	 */
	void stepOut();

	/**
	 * Runs an operation on the paused thread and returns its result. Called
	 * on the paused thread itself, it runs inline.
	 */
	<T> T call(Callable<T> operation) throws Exception;
}
