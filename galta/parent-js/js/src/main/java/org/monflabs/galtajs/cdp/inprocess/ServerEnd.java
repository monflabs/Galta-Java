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
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import org.monflabs.galtajs.cdp.CdpClientChannel;
import org.monflabs.galtajs.cdp.CdpTransport;

/**
 * The server ({@code CdpSession}) side of an in-process CDP pipe. Inbound
 * (client-to-server) messages go through a queue, since {@link #run} is a
 * blocking pull loop exactly like {@code WebSocketConnection.run}; outbound
 * ones are delivered by calling the peer's listener directly, synchronously,
 * on the sending thread - safe because {@code CdpConnection}/{@code
 * DebugSession} already document that their callbacks may arrive on any
 * thread and are trampolined onto their own executor.
 */
final class ServerEnd implements CdpTransport {

	private static final Object CLOSE = new Object();

	private final BlockingQueue<Object> inbox = new LinkedBlockingQueue<>();
	private final AtomicBoolean closed;
	private volatile ClientEnd peer;
	// Responses (reader thread) and events (script thread) are sent from
	// different threads: deliver them one at a time, so the client never
	// sees two messages interleaved
	private final Object sendLock = new Object();

	ServerEnd(final AtomicBoolean closed) {
		this.closed = closed;
	}

	void setPeer(final ClientEnd peer) {
		this.peer = peer;
	}

	@Override
	public void run(final Consumer<String> onMessage) throws IOException {
		while (true) {
			final Object item;
			try {
				item = inbox.take();
			} catch (final InterruptedException e) {
				Thread.currentThread().interrupt();
				return;
			}
			if (item == CLOSE) {
				return;
			}
			onMessage.accept((String) item);
		}
	}

	@Override
	public void send(final String text) throws IOException {
		if (closed.get()) {
			throw new IOException("connection closed");
		}
		synchronized (sendLock) {
			final CdpClientChannel.ChannelListener listener = peer.listener();
			if (listener != null) {
				listener.onText(text, true);
			}
		}
	}

	@Override
	public void close(final int code, final String reason) {
		if (!closed.compareAndSet(false, true)) {
			return;
		}
		inbox.offer(CLOSE);
		synchronized (sendLock) {
			final CdpClientChannel.ChannelListener listener = peer.listener();
			if (listener != null) {
				listener.onClosed(reason);
			}
		}
	}

	void deliver(final String text) {
		inbox.offer(text);
	}
}
