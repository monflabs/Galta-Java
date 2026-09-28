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
package doc_examples;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.JSModule;
import org.monflabs.galtajs.environments.JavaScriptEnvironment;
import org.monflabs.galtajs.jsonfactory.JSObject;
import org.monflabs.galtajs.modules.JSInterpretedUnit;
import org.monflabs.galtajs.rt.JSGlobalContext;
import org.monflabs.galtajs.rt.JSRuntimeException;
import org.monflabs.galtajs.rt.JSScriptExecutor;
import org.monflabs.galtajs.rt.interpreter.InterpretedGlobalRuntimeContext;
import org.monflabs.tests.__BaseTestCase;

import static doc_examples.DocExampleSupport.*;

/**
 * Samples for docs/GaltaJS/UserGuide/ExecutingCode.md
 */
public class ExecutingCodeExamples extends __BaseTestCase {

	public void testCompileOnceRunManyTimes() {
		JSEnvironment env = JavaScriptEnvironment.create();
		JSInterpretedUnit unit = env.createScript("Math.max(...[3, 9, 4])", "max.js");
		assertEquals(9, unit.execute());
		assertEquals(9, unit.execute());   // the parsed program is reused
	}

	public void testSharedContextAcrossScripts() {
		JSEnvironment env = JavaScriptEnvironment.create();
		// A global context holds the variables, the executor (event loop) and the output streams
		JSGlobalContext ctx = new InterpretedGlobalRuntimeContext(env, env.createProgramExecutor());

		env.createScript("var counter = 0; function inc() { return ++counter }", "defs.js").executeWithContext(ctx);
		env.createScript("inc(); inc()", "calls.js").executeWithContext(ctx);
		assertEquals(3, env.createScript("inc()", "more.js").executeWithContext(ctx));

		// Java can read the globals back through the same context
		assertEquals(3, ctx.global("counter").intValue());
		assertEquals(4, ctx.global("inc").call().intValue());
	}

	public void testReuseAcrossSessions() {
		// Built once per application: intrinsics, accessors, libraries
		JSEnvironment env = JavaScriptEnvironment.create();
		// Parsed once, executed in every session
		JSInterpretedUnit unit = env.createScript("`hello ` + name", "greet.js");

		StringBuilder out = new StringBuilder();
		for (String who : List.of("ada", "alan")) {
			// One context per session - this is the isolation boundary
			JSGlobalContext session = new InterpretedGlobalRuntimeContext(env, env.createProgramExecutor());
			session.getGlobalThis().setOwnProperty("name", who);
			out.append(unit.executeWithContext(session)).append(';');
		}
		assertEquals("hello ada;hello alan;", out.toString());
	}

	public void testSessionsShareIntrinsicsNotGlobals() {
		JSEnvironment env = JavaScriptEnvironment.create();
		JSGlobalContext s1 = new InterpretedGlobalRuntimeContext(env, env.createProgramExecutor());
		JSGlobalContext s2 = new InterpretedGlobalRuntimeContext(env, env.createProgramExecutor());

		// Globals are isolated per context
		env.createScript("var secret = 1", "a.js").executeWithContext(s1);
		assertEquals("undefined", env.createScript("typeof secret", "b.js").executeWithContext(s2));

		// ...but the intrinsics live on the environment, so they are shared
		env.createScript("Array.prototype.mine = () => 1", "c.js").executeWithContext(s1);
		assertEquals("function", env.createScript("typeof [].mine", "d.js").executeWithContext(s2));

		// Full isolation means a separate environment
		JSEnvironment other = JavaScriptEnvironment.create();
		JSGlobalContext s3 = new InterpretedGlobalRuntimeContext(other, other.createProgramExecutor());
		assertEquals("undefined", other.createScript("typeof [].mine", "e.js").executeWithContext(s3));
	}

	public void testGlobalsInstalledFromJava() {
		JSEnvironment env = JavaScriptEnvironment.create();
		JSGlobalContext ctx = new InterpretedGlobalRuntimeContext(env, env.createProgramExecutor());
		ctx.getGlobalThis().setOwnProperty("config", JSObject.of(env, "retries", 3, "hosts", List.of("a", "b")));
		ctx.getGlobalThis().setOwnProperty("answer", 42);

		Object r = env.createScript("`${config.retries} retries on ${config.hosts.length} hosts, answer ${answer}`", "g.js")
				.executeWithContext(ctx);
		assertEquals("3 retries on 2 hosts, answer 42", r);
	}

	public void testThisArgument() {
		JSEnvironment env = JavaScriptEnvironment.create();
		JSObject self = JSObject.of(env, "name", "ctx");
		assertEquals("ctx!", (Object)env.evaluateScript("this.name + '!'", self));
		assertEquals("ctx!", (Object)env.evaluateExpression("this.name + '!'", self));
	}

	public void testCapturingOutput() {
		JSEnvironment env = JavaScriptEnvironment.create();
		Captured run = captureOutput(env, ctx -> {
			ctx.getGlobalThis().setOwnProperty("who", "world");
			return env.createScript("console.log(`hello ${who}`); console.error('oops'); 1", "out.js").executeWithContext(ctx);
		});
		assertEquals(1, run.value());
		assertEquals(lines("hello world", "oops"), run.output());
	}

	public void testScriptExecutorWithFiles() throws Exception {
		Path root = Files.createTempDirectory("galta-scripts");
		Files.writeString(root.resolve("defs.js"), "var base = 10; function add(n) { return base + n }");
		Files.writeString(root.resolve("main.js"), "add(5)");

		// JSScriptExecutor keeps one context, so files and snippets share their globals
		JSScriptExecutor exec = new JSScriptExecutor(JavaScriptEnvironment.create(), root);
		exec.executeFile("defs.js");
		assertEquals(15, exec.executeFile("main.js"));
		assertEquals(20, exec.execute("base * 2"));

		exec.clearContext();   // start from a fresh global scope
		try {
			exec.execute("base");
			fail();
		} catch(JSRuntimeException e) {
			assertTrue(e.getMessage().contains("ReferenceError: Unknown identifier base"));
		}
	}

	public void testRunningAModuleUnit() {
		JSEnvironment env = JavaScriptEnvironment.create();
		// SCRIPT_MODULE parses the text as an ES module instead of a classic script
		JSInterpretedUnit unit = env.createScript(
				"export const version = 3; export default function greet(n) { return 'hi ' + n }",
				"lib.js", JSEnvironment.SCRIPT_MODULE);
		JSGlobalContext ctx = new InterpretedGlobalRuntimeContext(env, env.createProgramExecutor());
		JSModule module = unit.initModule(ctx, false);
		assertEquals(3, module.getExport("version"));
		assertEquals("hi you", ctx.value(module.getDefaultExport()).call("you").stringValue());
	}
}
