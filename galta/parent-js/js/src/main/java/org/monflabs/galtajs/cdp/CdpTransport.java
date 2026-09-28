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
package org.monflabs.galtajs.cdp;

import java.io.IOException;
import java.util.function.Consumer;

/**
 * The server side of one CDP connection, from {@link org.monflabs.galtajs.cdp.protocol.CdpSession}'s
 * own point of view: read/deliver inbound messages until the peer goes away,
 * send outbound ones, close. Two implementations exist -
 * {@link org.monflabs.galtajs.cdp.ws.WebSocketConnection} (a real socket) and
 * an in-process one (no socket at all, for when the engine and a debugger
 * client run in the same JVM) - {@code CdpSession} itself has no idea which
 * one it is talking to.
 */
public interface CdpTransport {

	/**
	 * Delivers each inbound text message to {@code onMessage} until the peer
	 * disconnects. Blocks the calling thread for the connection's whole
	 * lifetime - callers run this on a dedicated thread.
	 * @param onMessage where each inbound message goes
	 * @throws IOException if the connection breaks
	 */
	void run(Consumer<String> onMessage) throws IOException;

	/**
	 * Sends one complete text message. Safe to call from any thread.
	 * @param text the message
	 * @throws IOException if it cannot be sent
	 */
	void send(String text) throws IOException;

	/**
	 * Closes the connection. Idempotent.
	 * @param code a close status code
	 * @param reason a short reason
	 */
	void close(int code, String reason);
}
