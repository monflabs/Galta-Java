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

/**
 * A call frame of a paused thread. Valid until the thread resumes.
 */
public interface DebugFrame {

	/**
	 * The id, unique within the pause: the frame's index, innermost first.
	 */
	String id();

	/**
	 * The function's name; empty for an anonymous function, {@code <program>} for a script body.
	 */
	String functionName();

	/**
	 * Where the frame is: the statement it executes.
	 */
	Location location();

	/**
	 * Where the function begins, or null if unknown.
	 */
	Location functionLocation();

	/**
	 * The scopes visible from the frame, innermost first, ending with the global scope.
	 */
	List<DebugScope> scopes();

	/**
	 * The receiver.
	 */
	Object thisValue();

	/**
	 * Evaluates an expression as if written at the frame's location. Runs on
	 * the paused thread.
	 */
	Object evaluate(String expression) throws DebugException;
}
