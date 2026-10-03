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
package tests.javascript.regression;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.JSException;
import org.monflabs.galtajs.debug.api.DebugListener;
import org.monflabs.galtajs.debug.api.ExceptionEvent;
import org.monflabs.galtajs.debug.api.impl.DebuggerImpl;
import org.monflabs.galtajs.rt.interpreter.InterpretedGlobalRuntimeContext;
import org.monflabs.galtajs.library.CommonJSLibrary;
import org.monflabs.galtajs.library.StaticLibrary;
import org.monflabs.galtajs.library.java.JavaLibrary;
import org.monflabs.galtajs.library.rhino.RhinoShellLibrary;
import org.monflabs.galtajs.modules.JSMemoryModuleResolver;
import org.monflabs.galtajs.modules.JSPathModuleResolver;
import org.monflabs.galtajs.rt.builtins.standard.StandardLibrary;
import org.monflabs.galtajs.rt.transpiler.JSTranspiledUnit;
import org.monflabs.galtajs.rt.transpiler.TranspiledGlobalRuntimeContext;
import org.monflabs.galtajs.transpiler.JSTranspiler;
import org.monflabs.galtajs.transpiler.JSTranspilerOptions;
import org.monflabs.javacompiler.FactoryClassLoader;
import org.monflabs.javacompiler.JavaCompiler;
import org.monflabs.javacompiler.JavaCompilerFactory;
import org.monflabs.javacompiler.factory.MapSourceFactory;
import org.monflabs.javacompiler.factory.MapTargetFactory;
import org.monflabs.util.model.ClassMetadata;
import org.monflabs.galtajs.environments.JavaScriptEnvironment;
import org.monflabs.galtajs.library.UnitTestLibrary;
import org.monflabs.galtajs.library.platform.FetchLibrary;
import org.monflabs.galtajs.rt.JSRuntimeUncatchableException;
import org.monflabs.tests.__BaseTestCase;

import com.sun.net.httpserver.HttpServer;

/**
 * Engine regressions (third audit round: transpiler, modules, libraries,
 * debugger) checked through the Java API.
 */
public class EngineRegression3JavaTest extends __BaseTestCase {

	// assertParseError() used to swallow its own assertion failure
	public void testAssertParseError() {
		JSEnvironment env = JavaScriptEnvironment.newBuilder().registerLibrary(new UnitTestLibrary()).build();
		env.evaluateScript("assertParseError('a ab'); assertParse('a=1')");
		try {
			env.evaluateScript("assertParseError('a=1')");
			fail();
		} catch(JSRuntimeUncatchableException e) {
			assertTrue(e.getMessage(), e.getMessage().contains("compiling without error"));
		}
	}

	// fetch(): URI policy (redirect targets included), body size limit,
	// invalid URL and forbidden header reject with a TypeError
	public void testFetchPolicyAndLimits() throws Exception {
		HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/", ex -> {
			String path = ex.getRequestURI().getPath();
			if(path.equals("/redirect")) {
				ex.getResponseHeaders().add("Location", "/secret");
				ex.sendResponseHeaders(302, -1);
			} else if(path.equals("/big")) {
				byte[] b = new byte[100];
				ex.sendResponseHeaders(200, b.length);
				ex.getResponseBody().write(b);
			} else if(path.equals("/chunked")) {
				ex.sendResponseHeaders(200, 0);
				ex.getResponseBody().write(new byte[100]);
			} else {
				byte[] b = path.getBytes(StandardCharsets.UTF_8);
				ex.sendResponseHeaders(200, b.length);
				ex.getResponseBody().write(b);
			}
			ex.close();
		});
		server.start();
		try {
			String base = "http://127.0.0.1:"+server.getAddress().getPort();
			JSEnvironment env = JavaScriptEnvironment.newBuilder()
					.registerLibrary(new FetchLibrary(uri -> !uri.getPath().startsWith("/secret"), 50))
					.build();
			String f = "async function f(url, init) { try { const r = await fetch(url, init); return await r.text() } catch(e) { return e.name+': '+e.message } }\n";
			assertEquals("/ok", (Object)env.evaluateScript(f+"await f('"+base+"/ok')"));
			assertTrue((String)env.evaluateScript(f+"await f('"+base+"/secret')"), ((String)env.evaluateScript(f+"await f('"+base+"/secret')")).startsWith("TypeError"));
			assertTrue(((String)env.evaluateScript(f+"await f('"+base+"/redirect')")).startsWith("TypeError"));
			assertTrue(((String)env.evaluateScript(f+"await f('"+base+"/big')")).startsWith("TypeError"));
			assertTrue(((String)env.evaluateScript(f+"await f('"+base+"/chunked')")).startsWith("TypeError"));
			assertTrue(((String)env.evaluateScript(f+"await f('not a url')")).startsWith("TypeError"));
			assertTrue(((String)env.evaluateScript(f+"await f('file:///etc/passwd')")).startsWith("TypeError"));
			assertTrue(((String)env.evaluateScript(f+"await f('"+base+"/ok', {headers:{Host:'x'}})")).startsWith("TypeError"));

			// Without a policy, the redirect is followed
			JSEnvironment env2 = JavaScriptEnvironment.newBuilder().registerLibrary(new FetchLibrary()).build();
			assertEquals("/secret", (Object)env2.evaluateScript(f+"await f('"+base+"/redirect')"));
		} finally {
			server.stop(0);
		}
	}

	// Runs a script transpiled, against a transpiled global context
	static Object runTranspiled(JSEnvironment env, String code) throws Exception {
		return runTranspiled(env, code, JSTranspilerOptions.newBuilder().build());
	}
	static Object runTranspiled(JSEnvironment env, String code, JSTranspilerOptions options) throws Exception {
		String java = new JSTranspiler(env, options)
				.compileResult("T", "Object", env.createScript(code, "t.js").getProgram(), null)
				.getJavaCode();
		MapTargetFactory classes = new MapTargetFactory();
		try(JavaCompiler compiler = JavaCompilerFactory.newBuilder()
				.classLoader(env.getClassLoader())
				.sourceFactory(MapSourceFactory.of("T.java", java))
				.targetFactory(classes)
				.build()) {
			compiler.compile("T");
		}
		FactoryClassLoader loader = new FactoryClassLoader(env.getClassLoader(), classes);
		JSTranspiledUnit unit = (JSTranspiledUnit)loader.loadClass("T")
				.getConstructor(JSEnvironment.class, String.class)
				.newInstance(env, null);
		return unit.executeWithContext(new TranspiledGlobalRuntimeContext(env, env.createProgramExecutor()));
	}

	// An ES module loaded by a transpiling resolver is compiled as a module
	public void testTranspiledESModule() {
		JSMemoryModuleResolver modules = new JSMemoryModuleResolver()
				.put("meta", "export const t = typeof import.meta; export default 'd'");
		modules.initTranspiler(JSTranspilerOptions.newBuilder().build(), JSEnvironment.class.getClassLoader(), new MapTargetFactory());
		JSEnvironment env = JavaScriptEnvironment.newBuilder().addModuleResolver(modules).build();
		assertEquals("object/d", env.evaluateScript("import d, { t } from 'meta'; t+'/'+d"));
	}

	// require(): a module body that throws runs once, and its error is reported
	public void testRequireErrorNotRetried() {
		JSMemoryModuleResolver modules = new JSMemoryModuleResolver()
				.put("bad.js", "globalThis.count = (globalThis.count||0)+1; throw new Error('boom')")
				.put("good.js", "module.exports = 42");
		modules.setCommonJS(true);
		JSEnvironment env = JavaScriptEnvironment.newBuilder().addModuleResolver(modules).registerLibrary(new CommonJSLibrary()).build();
		assertEquals("boom/1", env.evaluateScript("var r; try { require('bad') } catch(e) { r = e.message } r+'/'+globalThis.count"));
		assertEquals(42, (Object)env.evaluateScript("require('good')"));
		assertEquals(42, (Object)env.evaluateScript("require('good.js')"));
	}

	// JSPathModuleResolver: a symbolic link inside the root can't reach outside of it
	public void testPathResolverSymlink() throws Exception {
		Path outside = Files.createTempDirectory("mod-out");
		Path root = Files.createTempDirectory("mod-root");
		Files.writeString(outside.resolve("secret.js"), "export default 'secret'");
		Files.writeString(root.resolve("ok.js"), "export default 'ok'");
		try {
			Files.createSymbolicLink(root.resolve("link.js"), outside.resolve("secret.js"));
		} catch(UnsupportedOperationException | java.io.IOException e) {
			return; // no symbolic links on this file system
		}
		JSPathModuleResolver r = new JSPathModuleResolver(root);
		assertNotNull(r.getModule("ok.js"));
		assertNull(r.getModule("link.js"));
		assertNull(r.getModule("../"+outside.getFileName()+"/secret.js"));
	}

	public static class ClassHolder {
		public Class<?> clazz = Runtime.class;
		public int[] ints = new int[3];
		public List<Object> list = new ArrayList<>(List.of("a","b"));
	}
	public static class Thrower {
		public Thrower(String s) {
			throw new IllegalStateException("ctor: "+s);
		}
	}

	private static JSEnvironment javaEnv(ClassMetadata.AccessManager am, Object holder) {
		StaticLibrary lib = new StaticLibrary();
		lib.addStaticGlobal("holder", holder);
		return JSEnvironment.newBuilder()
				.enableGaltaJSExtensions()
				.registerLibrary(new StandardLibrary())
				.registerLibrary(new JavaLibrary(am))
				.registerLibrary(lib)
				.build();
	}

	// The access manager also covers Class[] results and Class-typed fields
	public void testAccessManagerClassArraysAndFields() {
		ClassMetadata.AccessManager deny = new ClassMetadata.AccessManager() {
			@Override
			public boolean canLoadClass(String className) {
				return !className.equals("java.lang.Runtime") && !className.equals("java.io.Serializable");
			}
		};
		JSEnvironment env = javaEnv(deny, new ClassHolder());
		try {
			env.evaluateScript("holder.clazz");
			fail();
		} catch(JSException e) {
			assertTrue(e.getMessage(), e.getMessage().contains("cannot be loaded"));
		}
		try {
			env.evaluateScript("var R=Java.type('java.util.Random'); new R().getClass().getInterfaces()");
			fail();
		} catch(JSException e) {
			assertTrue(e.getMessage(), e.getMessage().contains("cannot be loaded"));
		}
		JSEnvironment open = javaEnv(null, new ClassHolder());
		assertEquals("java.lang.Runtime", open.evaluateScript("holder.clazz.getName()"));
	}

	// Java arrays and lists: converted writes, out of range accesses;
	// constructors: the caller's arguments are untouched, the Java exception is reported
	public void testJavaArraysListsAndConstructors() {
		ClassHolder holder = new ClassHolder();
		JSEnvironment env = javaEnv(null, holder);
		env.evaluateScript("holder.ints[1] = 3.5*2; holder.ints[2] = 4");
		assertEquals(7, holder.ints[1]);
		assertEquals(4, holder.ints[2]);
		assertEquals(Boolean.TRUE, env.evaluateScript("holder.list[5] === undefined"));
		env.evaluateScript("holder.list[2] = 'c'");
		assertEquals(List.of("a","b","c"), holder.list);
		env.evaluateScript("holder.list[5] = 'x'");
		assertEquals(java.util.Arrays.asList("a","b","c",null,null,"x"), holder.list);
		String r = String.valueOf((Object)env.evaluateScript("var T=Java.type('"+Thrower.class.getName()+"'); var r; try { r = 'created:'+new T('x') } catch(e) { r = 'caught:'+e+'/'+e.message } r"));
		assertTrue(r, r.contains("ctor: x"));
	}

	// RhinoShell load(): only files inside the base directory, and transpiled callers work
	public void testRhinoShellLoad() throws Exception {
		Path base = Files.createTempDirectory("rhino-base");
		Path outside = Files.createTempFile("rhino-out", ".js");
		Files.writeString(base.resolve("lib.js"), "var loaded = 'yes';");
		Files.writeString(outside, "var loaded = 'outside';");
		JSEnvironment env = JavaScriptEnvironment.newBuilder()
				.registerLibrary(new RhinoShellLibrary())
				.putProperty(RhinoShellLibrary.PROPERTY_BASEDIR, base.toFile())
				.build();
		assertEquals("yes", env.evaluateScript("load('lib.js'); loaded"));
		assertEquals("Error", env.evaluateScript("var r; try { load('../"+outside.getFileName()+"') } catch(e) { r = e.name } r"));
		runTranspiled(env, "load('lib.js'); if(loaded!=='yes') throw new Error('not loaded: '+loaded)");
	}

	// The debugger's execution thread reports an uncaught exception to the
	// listeners, and is a daemon thread
	public void testDebuggerUncaughtException() throws Exception {
		JSEnvironment env = JavaScriptEnvironment.newBuilder().debug(true).build();
		DebuggerImpl debugger = new DebuggerImpl(env.createScript("throw new TypeError('uncaught!')", "dbg.js"),
				() -> new InterpretedGlobalRuntimeContext(env, env.createProgramExecutor()));
		java.util.concurrent.BlockingQueue<ExceptionEvent> events = new java.util.concurrent.ArrayBlockingQueue<>(4);
		java.util.concurrent.CountDownLatch finished = new java.util.concurrent.CountDownLatch(1);
		debugger.addListener(new DebugListener() {
			@Override
			public void exceptionThrown(ExceptionEvent event) {
				events.add(event);
			}
			@Override
			public void executionFinished() {
				finished.countDown();
			}
		});
		debugger.start();
		assertTrue(debugger.getExecutionThread().isDaemon());
		ExceptionEvent event = events.poll(10, java.util.concurrent.TimeUnit.SECONDS);
		assertNotNull(event);
		assertTrue(event.message(), event.message().contains("uncaught!"));
		assertTrue(finished.await(10, java.util.concurrent.TimeUnit.SECONDS));
	}

	// Preprocessor directives are whole words at the start of a line comment;
	// a source without any is returned as is
	public void testPreprocessorDirectiveWords() {
		java.util.Map<String,Object> sym = java.util.Map.of("A", true);
		String noDirective = "var u = 'http://x/#if';\r\n//#ifdebug\r\n//#elsewhere\r\nvar y = 1;";
		assertSame(noDirective, org.monflabs.galtajs.preprocessor.ScriptPreProcessor.preprocess(noDirective, sym));
		String out = org.monflabs.galtajs.preprocessor.ScriptPreProcessor.preprocess("//#if A\na\n//#elsewhere\nb\n//#else\nc\n//#endif\n", sym);
		assertEquals("//#if A\na\n//#elsewhere\nb\n//#else\n//c\n//#endif\n", out);
	}

	// delete through a with: HasBinding is [[HasProperty]], so an inherited
	// property resolves to the with-object, whose [[Delete]] (no own
	// property) returns true - the outer binding is untouched. Transpiled
	// mode only: the interpreter still checks for an own property (KNOWN BUG,
	// rt/interpreter, reported).
	public void testTranspiledDeleteThroughWith() throws Exception {
		JSEnvironment env = JavaScriptEnvironment.create();
		runTranspiled(env, """
			var deleteWithOuter = 'outer';
			var o = Object.create({ deleteWithOuter: 1 });
			var r;
			with(o) {
				r = delete deleteWithOuter;
			}
			if(r!==true || deleteWithOuter!=='outer' || o.deleteWithOuter!==1) {
				throw new Error('delete through with: '+r+' '+deleteWithOuter+' '+o.deleteWithOuter);
			}
			""");
	}

	// Over 50 functions in one scope, the transpiler routes the calls through dispatch
	// classes: 1200 functions span 3 chunks of 500 and their blocks of 200, each must be
	// reached, and a context-free (elided) function still recurses and closes correctly
	public void testTranspiledFunctionDispatch() throws Exception {
		JSEnvironment env = JavaScriptEnvironment.create();
		StringBuilder code = new StringBuilder();
		int n = 1200;
		for (int i = 0; i < n; i++) {
			code.append("function f").append(i).append("(){ return ").append(i).append("; }\n");
		}
		code.append("function fact(k){ return k<=1 ? 1 : k*fact(k-1); }\n");
		code.append("function counter(){ var c=0; return function(){ return ++c; }; }\n");
		code.append("var sum=0; for (var i=0;i<").append(n).append(";i++){ sum += globalThis['f'+i](); }\n");
		code.append("var c=counter(); c(); c();\n");
		long expected = (long) n * (n - 1) / 2;
		code.append("var r = sum + ':' + fact(10) + ':' + c();\n");
		code.append("if (r !== '").append(expected).append(":3628800:3') throw new Error('dispatch: ' + r);");
		runTranspiled(env, code.toString());
	}

	// Large functions have statement lists moved to regions (lambdas run at once):
	// with a budget of 1, every list is, and the control flow crossing them -
	// labeled break/continue, switch fall-through, try/finally, return values,
	// exceptions, per-iteration closures - must behave as when interpreted
	public void testTranspiledMethodRegions() throws Exception {
		String code = """
			function run() {
				var out = [];
				outer: for (var i = 0; i < 4; i++) {
					inner: for (var j = 0; j < 4; j++) {
						if (j == 1) continue;
						if (j == 3) continue outer;
						if (i == 3) break outer;
						out.push(i + '' + j);
					}
				}
				for (var k = 0; k < 6; k++) {
					switch (k) {
						case 0: out.push('a');
						case 1: out.push('b'); break;
						case 2: out.push('c'); continue;
						case 3: { out.push('d'); break; }
						default: out.push('e');
					}
					out.push('k' + k);
				}
				function find(list, v) {
					for (var x of list) {
						try {
							if (x === v) { return 'found ' + x; }
							if (x > 100) { break; }
						} finally {
							out.push('f' + x);
						}
					}
					return 'none';
				}
				out.push(find([1, 2, 3], 2), find([5, 200, 6], 6));
				var fns = [];
				for (let n = 0; n < 3; n++) { let m = n * 10; fns.push(function () { return n + m; }); }
				out.push(fns.map(function (f) { return f(); }).join('/'));
				try {
					(function () { var o = { a: 1, b: 2 }; for (var key in o) { if (key == 'b') throw new Error('in ' + key); } })();
				} catch (e) { out.push(e.message); }
				var d = 0;
				do { d++; if (d == 2) continue; out.push('d' + d); } while (d < 3);
				blk: { out.push('x'); if (out.length > 0) break blk; out.push('never'); }
				function sum() { var s = 0; for (var q = 0; q < arguments.length; q++) s += arguments[q]; return s; }
				var obj = { v: 7, get: function () { return this.v; } };
				function fact(n) { if (n <= 1) { return 1; } return n * fact(n - 1); }
				out.push(sum(1, 2, 3), obj.get(), fact(6), (function () { if (true) { return; } })());
				return out.join(',');
			}
			""";
		JSEnvironment env = JavaScriptEnvironment.create();
		Object expected = env.evaluateScript(code + "run();");
		JSTranspilerOptions split = JSTranspilerOptions.newBuilder().methodBudget(1).build();
		String java = new JSTranspiler(env, split).compileResult("T", "Object", env.createScript(code, "t.js").getProgram(), null).getJavaCode();
		assertTrue("no region was generated", java.contains("JSTranspiledRegion"));
		String check = code + "var r = run(); if (r !== " + org.monflabs.json.JsonUtil.encodeString(expected.toString(), '"') + ") throw new Error('regions: ' + r);";
		runTranspiled(JavaScriptEnvironment.create(), check, split);
	}
}
