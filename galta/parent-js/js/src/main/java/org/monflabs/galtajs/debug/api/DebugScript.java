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
 * A script compiled with debugging capabilities.
 */
public interface DebugScript {

	/**
	 * The id, unique within the debugger.
	 */
	String id();

	/**
	 * The url. Matches the script's descriptor name, so every script can be
	 * named in a breakpoint.
	 */
	String url();

	/**
	 * The name the source was given - a file name, {@code <eval>}, and so on.
	 */
	String name();

	/**
	 * The source text.
	 */
	String source();

	/**
	 * A digest of the source, stable across runs.
	 */
	String hash();

	/**
	 * The last line, 1-based.
	 */
	int endLine();

	/**
	 * The column after the last character of the last line, 1-based.
	 */
	int endColumn();

	/**
	 * The length of the source in characters.
	 */
	int length();

	/**
	 * Whether this is code handed to {@code eval}.
	 */
	boolean isEval();

	/**
	 * Whether this is a module rather than a script.
	 */
	boolean isModule();

	/**
	 * The context the script was compiled for.
	 */
	ExecutionContext context();

	/**
	 * The locations a breakpoint can be set at within a range, in order.
	 *
	 * @param startLine first line, 1-based
	 * @param startColumn first column on that line, 1-based
	 * @param endLine last line, inclusive; negative for the end of the script
	 * @param endColumn last column on that line, exclusive
	 */
	List<Location> possibleBreakpoints(int startLine, int startColumn, int endLine, int endColumn);
}
