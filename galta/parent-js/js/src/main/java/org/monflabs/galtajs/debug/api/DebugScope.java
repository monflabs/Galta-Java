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
 * One link of a frame's scope chain.
 *
 * @param type what kind of scope
 * @param object the object holding the scope's bindings, a GaltaJS value (see {@link DebugValues})
 * @param name the scope's name where one applies - the function's for a local or closure scope - or null
 */
public record DebugScope(ScopeType type, Object object, String name) {

	/**
	 * The kinds of scope.
	 */
	public enum ScopeType {
		/** The global object. */
		GLOBAL,
		/** The paused function's own variables. */
		LOCAL,
		/** An enclosing function's variables. */
		CLOSURE,
		/** A block's lexical declarations. */
		BLOCK,
		/** A catch clause's parameter. */
		CATCH,
		/** A {@code with} statement's object. */
		WITH,
		/** The global lexical declarations of a script. */
		SCRIPT
	}
}
