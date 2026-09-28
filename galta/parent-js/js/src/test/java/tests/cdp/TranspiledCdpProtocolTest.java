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

import java.lang.reflect.Constructor;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.cdp.CdpServer;
import org.monflabs.galtajs.debug.api.DebugOptions;
import org.monflabs.galtajs.debug.api.impl.DebuggerImpl;
import org.monflabs.galtajs.environments.JavaScriptEnvironment;
import org.monflabs.galtajs.modules.JSInterpretedUnit;
import org.monflabs.galtajs.rt.transpiler.JSTranspiledUnit;
import org.monflabs.galtajs.rt.transpiler.TranspiledGlobalRuntimeContext;
import org.monflabs.galtajs.transpiler.JSTranspiler;
import org.monflabs.galtajs.transpiler.JSTranspilerOptions;
import org.monflabs.javacompiler.JavaCompiler;
import org.monflabs.javacompiler.JavaCompilerFactory;
import org.monflabs.util.path.FilesUtil;
import org.monflabs.util.path.PathClassLoader;

import junit.framework.TestCase;

/**
 * The SAME {@code DebuggerImpl}/{@code CdpServer}/{@code CdpSession} stack
 * {@link CdpProtocolTest} exercises for interpreted mode, driven this time
 * against a script compiled with the REAL transpiler
 * ({@code JSTranspilerOptions.debuggable(true)}) - proves the mode-neutral
 * refactor actually holds at the CDP wire-protocol level too, not just
 * through the facade directly (see {@code TranspiledDebuggerImplTest} in the
 * {@code js} module for that narrower check).
 */
public class TranspiledCdpProtocolTest extends TestCase {

	private DebuggerImpl debugger;
	private CdpServer.Handle server;
	private CdpClient client;

	private JSTranspiledUnit compileAndLoad(JSEnvironment env, String className, String script) throws Exception {
		JSInterpretedUnit source = env.createScript(script, className + ".js");
		JSTranspilerOptions options = JSTranspilerOptions.newBuilder().debuggable(true).build();
		JSTranspiler transpiler = new JSTranspiler(env, options);
		String javaCode = transpiler.compile(className, "Object", source);

		Path root = Files.createTempDirectory("galtajs-cdp-transpile");
		Path src = root.resolve("src");
		Files.createDirectory(src);
		Path tgt = root.resolve("tgt");
		Files.createDirectory(tgt);
		FilesUtil.writeString(src.resolve(className + ".java"), javaCode, StandardCharsets.UTF_8);

		try (JavaCompiler cp = JavaCompilerFactory.newBuilder()
				.classLoader(getClass().getClassLoader())
				.sourceFolder(src, StandardCharsets.UTF_8)
				.targetFolder(tgt)
				.build()) {
			cp.compile(className);
		}

		PathClassLoader cl = new PathClassLoader(getClass().getClassLoader(), tgt);
		Constructor<?> ctor = cl.loadClass(className).getConstructor(JSEnvironment.class, String.class);
		return (JSTranspiledUnit) ctor.newInstance(env, className + ".js");
	}

	@Override
	protected void tearDown() throws Exception {
		if (client != null) {
			client.close();
		}
		if (server != null) {
			server.close();
		}
		if (debugger != null) {
			debugger.close();
		}
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> map(final Object o) {
		return (Map<String, Object>) o;
	}

	@SuppressWarnings("unchecked")
	private static List<Object> list(final Object o) {
		return (List<Object>) o;
	}

	// Deliberately a TOP-LEVEL breakpoint/scope check, not a function-local
	// one: transpiled global variables are wired into a readable scope
	// object (TranspiledRuntimeContext.initGlobalVariables()), but ordinary
	// (non-generator) transpiled FUNCTIONS never populate
	// TranspiledFunctionRuntimeContext's locals array at all - confirmed the
	// hard way (Runtime.getProperties on a paused function's local scope
	// came back empty). Emitting the equivalent of interpreted mode's local-
	// name table for transpiled function locals is real, additional
	// transpiler codegen (a new $LOCAL_NAMES-style constant plus a
	// setLocals() call at every ordinary function's own entry) - out of
	// scope here; a known, documented gap for a future pass, not something
	// this test papers over.
	public void testTranspiledBreakpointPauseScopesResume() throws Exception {
		JSEnvironment env = JavaScriptEnvironment.newBuilder().build();
		// Deliberately no loop: a breakpoint inside a loop body fires once
		// PER ITERATION (correct debugger behavior), which a single
		// pause/resume pair in this test can't drain by itself - keeping
		// the script to plain top-level statements means the breakpoint
		// executes exactly once.
		JSTranspiledUnit unit = compileAndLoad(env, "TranspiledCdpTestClass", """
				var total = 5;
				console.log(total);
				""");
		debugger = new DebuggerImpl(unit, () -> new TranspiledGlobalRuntimeContext(env, env.createProgramExecutor()));
		server = CdpServer.open(debugger, DebugOptions.parse("127.0.0.1:0", false));
		client = new CdpClient(server.webSocketUrl());
		client.call("Runtime.enable");
		client.call("Debugger.enable");

		// "console.log(total);" - 0-based line 1 (1-based line 2)
		final Map<String, Object> set = client.call("Debugger.setBreakpointByUrl", "lineNumber", 1, "url", "TranspiledCdpTestClass.js");
		final String breakpointId = (String) set.get("breakpointId");

		debugger.start();

		final Map<String, Object> paused = client.event("Debugger.paused");
		assertEquals("other", paused.get("reason"));
		assertEquals(List.of(breakpointId), list(paused.get("hitBreakpoints")));
		final List<Object> frames = list(paused.get("callFrames"));
		assertFalse(frames.isEmpty());
		final Map<String, Object> top = map(frames.get(0));
		assertEquals(1L, ((Number) map(top.get("location")).get("lineNumber")).longValue());

		// the global scope's properties - `total` (a top-level var) lives here.
		final List<Object> scopes = list(top.get("scopeChain"));
		final Map<String, Object> globalScope = map(scopes.get(scopes.size() - 1));
		assertEquals("global", globalScope.get("type"));
		final String globalId = (String) map(globalScope.get("object")).get("objectId");
		final Map<String, Object> props = client.call("Runtime.getProperties", "objectId", globalId, "ownProperties", true);
		boolean sawTotal = false;
		for (final Object p : list(props.get("result"))) {
			if ("total".equals(map(p).get("name"))) {
				sawTotal = true;
				assertEquals(5L, map(map(p).get("value")).get("value"));
			}
		}
		assertTrue(props.toString(), sawTotal);

		client.call("Debugger.resume");
		client.event("Debugger.resumed");
		debugger.getExecutionThread().join(5000);
		assertFalse(debugger.getExecutionThread().isAlive());
	}

	// The literal `debugger;` statement in TRANSPILED mode - previously a
	// no-op for CDP purposes (only ASTDebugger's own Java-breakpoint-hint
	// log call fired; JSTranspiledUnit.debugStatement() had no way to
	// flag a statement as the debugger statement at all, so it never
	// triggered PauseReason.DEBUGGER_STATEMENT the way interpreted mode's
	// ASTDebugHook already did).
	public void testDebuggerStatementPauses() throws Exception {
		JSEnvironment env = JavaScriptEnvironment.newBuilder().build();
		JSTranspiledUnit unit = compileAndLoad(env, "TranspiledDebuggerStmtTest", """
				var a = 1;
				debugger;
				var b = 2;
				""");
		debugger = new DebuggerImpl(unit, () -> new TranspiledGlobalRuntimeContext(env, env.createProgramExecutor()));
		server = CdpServer.open(debugger, DebugOptions.parse("127.0.0.1:0", false));
		client = new CdpClient(server.webSocketUrl());
		client.call("Runtime.enable");
		client.call("Debugger.enable");

		debugger.start();

		final Map<String, Object> paused = client.event("Debugger.paused");
		assertEquals("debuggerStatement", paused.get("reason"));
		final List<Object> frames = list(paused.get("callFrames"));
		assertFalse(frames.isEmpty());
		assertEquals(1L, ((Number) map(map(frames.get(0)).get("location")).get("lineNumber")).longValue());

		client.call("Debugger.resume");
		client.event("Debugger.resumed");
		debugger.getExecutionThread().join(5000);
		assertFalse(debugger.getExecutionThread().isAlive());
	}
}
