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
package org.monflabs.galtajs.cdp.inprocess;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;

import org.monflabs.galtajs.cdp.CdpClientChannel;

/**
 * The client ({@code CdpConnection}) side of an in-process CDP pipe -
 * see {@link ServerEnd}. Sending hands the message straight to the server's
 * inbox queue; there is nothing to queue in the other direction, since the
 * server delivers directly (see {@link ServerEnd#send}).
 */
final class ClientEnd implements CdpClientChannel {

	private final AtomicBoolean closed;
	private volatile ChannelListener listener;
	private volatile ServerEnd peer;

	ClientEnd(final AtomicBoolean closed) {
		this.closed = closed;
	}

	void setPeer(final ServerEnd peer) {
		this.peer = peer;
	}

	ChannelListener listener() {
		return listener;
	}

	@Override
	public void listen(final ChannelListener listener) {
		this.listener = listener;
	}

	@Override
	public CompletionStage<?> sendText(final String text) {
		if (closed.get()) {
			return CompletableFuture.failedFuture(new IOException("connection closed"));
		}
		peer.deliver(text);
		return CompletableFuture.completedFuture(null);
	}

	@Override
	public void requestClose() {
		peer.close(1000, "connection closed");
	}
}
