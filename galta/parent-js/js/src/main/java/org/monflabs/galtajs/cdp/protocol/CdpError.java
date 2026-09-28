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
package org.monflabs.galtajs.cdp.protocol;

/**
 * A protocol error: the code and message the response carries.
 */
final class CdpError extends Exception {
	private static final long serialVersionUID = 1L;

	static final int PARSE_ERROR = -32700;
	static final int INVALID_REQUEST = -32600;
	static final int METHOD_NOT_FOUND = -32601;
	static final int INVALID_PARAMS = -32602;
	static final int SERVER_ERROR = -32000;

	private final int code;

	CdpError(final int code, final String message) {
		super(message);
		this.code = code;
	}

	int code() {
		return code;
	}

	static CdpError notPaused() {
		return new CdpError(SERVER_ERROR, "Can only perform operation while paused.");
	}

	static CdpError invalidParams(final String what) {
		return new CdpError(INVALID_PARAMS, "Invalid parameters: " + what);
	}
}
