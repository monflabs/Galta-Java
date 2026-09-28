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
 * Where a {@link DebuggerFrontend} listens.
 */
public record DebugOptions(String host, int port, boolean waitForDebugger) {

	/** The default host. */
	public static final String DEFAULT_HOST = "127.0.0.1";
	/** The default port - Node's own {@code --inspect} default. */
	public static final int DEFAULT_PORT = 9229;

	/**
	 * Parses {@code [host:]port}, {@code host} or the empty string, as
	 * Node's {@code --inspect} option accepts them.
	 *
	 * @param spec the specification, possibly null or empty
	 * @param waitForDebugger whether execution waits for a client
	 */
	public static DebugOptions parse(final String spec, final boolean waitForDebugger) {
		String host = DEFAULT_HOST;
		int port = DEFAULT_PORT;
		if (spec != null && !spec.isEmpty() && !"true".equals(spec)) {
			final int colon = spec.lastIndexOf(':');
			final String portText;
			if (colon >= 0) {
				if (colon > 0) {
					host = spec.substring(0, colon);
				}
				portText = spec.substring(colon + 1);
			} else if (spec.chars().allMatch(Character::isDigit)) {
				portText = spec;
			} else {
				host = spec;
				portText = "";
			}
			if (!portText.isEmpty()) {
				try {
					port = Integer.parseInt(portText);
				} catch (final NumberFormatException e) {
					throw new IllegalArgumentException("not a port number: " + portText, e);
				}
			}
		}
		return new DebugOptions(host, port, waitForDebugger);
	}
}
