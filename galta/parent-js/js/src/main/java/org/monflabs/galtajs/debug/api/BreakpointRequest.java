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
 * Where and under which condition to break.
 *
 * @param url the script's name/url (matches {@link DebugScript#url()})
 * @param urlRegex a pattern matching the script's url, instead of an exact url
 * @param scriptId a specific script's id, instead of a url
 * @param line the line, 1-based, matching {@link org.monflabs.galtajs.node.ASTNode#getBeginLine()}'s convention
 * @param column the column, 1-based, or -1 for "any column on the line"
 * @param condition an expression that must be truthy for the breakpoint to fire, or null
 */
public record BreakpointRequest(String url, String urlRegex, String scriptId, int line, int column, String condition) {

	/**
	 * A breakpoint by url and line.
	 */
	public static BreakpointRequest at(final String url, final int line) {
		return new BreakpointRequest(url, null, null, line, -1, null);
	}
}
