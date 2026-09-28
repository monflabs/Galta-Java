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
package org.monflabs.galtajs.cdp.ws;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

import org.monflabs.galtajs.cdp.CdpTransport;

/**
 * One WebSocket connection, RFC 6455 server side: masked frames in, unmasked
 * frames out; text, fragments, ping, pong and close. Reading happens on the
 * thread that calls {@link #run}; writing is synchronized, so any thread may
 * {@link #send}.
 *
 * <p>Ported near-verbatim from a sibling project's own Chrome DevTools
 * Protocol debugger (same author) - pure JDK sockets, zero engine coupling,
 * so no adaptation was needed beyond the package rename.
 *
 * <p>Implements {@link CdpTransport} - {@code run}/{@code send}/{@code
 * close(int,String)} already matched that interface's shape exactly before
 * it existed; this is the real-socket implementation {@code CdpSession} uses
 * today, alongside a socket-free in-process one (see {@code cdp.inprocess}).
 */
public final class WebSocketConnection implements AutoCloseable, CdpTransport {
	private static final int OP_CONTINUATION = 0;
	private static final int OP_TEXT = 1;
	private static final int OP_BINARY = 2;
	private static final int OP_CLOSE = 8;
	private static final int OP_PING = 9;
	private static final int OP_PONG = 10;
	/** Larger than any script source a debugger is likely to be handed, smaller than a mistake. */
	private static final int MAX_MESSAGE = 64 * 1024 * 1024;

	private final Socket socket;
	private final InputStream in;
	private final OutputStream out;
	private volatile boolean closed;

	WebSocketConnection(final Socket socket, final InputStream in, final OutputStream out) {
		this.socket = socket;
		this.in = in;
		this.out = out;
	}

	/**
	 * Reads frames until the connection closes, handing each text message to the consumer.
	 * @param onMessage what to do with a message
	 * @throws IOException on a broken connection
	 */
	@Override
	public void run(final Consumer<String> onMessage) throws IOException {
		final ByteArrayOutputStream message = new ByteArrayOutputStream();
		int messageOpcode = -1;
		try {
			while (!closed) {
				final int b0 = in.read();
				if (b0 < 0) {
					return;
				}
				final boolean fin = (b0 & 0x80) != 0;
				final int opcode = b0 & 0x0f;
				final int b1 = readByte();
				final boolean masked = (b1 & 0x80) != 0;
				if (!masked) {
					// RFC 6455 section 5.1: a server must close on an unmasked client frame
					close(1002, "client frames must be masked");
					return;
				}
				long length = b1 & 0x7f;
				if (length == 126) {
					length = ((long) readByte() << 8) | readByte();
				} else if (length == 127) {
					length = 0;
					for (int i = 0; i < 8; i++) {
						length = (length << 8) | readByte();
					}
				}
				if (length < 0 || length > MAX_MESSAGE) {
					close(1009, "message too big");
					return;
				}
				final byte[] mask = masked ? readFully(4) : null;
				final byte[] payload = readFully((int) length);
				if (mask != null) {
					for (int i = 0; i < payload.length; i++) {
						payload[i] ^= mask[i & 3];
					}
				}
				switch (opcode) {
				case OP_TEXT, OP_BINARY -> {
					message.reset();
					message.write(payload);
					messageOpcode = opcode;
					if (fin) {
						deliver(onMessage, message, messageOpcode);
						messageOpcode = -1;
					}
				}
				case OP_CONTINUATION -> {
					if (messageOpcode < 0) {
						close(1002, "continuation without a start");
						return;
					}
					if (message.size() + payload.length > MAX_MESSAGE) {
						close(1009, "message too big");
						return;
					}
					message.write(payload);
					if (fin) {
						deliver(onMessage, message, messageOpcode);
						messageOpcode = -1;
					}
				}
				case OP_PING -> frame(OP_PONG, payload);
				case OP_PONG -> { /* nothing to do */ }
				case OP_CLOSE -> {
					if (!closed) {
						frame(OP_CLOSE, payload.length >= 2 ? new byte[] { payload[0], payload[1] } : new byte[0]);
						closed = true;
					}
					return;
				}
				default -> {
					close(1002, "unknown opcode " + opcode);
					return;
				}
				}
			}
		} finally {
			closeSocket();
		}
	}

	private static void deliver(final Consumer<String> onMessage, final ByteArrayOutputStream message, final int opcode) {
		if (opcode == OP_TEXT) {
			onMessage.accept(message.toString(StandardCharsets.UTF_8));
		}
	}

	private int readByte() throws IOException {
		final int b = in.read();
		if (b < 0) {
			throw new EOFException();
		}
		return b;
	}

	private byte[] readFully(final int n) throws IOException {
		final byte[] bytes = new byte[n];
		int read = 0;
		while (read < n) {
			final int r = in.read(bytes, read, n - read);
			if (r < 0) {
				throw new EOFException();
			}
			read += r;
		}
		return bytes;
	}

	/**
	 * Sends a text message. Safe from any thread.
	 * @param text the message
	 * @throws IOException on a broken connection
	 */
	@Override
	public void send(final String text) throws IOException {
		frame(OP_TEXT, text.getBytes(StandardCharsets.UTF_8));
	}

	private synchronized void frame(final int opcode, final byte[] payload) throws IOException {
		if (closed && opcode != OP_CLOSE) {
			throw new IOException("connection closed");
		}
		out.write(0x80 | opcode);
		final int length = payload.length;
		if (length < 126) {
			out.write(length);
		} else if (length < 65536) {
			out.write(126);
			out.write(length >>> 8);
			out.write(length);
		} else {
			out.write(127);
			for (int i = 7; i >= 0; i--) {
				out.write((int) (((long) length) >>> (8 * i)));
			}
		}
		out.write(payload);
		out.flush();
	}

	/**
	 * Closes with a status code.
	 * @param code the status code
	 * @param reason the reason
	 */
	@Override
	public void close(final int code, final String reason) {
		if (closed) {
			return;
		}
		closed = true;
		try {
			final byte[] text = reason.getBytes(StandardCharsets.UTF_8);
			final byte[] payload = new byte[2 + text.length];
			payload[0] = (byte) (code >>> 8);
			payload[1] = (byte) code;
			System.arraycopy(text, 0, payload, 2, text.length);
			frame(OP_CLOSE, payload);
		} catch (final IOException ignored) {
			// the other side is gone
		}
		closeSocket();
	}

	@Override
	public void close() {
		close(1000, "");
	}

	/**
	 * Whether the connection has been closed from either side.
	 * @return true when closed
	 */
	public boolean isClosed() {
		return closed;
	}

	private void closeSocket() {
		try {
			socket.close();
		} catch (final IOException ignored) {
			// nothing more to do with it
		}
	}
}
