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
package tests.cdp;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.cdp.CdpServer;
import org.monflabs.galtajs.debug.api.DebugOptions;
import org.monflabs.galtajs.debug.api.impl.DebuggerImpl;
import org.monflabs.galtajs.environments.JavaScriptEnvironment;
import org.monflabs.galtajs.modules.JSInterpretedUnit;
import org.monflabs.galtajs.rt.interpreter.InterpretedGlobalRuntimeContext;

import junit.framework.TestCase;

/**
 * A client attaching over the WebSocket after the previous one went away:
 * right after it closed, or after it dropped its socket without a close.
 */
public class CdpReattachTest extends TestCase {

	private DebuggerImpl debugger;
	private CdpServer.Handle server;

	@Override
	protected void setUp() throws Exception {
		JSEnvironment env = JavaScriptEnvironment.newBuilder().debug(true).build();
		JSInterpretedUnit unit = env.createScript("1;\n", "Reattach.js");
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		debugger = new DebuggerImpl(unit, () -> {
			InterpretedGlobalRuntimeContext ctx = new InterpretedGlobalRuntimeContext(env, env.createProgramExecutor());
			ctx.setOutStream(new PrintStream(output, true));
			return ctx;
		});
		server = CdpServer.open(debugger, DebugOptions.parse("127.0.0.1:0", false));
	}

	@Override
	protected void tearDown() throws Exception {
		server.close();
		debugger.close();
	}

	public void testAttachRightAfterTheLastOneClosed() throws Exception {
		for (int i = 0; i < 20; i++) {
			try (CdpClient client = new CdpClient(server.webSocketUrl())) {
				assertNotNull("attach " + i, client.call("Runtime.enable"));
			}
		}
	}

	public void testAttachAfterTheLastOneDroppedItsSocket() throws Exception {
		URI uri = URI.create(server.webSocketUrl());
		try (Socket socket = new Socket(uri.getHost(), uri.getPort())) {
			socket.setSoTimeout(5000);
			OutputStream out = socket.getOutputStream();
			out.write(("GET " + uri.getPath() + " HTTP/1.1\r\nHost: " + uri.getHost() + ":" + uri.getPort()
					+ "\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\nSec-WebSocket-Version: 13\r\n\r\n")
					.getBytes(StandardCharsets.ISO_8859_1));
			out.flush();
			InputStream in = socket.getInputStream();
			byte[] buf = new byte[4096];
			int n = in.read(buf);
			assertTrue(new String(buf, 0, n, StandardCharsets.ISO_8859_1).startsWith("HTTP/1.1 101"));
			// closed without a WebSocket close
		}
		try (CdpClient client = new CdpClient(server.webSocketUrl())) {
			assertNotNull(client.call("Runtime.enable"));
		}
	}
}
