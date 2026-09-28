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
package tests.debug;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Constructor;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import org.monflabs.filesystem.memory.MemoryFileSystem;
import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.debug.api.BreakpointRequest;
import org.monflabs.galtajs.debug.api.DebugFrame;
import org.monflabs.galtajs.debug.api.DebugListener;
import org.monflabs.galtajs.debug.api.DebugProperty;
import org.monflabs.galtajs.debug.api.DebugScope;
import org.monflabs.galtajs.debug.api.PauseReason;
import org.monflabs.galtajs.debug.api.PausedEvent;
import org.monflabs.galtajs.debug.api.impl.DebuggerImpl;
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
import util.GlobalTestEnvironment;

/**
 * End-to-end test of Phase 2 (transpiled-mode) debugging - compiles the
 * script through the REAL transpiler with
 * {@code JSTranspilerOptions.debuggable(true)}, loads and runs the
 * generated class, and drives the exact same {@code DebuggerImpl} facade
 * DebuggerImplTest already exercises for interpreted mode: breakpoint,
 * pause, inspect a scope variable, resume, confirm final output. Proves the
 * mode-neutral refactor (DebugHook/DebugLocation/Debuggable) actually
 * lets ONE facade implementation drive both execution modes.
 *
 * Known v1 gaps this test does NOT exercise (see this session's own
 * documented scope): pause-on-exception, evaluating an expression on a
 * paused transpiled frame, and a multi-frame call stack - all explicitly
 * out of scope for transpiled mode's first cut.
 */
public class TranspiledDebuggerImplTest extends TestCase {

	private static final String SCRIPT = """
			var x = 10;
			var out = [];
			for(let i=0; i<3; i++) {
			  var y = x + i;
			  out.push(y);
			}
			console.log(out.join(","));
			""";

	private JSTranspiledUnit compileAndLoad(JSEnvironment env, String className) throws Exception {
		JSInterpretedUnit source = env.createScript(SCRIPT, className + ".js");
		JSTranspilerOptions options = JSTranspilerOptions.newBuilder()
				.debuggable(true)
				.build();
		JSTranspiler transpiler = new JSTranspiler(env, options);
		String javaCode = transpiler.compile(className, "Object", source);

		FileSystem fs = MemoryFileSystem.newBuilder().build();
		Path src = fs.getPath("src");
		Files.createDirectory(src);
		Path tgt = fs.getPath("tgt");
		Files.createDirectory(tgt);
		Path srcFile = src.resolve(className + ".java");
		FilesUtil.writeString(srcFile, javaCode, StandardCharsets.UTF_8);

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

	public void testBreakpointPauseInspectResumeTranspiled() throws Exception {
		JSEnvironment env = GlobalTestEnvironment.create();
		JSTranspiledUnit unit = compileAndLoad(env, "TranspiledDebuggerImplTestClass");

		ByteArrayOutputStream captured = new ByteArrayOutputStream();
		DebuggerImpl debugger = new DebuggerImpl(unit, () -> {
			TranspiledGlobalRuntimeContext ctx = new TranspiledGlobalRuntimeContext(env, env.createProgramExecutor());
			ctx.setOutStream(new PrintStream(captured, true));
			return ctx;
		});

		BlockingQueue<PausedEvent> pauses = new ArrayBlockingQueue<>(16);
		debugger.addListener(new DebugListener() {
			@Override
			public void paused(PausedEvent event) {
				pauses.add(event);
			}
		});

		// "out.push(y);" - line 5
		debugger.setBreakpoint(BreakpointRequest.at("TranspiledDebuggerImplTestClass.js", 5));

		debugger.start();
		try {
			for (int i = 0; i < 3; i++) {
				PausedEvent event = pauses.poll(5, TimeUnit.SECONDS);
				assertNotNull("expected a pause for iteration " + i, event);
				assertEquals(PauseReason.BREAKPOINT, event.reason());
				assertEquals(List.of("bp1"), event.hitBreakpoints());

				List<DebugFrame> frames = event.frames();
				assertFalse(frames.isEmpty());
				DebugFrame top = frames.get(0);
				assertEquals(5, top.location().line());

				Object yValue = null;
				boolean foundY = false;
				for (DebugScope scope : top.scopes()) {
					for (DebugProperty p : debugger.values().ownProperties(scope.object(), true, true)) {
						if ("y".equals(p.name())) {
							foundY = true;
							yValue = p.value();
						}
					}
				}
				assertTrue("expected to find 'y' in some scope", foundY);
				assertEquals(Integer.valueOf(10 + i), yValue);

				event.resume();
			}

			Thread execThread = debugger.getExecutionThread();
			execThread.join(5000);
			assertFalse(execThread.isAlive());
			assertEquals("10,11,12", captured.toString().trim());
		} finally {
			debugger.close();
		}
	}

	public void testNoDebuggerAttachedRunsNormallyTranspiled() throws Exception {
		JSEnvironment env = GlobalTestEnvironment.create();
		JSTranspiledUnit unit = compileAndLoad(env, "TranspiledDebuggerImplTestClass2");

		ByteArrayOutputStream captured = new ByteArrayOutputStream();
		TranspiledGlobalRuntimeContext ctx = new TranspiledGlobalRuntimeContext(env, env.createProgramExecutor());
		ctx.setOutStream(new PrintStream(captured, true));

		unit.executeWithContext(ctx);

		assertEquals("10,11,12", captured.toString().trim());
	}
}
