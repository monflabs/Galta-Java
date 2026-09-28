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

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import org.monflabs.galtajs.cdp.CdpClientChannel;
import org.monflabs.galtajs.cdp.protocol.CdpSession;
import org.monflabs.galtajs.debug.api.Debugger;
import org.monflabs.galtajs.debug.api.DebugOptions;

/**
 * A same-JVM, socket-free Chrome DevTools Protocol session: the counterpart
 * to {@link org.monflabs.galtajs.cdp.CdpServer} for when the engine and a
 * debugger client (e.g. the Swing {@code DebuggerPanel}) run in the very
 * same process, or when no network stack is available at all. Reuses
 * {@link CdpSession}'s own JSON-RPC dispatch unchanged - only the transport
 * underneath it differs.
 *
 * <pre>
 *   JSEnvironment env = JSEnvironment.newBuilder().debug(true).build();
 *   JSInterpretedUnit unit = env.createScript(source, "app.js");
 *   DebuggerImpl debugger = new DebuggerImpl(unit, () -&gt; new InterpretedGlobalRuntimeContext(env, env.createProgramExecutor()));
 *   InProcessCdpServer.Handle handle = InProcessCdpServer.open(debugger, DebugOptions.parse("", false));
 *   // hand handle.clientChannel() to a CdpConnection/DebugSession/DebuggerPanel in the same JVM
 * </pre>
 */
public final class InProcessCdpServer {

	/**
	 * A running in-process session.
	 */
	public static final class Handle implements AutoCloseable {
		private final ServerEnd serverEnd;
		private final ClientEnd clientEnd;

		private Handle(final ServerEnd serverEnd, final ClientEnd clientEnd) {
			this.serverEnd = serverEnd;
			this.clientEnd = clientEnd;
		}

		/**
		 * The client-side channel - hand this to {@code CdpConnection.open(...)}
		 * or one of the {@code CdpClientChannel}-accepting {@code attach(...)}
		 * overloads on {@code DebugSession}/{@code DebuggerPanel}.
		 * @return the client channel
		 */
		public CdpClientChannel clientChannel() {
			return clientEnd;
		}

		@Override
		public void close() {
			serverEnd.close(1001, "server closing");
		}
	}

	private InProcessCdpServer() {
	}

	/**
	 * Starts an in-process session for a debugger: no socket, no port, no
	 * network stack of any kind - the only way to reach it is through the
	 * returned {@link Handle}'s own {@link Handle#clientChannel()}.
	 *
	 * <p>Unlike {@code CdpServer.open}, {@code options.host()}/{@code
	 * options.port()} are meaningless here and ignored.
	 * {@code options.waitForDebugger()} is deliberately NOT supported (and
	 * rejected outright): {@code CdpServer.open} can block the caller until
	 * a THIRD PARTY discovers the port and connects, but an in-process pipe
	 * has no third party - the only possible client is whoever receives the
	 * {@link Handle} this method returns, so blocking the caller before
	 * returning it would deadlock forever, not just freeze a UI thread
	 * until an external client shows up.
	 * @param debugger the debugger to expose
	 * @param options only {@code waitForDebugger()} is consulted, and must be false
	 * @return the running session's handle
	 */
	public static Handle open(final Debugger debugger, final DebugOptions options) {
		if (options.waitForDebugger()) {
			throw new IllegalArgumentException(
					"InProcessCdpServer.open() cannot honor waitForDebugger=true: "
					+ "there is no third party to attach before this call returns the only handle a client could ever use");
		}
		final AtomicBoolean closed = new AtomicBoolean();
		final ServerEnd serverEnd = new ServerEnd(closed);
		final ClientEnd clientEnd = new ClientEnd(closed);
		serverEnd.setPeer(clientEnd);
		clientEnd.setPeer(serverEnd);
		final String id = UUID.randomUUID().toString();
		Thread.ofVirtual().name("galtajs-debugger-inprocess").start(() -> new CdpSession(debugger, serverEnd, id, () -> { }).run());
		return new Handle(serverEnd, clientEnd);
	}
}
