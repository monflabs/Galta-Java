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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.JSException;
import org.monflabs.galtajs.JSModuleDescriptor;
import org.monflabs.galtajs.environments.JavaScriptEnvironment;
import org.monflabs.galtajs.library.java.JavaLibrary;
import org.monflabs.galtajs.library.platform.HostLibrary;
import org.monflabs.galtajs.modules.JSPathModuleResolver;
import org.monflabs.galtajs.rt.builtins.Callable;
import org.monflabs.galtajs.rt.builtins.standard.promise.BuiltinPromise;
import org.monflabs.tests.__BaseTestCase;

/**
 * Regressions in the libraries and module resolution around the engine:
 * Java interop, timers, file-system modules.
 */
public class LibraryRegressionJavaTest extends __BaseTestCase {

	private static JSEnvironment javaEnv(JavaLibrary lib) {
		return JavaScriptEnvironment.newBuilder().registerLibrary(lib).build();
	}

	// JavaClass(value) is a Java cast: it returns the value, converted for numbers
	public void testJavaClassCast() {
		JSEnvironment env = javaEnv(new JavaLibrary());
		assertEquals("abc", env.evaluateScript("Java.type('java.lang.String')('abc')"));
		assertEquals(3, (Object) env.evaluateScript("Java.type('java.lang.Integer')(3.7)"));
		assertEquals(Boolean.TRUE, env.evaluateScript("Java.type('java.lang.String')(null) === null"));
		assertThrows(JSException.class, () -> env.evaluateScript("Java.type('java.lang.Integer')('abc')"));
		assertThrows(JSException.class, () -> env.evaluateScript("Java.type('java.util.List')('abc')"));
	}

	// A Java proxy answers Object's methods itself instead of calling the
	// function (called outside a script, the function would even fail: there
	// is no running context - which is how hashCode() used to break HashSet)
	public void testProxyObjectMethods() {
		JavaLibrary lib = new JavaLibrary();
		JSEnvironment env = javaEnv(lib);
		Object fn = env.evaluateScript("(function(){ throw new Error('must not be called'); })");
		Runnable r = (Runnable) lib.getProxy((Callable) fn, Runnable.class);
		Set<Object> set = new HashSet<>();
		set.add(r);
		assertTrue(set.contains(r));
		assertTrue(r.equals(r));
		assertFalse(r.equals(new Object()));
		assertTrue(r.toString().contains("@"));
	}

	// The keys of a Java class are its own members, not those of the JS wrapper
	@SuppressWarnings("unchecked")
	public void testJavaClassKeys() {
		JSEnvironment env = javaEnv(new JavaLibrary());
		List<Object> keys = (List<Object>) env.evaluateScript("Object.keys(Java.type('java.lang.Integer'))");
		assertTrue(keys.toString(), keys.contains("parseInt"));
		assertFalse(keys.toString(), keys.contains("getNativeClass"));
	}

	// The setUse* options narrow what scripts see of Java objects
	public void testUseOptions() {
		JavaLibrary noMethods = new JavaLibrary();
		noMethods.setUseMethods(false);
		assertEquals("undefined", javaEnv(noMethods).evaluateScript("typeof Java.type('java.lang.Math').abs"));
		JavaLibrary noConstructors = new JavaLibrary();
		noConstructors.setUseConstructors(false);
		assertThrows(JSException.class, () -> javaEnv(noConstructors).evaluateScript("new (Java.type('java.lang.StringBuilder'))()"));
		assertEquals("function", javaEnv(new JavaLibrary()).evaluateScript("typeof Java.type('java.lang.Math').abs"));
	}

	// setInterval without a delay, or with 0, repeats until cleared
	public void testIntervalWithoutDelayRepeats() {
		JSEnvironment env = JavaScriptEnvironment.newBuilder().registerLibrary(new HostLibrary()).build();
		Object result = env.evaluateScript("""
				var n = 0, m = 0;
				var a = setInterval(function(){ if(++n == 3) clearInterval(a); });
				var b = setInterval(function(){ if(++m == 2) clearInterval(b); }, 0);
				new Promise(function(resolve){ setTimeout(function(){ resolve(n + ',' + m); }, 100); })
				""");
		// the script's value is the promise; the timers have run by the time evaluateScript() returns
		assertEquals("FULFILLED", String.valueOf(((BuiltinPromise) result).getState()));
		assertEquals("3,2", ((BuiltinPromise) result).getResult());
	}

	// File-system modules stay inside the root, and a directory is not a module
	public void testPathModuleResolver() throws Exception {
		Path dir = Files.createTempDirectory("galtajs-modules");
		Path root = Files.createDirectory(dir.resolve("root"));
		Files.writeString(dir.resolve("outside.js"), "export const x = 1;");
		Files.writeString(root.resolve("inside.js"), "export const y = 2;");
		Files.createDirectory(root.resolve("lib"));
		Files.writeString(root.resolve("lib.js"), "export const z = 3;");
		JSPathModuleResolver resolver = new JSPathModuleResolver(root);
		assertNull(resolver.getModule("../outside.js"));
		assertNull(resolver.getModule("../outside"));
		assertNotNull(resolver.getModule("./inside.js"));
		JSModuleDescriptor lib = resolver.getModule("./lib");
		assertNotNull("falls back to lib.js next to the lib directory", lib);
		assertEquals("export const z = 3;", lib.getScript());
		assertEquals(2, resolver.getModules().count());
	}
}
