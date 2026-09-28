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

import static org.junit.Assert.assertThrows;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.JSException;
import org.monflabs.galtajs.environments.JavaScriptEnvironment;
import org.monflabs.galtajs.jsonfactory.JSObject;
import org.monflabs.galtajs.rt.builtins.standard.StandardLibrary;
import org.monflabs.galtajs.library.java.JavaLibrary;
import org.monflabs.galtajs.modules.JSInterpretedUnit;
import org.monflabs.galtajs.modules.JSMemoryModuleResolver;
import org.monflabs.galtajs.modules.JSTranspiledModuleResolver;
import org.monflabs.galtajs.rt.util.ConcurrentWeakIdentitytMap;
import org.monflabs.galtajs.transpiler.JSTranspilerOptions;
import org.monflabs.javacompiler.FactoryClassLoader;
import org.monflabs.javacompiler.factory.MapTargetFactory;
import org.monflabs.tests.__BaseTestCase;
import org.monflabs.util.model.ClassMetadata;

/**
 * Engine regressions that are checked through the Java API (independent of the
 * execution mode of the test suites).
 */
public class EngineRegressionJavaTest extends __BaseTestCase {

	// ASTProgram: hoisting is per execution, not a flag left on the shared AST
	public void testHoistingOnEveryExecution() {
		JSEnvironment env = JavaScriptEnvironment.create();
		JSInterpretedUnit unit = env.createScript("\"use strict\"; x = 5; var x; x", "hoist.js");
		assertEquals(5, unit.execute());
		assertEquals(5, unit.execute());
		JSInterpretedUnit fn = env.createScript("f(); function f(){ return 1 }", "hoistfn.js");
		assertEquals(1, fn.execute());
		assertEquals(1, fn.execute());
	}

	// ASTRootStatementList: the directive prologue is found through the debug hooks
	public void testUseStrictInDebugMode() {
		JSEnvironment env = JavaScriptEnvironment.newBuilder().debug(true).build();
		assertEquals("undefined", env.createScript("\"use strict\"; typeof (function(){ return this })()", "d1.js").execute());
		assertEquals("undefined", env.createScript("(function(){ 'use strict'; return typeof this })()", "d2.js").execute());
		assertEquals("object", env.createScript("typeof (function(){ return this })()", "d3.js").execute());
	}

	// ASTClassMember: a private name used in a computed key is validated
	public void testPrivateNameInComputedKey() {
		JSEnvironment env = JavaScriptEnvironment.create();
		assertThrows(JSException.class, () -> env.createScript("class C{ #x; m(o){ class D{ [o.#nope](){} } } }", "p.js").execute());
		assertEquals("m", env.createScript("class C{ #x='m'; k(o){ class D{ [o.#x](){} } return Object.getOwnPropertyNames(D.prototype)[1] } } new C().k(new C())", "p2.js").execute());
	}

	public static class Bean {
		public String name = "initial";
	}

	// ObjectAccessorWrapper: writes go to the wrapped object
	public void testObjectAccessorWrapperWrite() {
		JSEnvironment env = JavaScriptEnvironment.newBuilder().registerLibrary(new JavaLibrary()).build();
		Bean bean = new Bean();
		JSObject o = JSObject.from(env, bean);
		o.setOwnProperty("name", "updated");
		assertEquals("updated", bean.name);
		assertEquals("updated", o.getOwnProperty("name"));
	}

	private static JSEnvironment javaEnv(ClassMetadata.AccessManager am) {
		return JSEnvironment.newBuilder()
				.enableGaltaJSExtensions()
				.registerLibrary(new StandardLibrary())
				.registerLibrary(new JavaLibrary(am))
				.build();
	}

	// AccessManager: reflection can't be used to reach denied classes
	public void testAccessManagerReflection() {
		ClassMetadata.AccessManager denyReflection = new ClassMetadata.AccessManager() {
			@Override
			public boolean canLoadClass(String className) {
				return !className.equals("java.lang.Runtime");
			}
			@Override
			public boolean canAccessMember(Class<?> c) {
				return c!=Class.class && !c.getName().startsWith("java.lang.reflect.") && c!=Runtime.class;
			}
		};
		JSEnvironment env = javaEnv(denyReflection);
		assertEquals(3, (Object)env.evaluateScript("Java.type('java.lang.Math').abs(-3)"));
		// java.lang.Class members are not exposed
		assertThrows(JSException.class, () -> env.evaluateScript("var R=Java.type('java.util.Random'); new R().getClass().getMethods()"));
		assertThrows(JSException.class, () -> env.evaluateScript("var R=Java.type('java.util.Random'); new R().getClass().getClass().getMethods()"));

		// Only the class name is denied: a reflective handle to it is refused too
		ClassMetadata.AccessManager denyRuntime = new ClassMetadata.AccessManager() {
			@Override
			public boolean canLoadClass(String className) {
				return !className.equals("java.lang.Runtime");
			}
		};
		// The reflection path of the report: Class.getMethods() -> forName -> Method.invoke()
		String viaReflection = "var R=Java.type('java.util.Random'); var ms=new R().getClass().getClass().getMethods(); var fn;"
				+ " for(var i=0;i<ms.length;i++){ if(ms[i].getName()=='forName' && ms[i].getParameterCount()==1) fn=ms[i]; }"
				+ " var O=Java.type('java.lang.Object'); var args=new O[1]; args[0]='java.lang.Runtime'; fn.invoke(null, args)";
		JSEnvironment env2 = javaEnv(denyRuntime);
		try {
			env2.evaluateScript(viaReflection);
			fail();
		} catch(JSException e) {
			assertTrue(e.getMessage(), e.getMessage().contains("cannot be loaded"));
		}
		assertEquals("java.util.Random", env2.evaluateScript("var R=Java.type('java.util.Random'); new R().getClass().getName()"));

		// No access manager: unchanged
		JSEnvironment open = javaEnv(null);
		assertEquals("java.lang.Runtime", open.evaluateScript(viaReflection+".getName()"));
	}

	// Module resolvers: a precompiled module loads, and a failing module is not "not found"
	public void testTranspiledModuleResolvers() {
		MapTargetFactory classes = new MapTargetFactory();
		JSMemoryModuleResolver modules = new JSMemoryModuleResolver()
				.put("calc", "export function add(a, b) { return a + b }")
				.put("bad", "throw new Error('boom from module')");
		modules.initTranspiler(JSTranspilerOptions.newBuilder().build(), JSEnvironment.class.getClassLoader(), classes);
		JSEnvironment env = JavaScriptEnvironment.newBuilder().addModuleResolver(modules).build();
		assertEquals(5, (Object)env.evaluateScript("import { add } from 'calc'; add(2, 3)"));
		try {
			env.evaluateScript("import 'bad'; 1");
			fail();
		} catch(JSException e) {
			assertTrue(e.getMessage(), e.getMessage().contains("boom from module"));
		}

		// The class compiled above, served by a precompiled-module resolver
		FactoryClassLoader loader = new FactoryClassLoader(JSEnvironment.class.getClassLoader(), classes);
		JSTranspiledModuleResolver precompiled = new JSTranspiledModuleResolver(null, loader, "js");
		JSEnvironment env2 = JavaScriptEnvironment.newBuilder().addModuleResolver(precompiled).build();
		assertEquals(7, (Object)env2.evaluateScript("import { add } from 'calc'; add(3, 4)"));
	}

	// ConcurrentWeakIdentitytMap: concurrent inserts that trigger resizes, with a
	// concurrent clear(), must not deadlock and must not lose entries
	public void testConcurrentWeakIdentityMap() throws Exception {
		int threads = 8;
		int perThread = 5000;
		ConcurrentWeakIdentitytMap<Object,Object> map = new ConcurrentWeakIdentitytMap<>();
		List<Object> keys = new ArrayList<>();
		for(int i=0; i<threads*perThread; i++) {
			keys.add(new Object());
		}
		runConcurrently(threads, t -> {
			for(int i=0; i<perThread; i++) {
				Object k = keys.get(t*perThread+i);
				map.put(k, k);
			}
		});
		assertEquals(threads*perThread, map.size());
		for(Object k: keys) {
			assertSame(k, map.get(k));
		}

		// Inserts racing with clear(): only checks for deadlocks
		ConcurrentWeakIdentitytMap<Object,Object> map2 = new ConcurrentWeakIdentitytMap<>();
		runConcurrently(threads, t -> {
			for(int i=0; i<perThread; i++) {
				Object k = keys.get(t*perThread+i);
				map2.put(k, k);
				if(t==0 && i%500==0) {
					map2.clear();
				}
			}
		});
	}

	private interface IndexedTask {
		void run(int index) throws Exception;
	}
	private static void runConcurrently(int threads, IndexedTask task) throws Exception {
		CountDownLatch start = new CountDownLatch(1);
		CountDownLatch done = new CountDownLatch(threads);
		AtomicReference<Throwable> error = new AtomicReference<>();
		for(int t=0; t<threads; t++) {
			int index = t;
			Thread th = new Thread(() -> {
				try {
					start.await();
					task.run(index);
				} catch(Throwable e) {
					error.compareAndSet(null, e);
				} finally {
					done.countDown();
				}
			});
			th.setDaemon(true);
			th.start();
		}
		start.countDown();
		assertTrue("Deadlock: threads did not finish", done.await(60, TimeUnit.SECONDS));
		if(error.get()!=null) {
			throw new AssertionError(error.get());
		}
	}
}
