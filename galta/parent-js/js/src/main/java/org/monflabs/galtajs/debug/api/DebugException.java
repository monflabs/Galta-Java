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
 * Thrown when an expression evaluated for a debugger throws or does not parse.
 */
public final class DebugException extends Exception {

	private static final long serialVersionUID = 1L;

	private final transient Object thrown;

	/**
	 * Creates the exception.
	 *
	 * @param message the message
	 * @param thrown the value the script threw, a GaltaJS value, or null for a parse error
	 * @param cause the underlying exception
	 */
	public DebugException(final String message, final Object thrown, final Throwable cause) {
		super(message, cause);
		this.thrown = thrown;
	}

	/**
	 * The value the script threw, if the expression threw.
	 */
	public Object thrown() {
		return thrown;
	}
}
