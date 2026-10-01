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

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.monflabs.galtajs.cdp.CdpClientChannel;

/**
 * A {@link CdpClientChannel} over the JDK's {@link WebSocket} - the real,
 * over-the-wire transport {@link CdpConnection#connect(String, CdpConnection.Listener)}
 * uses. A pure passthrough: no send-serialization of its own, since {@code
 * CdpConnection} already chains its own sends before ever calling
 * {@link #sendText(String)}.
 */
final class JdkWebSocketChannel implements CdpClientChannel {

	// One client for every dial: an HttpClient owns a selector thread and
	// its resources, released only when it is closed or collected
	private static final HttpClient CLIENT = HttpClient.newHttpClient();

	private volatile WebSocket socket;
	private volatile ChannelListener listener;

	private JdkWebSocketChannel() {
	}

	/**
	 * Dials a WebSocket and wraps it once the handshake completes.
	 * @param wsUrl the {@code ws://host:port/...} url
	 * @return a future for the connected channel; fails the way the raw
	 *         {@code HttpClient}/{@code WebSocket} dial fails (including a
	 *         {@link java.net.http.WebSocketHandshakeException} for a
	 *         non-101 response, e.g. the server's own 403 "busy" refusal)
	 */
	static CompletableFuture<JdkWebSocketChannel> dial(final String wsUrl) {
		final JdkWebSocketChannel channel = new JdkWebSocketChannel();
		final WebSocket.Listener wsListener = new WebSocket.Listener() {
			@Override
			public CompletionStage<?> onText(final WebSocket ws, final CharSequence data, final boolean last) {
				final ChannelListener l = channel.listener;
				if (l != null) {
					l.onText(data, last);
				}
				ws.request(1);
				return null;
			}

			@Override
			public CompletionStage<?> onClose(final WebSocket ws, final int statusCode, final String reason) {
				final ChannelListener l = channel.listener;
				if (l != null) {
					l.onClosed(reason == null || reason.isEmpty() ? "connection closed" : reason);
				}
				return null;
			}

			@Override
			public void onError(final WebSocket ws, final Throwable error) {
				final ChannelListener l = channel.listener;
				if (l != null) {
					l.onClosed(String.valueOf(error.getMessage()));
				}
			}
		};
		return CLIENT
				.newWebSocketBuilder()
				.buildAsync(URI.create(wsUrl), wsListener)
				.thenApply(ws -> {
					channel.socket = ws;
					return channel;
				});
	}

	@Override
	public void listen(final ChannelListener listener) {
		this.listener = listener;
	}

	@Override
	public CompletionStage<?> sendText(final String text) {
		return socket.sendText(text, true);
	}

	@Override
	public void requestClose() {
		try {
			socket.sendClose(WebSocket.NORMAL_CLOSURE, "done");
		} catch (final RuntimeException alreadyGone) {
			// the server may already be gone
		}
	}
}
