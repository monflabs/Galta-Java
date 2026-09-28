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

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.environments.JavaScriptEnvironment;
import org.monflabs.galtajs.modules.JSInterpretedUnit;
import org.monflabs.galtajs.modules.JSMemoryModuleResolver;
import org.monflabs.galtajs.optimizer.ScriptOptimizer;
import org.monflabs.galtajs.rt.transpiler.JSTranspiledUnit;
import org.monflabs.galtajs.rt.transpiler.TranspiledGlobalRuntimeContext;
import org.monflabs.galtajs.transpiler.JSTranspiler;
import org.monflabs.galtajs.transpiler.JSTranspilerOptions;
import org.monflabs.javacompiler.FactoryClassLoader;
import org.monflabs.javacompiler.JavaCompiler;
import org.monflabs.javacompiler.JavaCompilerFactory;
import org.monflabs.javacompiler.factory.MapSourceFactory;
import org.monflabs.javacompiler.factory.MapTargetFactory;
import org.monflabs.tests.__BaseTestCase;

/**
 * Samples for docs/GaltaJS/UserGuide/ExecutionModes.md
 */
public class TranspilerExamples extends __BaseTestCase {

	private static final String FIB = "function fib(n) { return n < 2 ? n : fib(n - 1) + fib(n - 2) }";

	public void testTranspileToJavaSource() {
		JSEnvironment env = JavaScriptEnvironment.create();
		JSInterpretedUnit unit = env.createScript(FIB, "fib.js");

		JSTranspiler transpiler = new JSTranspiler(env, JSTranspilerOptions.newBuilder().build());
		JSTranspiler.Result result = transpiler.compileResult("Fib", "Object", unit.getProgram(), null);
		String java = result.getJavaCode();

		// One Java class per unit; each JS function becomes a local class inside it
		assertTrue(java.contains("class Fib extends JSTranspiledUnit"));
		assertTrue(java.contains("extends BuiltinFunctionTranspiler"));
		assertTrue(java.contains("// line: 1"));   // source positions travel as comments
	}

	public void testCompileAndRunTranspiledCode() throws Exception {
		JSEnvironment env = JavaScriptEnvironment.create();
		String java = new JSTranspiler(env, JSTranspilerOptions.newBuilder().build())
				.compileResult("Fib", "Object", env.createScript(FIB, "fib.js").getProgram(), null)
				.getJavaCode();

		// Compile in memory with the javacompiler module...
		MapTargetFactory classes = new MapTargetFactory();
		try(JavaCompiler compiler = JavaCompilerFactory.newBuilder()
				.classLoader(env.getClassLoader())
				.sourceFactory(MapSourceFactory.of("Fib.java", java))
				.targetFactory(classes)
				.build()) {
			compiler.compile("Fib");
		}
		assertTrue(classes.getFiles().containsKey("Fib.class"));

		// ...load it, and run it against a transpiled global context
		FactoryClassLoader loader = new FactoryClassLoader(env.getClassLoader(), classes);
		JSTranspiledUnit unit = (JSTranspiledUnit)loader.loadClass("Fib")
				.getConstructor(JSEnvironment.class, String.class)
				.newInstance(env, null);
		TranspiledGlobalRuntimeContext ctx = new TranspiledGlobalRuntimeContext(env, env.createProgramExecutor());
		unit.executeWithContext(ctx);
		assertEquals(55, ctx.global("fib").call(10).intValue());
	}

	public void testTranspiledModulesThroughAResolver() {
		JSMemoryModuleResolver modules = new JSMemoryModuleResolver()
				.put("calc", "export function add(a, b) { return a + b }");
		// With initTranspiler(), the resolver transpiles and compiles modules on the fly
		modules.initTranspiler(JSTranspilerOptions.newBuilder().build(), JSEnvironment.class.getClassLoader(), new MapTargetFactory());

		JSEnvironment env = JavaScriptEnvironment.newBuilder().addModuleResolver(modules).build();
		assertEquals(5, (Object)env.evaluateScript("import { add } from 'calc'; add(2, 3)"));
	}

	public void testOptimizedInterpretedMode() {
		String script = "function f(a) { if (true) { return a * 2 } return 0 }\nconst x = 2 * 3 * 7; f(x)";

		JSEnvironment plain = JavaScriptEnvironment.newBuilder().scriptOptimizer(ScriptOptimizer.emptyOptimizer()).build();
		JSEnvironment optimized = JavaScriptEnvironment.newBuilder()
				.scriptOptimizer(ScriptOptimizer.newBuilder().optimizers(ScriptOptimizer.DEFAULT_NODE_OPTIMIZER_NODES).build())
				.build();
		assertEquals(84, plain.createScript(script, "o.js").execute());
		assertEquals(84, optimized.createScript(script, "o.js").execute());

		// The decompiler prints the AST back as JavaScript, which shows what the optimizer did
		String decompiled = optimized.createScript(script, "o.js").getProgram().decompile();
		assertTrue(decompiled.contains("const x=42"));       // constant folding
		assertFalse(decompiled.contains("return 0"));         // unreachable code removed
		assertTrue(plain.createScript(script, "o.js").getProgram().decompile().contains("2  * 3  * 7"));
	}
}
