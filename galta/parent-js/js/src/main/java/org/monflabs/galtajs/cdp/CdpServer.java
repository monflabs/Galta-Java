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
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;

import org.monflabs.galtajs.cdp.json.Json;
import org.monflabs.galtajs.cdp.protocol.CdpSession;
import org.monflabs.galtajs.cdp.ws.HttpWebSocketServer;
import org.monflabs.galtajs.debug.api.Debugger;
import org.monflabs.galtajs.debug.api.DebuggerFrontend;
import org.monflabs.galtajs.debug.api.DebugOptions;

/**
 * The Chrome DevTools Protocol server for a GaltaJS {@link Debugger}.
 * Listens on one port for the discovery documents Chrome and VS Code fetch
 * and for the WebSocket a client then opens, exactly as Node's inspector
 * does, so any client that can attach to Node can attach here.
 *
 * <pre>
 *   JSEnvironment env = JavaScriptEnvironment.newBuilder().debug(true).build();
 *   JSInterpretedUnit unit = env.createScript(source, "app.js");
 *   DebuggerImpl debugger = new DebuggerImpl(unit, () -&gt; new InterpretedGlobalRuntimeContext(env, env.createProgramExecutor()));
 *   try (CdpServer.Handle handle = CdpServer.open(debugger, DebugOptions.parse("9229", false))) {
 *       debugger.start();
 *       ...
 *   }
 * </pre>
 *
 * <p>Ported from a sibling project's own Chrome DevTools Protocol debugger
 * (same author) - only the discovery document's browser string and the
 * package names changed.
 */
public final class CdpServer implements DebuggerFrontend {

	/**
	 * A running server.
	 */
	public static final class Handle implements AutoCloseable {
		private final HttpWebSocketServer server;
		private final String id;

		private Handle(final HttpWebSocketServer server, final String id) {
			this.server = server;
			this.id = id;
		}

		/**
		 * The port the server listens on.
		 * @return the port
		 */
		public int port() {
			return server.port();
		}

		/**
		 * The WebSocket url a client connects to.
		 * @return the url
		 */
		public String webSocketUrl() {
			return "ws://" + server.host() + ":" + server.port() + "/" + id;
		}

		@Override
		public void close() {
			server.close();
		}
	}

	/**
	 * For the service loader.
	 */
	public CdpServer() {
	}

	@Override
	public String name() {
		return "Chrome DevTools Protocol";
	}

	@Override
	public AutoCloseable start(final Debugger debugger, final DebugOptions options) throws IOException {
		return startServer(debugger, options);
	}

	/**
	 * Starts a server for a debugger.
	 *
	 * @param debugger the debugger to expose
	 * @param options where to listen, and whether to wait for a client before returning
	 * @return the running server
	 * @throws IOException if the port cannot be bound
	 */
	public static Handle open(final Debugger debugger, final DebugOptions options) throws IOException {
		return startServer(debugger, options);
	}

	private static Handle startServer(final Debugger debugger, final DebugOptions options) throws IOException {
		final String id = UUID.randomUUID().toString();
		final CountDownLatch waiting = new CountDownLatch(options.waitForDebugger() ? 1 : 0);
		final HttpWebSocketServer[] holder = new HttpWebSocketServer[1];
		final HttpWebSocketServer server = new HttpWebSocketServer(options.host(), options.port(), "/" + id,
				() -> discovery(holder[0], id),
				() -> Json.write(Json.object("Browser", "GaltaJS", "Protocol-Version", "1.3")),
				connection -> new CdpSession(debugger, connection, id, waiting::countDown).run());
		holder[0] = server;
		server.start();
		final Handle handle = new Handle(server, id);
		System.err.println("Debugger listening on " + handle.webSocketUrl());
		System.err.println("For help, see: https://nodejs.org/en/docs/inspector");
		if (options.waitForDebugger()) {
			debugger.pauseOnStart();
			try {
				waiting.await();
			} catch (final InterruptedException e) {
				Thread.currentThread().interrupt();
			}
			System.err.println("Debugger attached.");
		}
		return handle;
	}

	private static String discovery(final HttpWebSocketServer server, final String id) {
		final String address = server.host() + ":" + server.port();
		return Json.write(List.of(Json.object(
				"description", "galtajs instance",
				"devtoolsFrontendUrl", "devtools://devtools/bundled/js_app.html?experiments=true&v8only=true&ws=" + address + "/" + id,
				"devtoolsFrontendUrlCompat", "devtools://devtools/bundled/inspector.html?experiments=true&v8only=true&ws=" + address + "/" + id,
				"faviconUrl", "https://nodejs.org/static/images/favicons/favicon.ico",
				"id", id,
				"title", "galtajs[" + ProcessHandle.current().pid() + "]",
				"type", "node",
				"url", "file://",
				"webSocketDebuggerUrl", "ws://" + address + "/" + id)));
	}
}
