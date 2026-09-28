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

package org.monflabs.js.debugger.ui.cdp;

/**
 * A protocol failure: either a JSON-RPC {@code error} the server returned for
 * a request, or a transport-level condition mapped to a code of its own. The
 * {@link #code()} is the server's for a real protocol error, and one of the
 * negative {@code TRANSPORT_*} constants otherwise, so a client can tell "the
 * server refused this call" from "there is no connection".
 */
public final class CdpException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    /** The connection was refused because another client is already attached (HTTP 403). */
    public static final int TRANSPORT_BUSY = -403;
    /** The connection closed, or never opened, for any other reason. */
    public static final int TRANSPORT_CLOSED = -1;

    private final int code;

    /**
     * Creates a failure.
     * @param code the server's error code, or a {@code TRANSPORT_*} constant
     * @param message the message
     */
    public CdpException(final int code, final String message) {
        super(message);
        this.code = code;
    }

    /**
     * The error code.
     * @return the code
     */
    public int code() {
        return code;
    }

    /** Whether this is a transport failure rather than a protocol error. */
    public boolean isTransport() {
        return code == TRANSPORT_BUSY || code == TRANSPORT_CLOSED;
    }
}
