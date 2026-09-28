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

import java.util.concurrent.CompletionStage;

/**
 * The client side of one CDP connection, from a UI's protocol layer (e.g.
 * {@code org.monflabs.js.debugger.ui.cdp.CdpConnection}) own point of view:
 * send outbound text, be told about inbound text and the close. Two
 * implementations exist - one over {@code java.net.http.WebSocket} (a real
 * socket), one in-process (no socket at all) - the protocol layer above has
 * no idea which one it is talking to.
 *
 * <p>Deliberately living in {@code org.monflabs.galtajs.cdp} rather than in
 * the debugger UI module: it is the one shared type an in-process transport
 * (which must live in this module, alongside {@link CdpTransport} and
 * {@code CdpSession}) can hand back to a UI client without this module ever
 * depending on the UI module - the dependency runs the other way.
 */
public interface CdpClientChannel {

	/**
	 * Registers where inbound frames and the close arrive. Called exactly
	 * once, before any {@link #sendText(String)}.
	 * @param listener the listener
	 */
	void listen(ChannelListener listener);

	/**
	 * Sends one complete text message.
	 * @param text the message
	 * @return a stage completing once the send succeeds or fails
	 */
	CompletionStage<?> sendText(String text);

	/**
	 * Requests that the channel close. Idempotent; safe even if the peer is
	 * already gone.
	 */
	void requestClose();

	/**
	 * Where inbound frames and the close land. Callbacks may arrive on any
	 * thread - the caller of {@link #listen(ChannelListener)} is responsible
	 * for any marshaling it needs.
	 */
	interface ChannelListener {
		/**
		 * One inbound frame (possibly a fragment - {@code last} says whether
		 * more is coming for the same logical message).
		 * @param data the frame's text
		 * @param last whether this completes the message
		 */
		void onText(CharSequence data, boolean last);

		/**
		 * The channel closed - cleanly, on error, or because it never opened.
		 * Fires exactly once.
		 * @param reason a short description
		 */
		void onClosed(String reason);
	}
}
