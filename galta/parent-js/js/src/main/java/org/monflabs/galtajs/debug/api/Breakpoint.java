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
 * A breakpoint the debugger is tracking. Session-scoped state - never stored
 * on an AST node - so a compiled script's AST stays cacheable regardless of
 * which breakpoints any particular debugging session sets on it.
 */
public interface Breakpoint {

	/**
	 * The id, unique within the debugger.
	 */
	String id();

	/**
	 * What was asked for.
	 */
	BreakpointRequest request();

	/**
	 * Where the breakpoint landed: empty while it is pending, otherwise one
	 * location per script it matched.
	 */
	List<Location> locations();
}
