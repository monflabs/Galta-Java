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

	public static class Interop {
		public static String obj(Object o) {
			return o==null ? "null" : o instanceof org.monflabs.galtajs.rt.builtins.Callable ? "function" : o.getClass().getSimpleName();
		}
		public static String list(List<?> l) {
			return "List";
		}
		public static String fn(List<?> l) {
			return "List";
		}
		public static String fn(Runnable r) {
			return "Runnable";
		}
		public static String task(Runnable r) {
			return "Runnable";
		}
		public static String task(java.util.concurrent.Callable<?> c) throws Exception {
			return "Callable " + c.call();
		}
		public static String str(String s) {
			return "String " + s;
		}
		public static String box(int i) {
			return "int";
		}
		public static String box(Integer i) {
			return "Integer " + i;
		}
		public static String ch(char c) {
			return "char";
		}
		public static String ch(Object o) {
			return "Object";
		}
		public static String type(java.lang.reflect.Type t) {
			return t.getTypeName();
		}
		public static int applyAsInt(java.util.function.ToIntFunction<String> f) {
			return f.applyAsInt("x");
		}
	}
	private static final String INTEROP = "var I=Java.type('" + Interop.class.getName() + "');";

	// Mixed int/double arguments: every overload of Math.max() used to be an ambiguity
	public void testJavaMixedNumericOverloads() {
		JSEnvironment env = javaEnv(new JavaLibrary());
		assertEquals(2.5, env.evaluateScript("Java.type('java.lang.Math').max(1, 2.5)"));
		assertEquals(2.5, env.evaluateScript("Java.type('java.lang.Math').max(2.5, 1)"));
		assertEquals(5.5, env.evaluateScript("Java.type('java.lang.Math').clamp(5.5, 1, 10)"));
		assertEquals(3, (Object) env.evaluateScript("Java.type('java.lang.Math').max(1, 3)"));
	}

	// A function is passed as is to a parameter it is an instance of, and only adapted
	// to a functional interface
	public void testJavaFunctionArguments() {
		JSEnvironment env = javaEnv(new JavaLibrary());
		// It used to fail: "ScriptFunction must match an interface"
		assertEquals("function", env.evaluateScript(INTEROP + "I.obj(function(){})"));
		assertEquals(Boolean.TRUE, env.evaluateScript("var f=()=>1; Java.type('java.util.Objects').requireNonNull(f)===f"));
		// List is not a functional interface: it used to get a proxy
		assertThrows(JSException.class, () -> env.evaluateScript(INTEROP + "I.list(function(){})"));
		assertEquals("Runnable", env.evaluateScript(INTEROP + "I.fn(function(){})"));
		// Runnable/Callable: the one returning a value wins, as a function always returns one
		assertEquals("Callable 7", env.evaluateScript(INTEROP + "I.task(function(){ return 7 })"));
		assertEquals(1, (Object) env.evaluateScript(
				"var ex=Java.type('java.util.concurrent.Executors').newSingleThreadExecutor();"
				+ "try { ex.submit(function(){ return 1 }).get() } finally { ex.shutdown() }"));
		// The result is converted to the primitive type the interface method returns
		assertEquals(1, (Object) env.evaluateScript(INTEROP + "I.applyAsInt(s => 1.5)"));
	}

	// undefined reaches Java as null
	public void testJavaUndefinedArgument() {
		JSEnvironment env = javaEnv(new JavaLibrary());
		assertEquals("String null", env.evaluateScript(INTEROP + "I.str(undefined)"));
		assertEquals("null", env.evaluateScript(INTEROP + "I.obj(undefined)"));
		assertEquals("Integer null", env.evaluateScript(INTEROP + "I.box(undefined)"));
		assertEquals("Integer null", env.evaluateScript(INTEROP + "(function(x){ return I.box(x) })()"));
	}

	// Overload ranking specific to the script values
	public void testJavaOverloadRanking() {
		JSEnvironment env = javaEnv(new JavaLibrary());
		// f(int) and f(Integer): the primitive one, whatever the reflection order
		assertEquals("int", env.evaluateScript(INTEROP + "I.box(1)"));
		// A one-character string goes where a longer one goes
		assertEquals("Object", env.evaluateScript(INTEROP + "I.ch('a')"));
		assertEquals("Object", env.evaluateScript(INTEROP + "I.ch('ab')"));
		// A Java class is passed as its Class to a parameter a Class is assignable to
		assertEquals(Interop.class.getName(), env.evaluateScript(INTEROP + "I.type(I)"));
		// A method wins over the property of the same name derived from a getter
		// (shutdown() over isShutdown()), which couldn't be called at all
		assertEquals(Boolean.TRUE, env.evaluateScript(
				"var ex=Java.type('java.util.concurrent.Executors').newSingleThreadExecutor(); ex.shutdown(); ex.isShutdown()"));
	}

	// Java.to() / Java.from(), and the array types they take
	public void testJavaToAndFrom() {
		JSEnvironment env = javaEnv(new JavaLibrary());
		String arrays = "var A=Java.type('java.util.Arrays');";
		assertEquals("[1, 2, 1, 0, 0]", env.evaluateScript(arrays + "A.toString(Java.to([1.9,'2',true,null,undefined],'int[]'))"));
		assertEquals("[1.0, NaN, 4.0]", env.evaluateScript(arrays + "A.toString(Java.to([1,,4],'double[]'))"));
		assertEquals("[1, a, null]", env.evaluateScript(arrays + "A.toString(Java.to([1,'a',undefined],'java.lang.String[]'))"));
		assertEquals("[a, B]", env.evaluateScript(arrays + "A.toString(Java.to(['a',66],'char[]'))"));
		assertEquals("[a, b, c]", env.evaluateScript(arrays + "A.toString(Java.to('abc','char[]'))"));
		assertEquals("[[1, 2], [3]]", env.evaluateScript(arrays + "A.deepToString(Java.to([[1,2],[3]],'int[][]'))"));
		assertEquals("[1, 1099511627776]", env.evaluateScript(arrays + "A.toString(Java.to([1, 2**40], Java.type('long[]')))"));
		assertEquals("[a, b]", env.evaluateScript(arrays + "A.toString(Java.to({length:2, 0:'a', 1:'b'},'java.lang.String[]'))"));
		// Object[] by default, elements as they are
		assertEquals("Object[]", env.evaluateScript("Java.to([1,'x']).$getClass().getSimpleName()"));
		// Collections
		assertEquals("java.util.ArrayList", env.evaluateScript("Java.to([1,2],'java.util.List').$getClass().getName()"));
		assertEquals("java.util.ArrayDeque", env.evaluateScript("Java.to([1,2],'java.util.Deque').getClass().getName()"));
		assertEquals(2, (Object) env.evaluateScript("Java.to([1,1,2],'java.util.Set').size"));
		// Functions to a functional interface, other values rejected
		assertEquals(Boolean.TRUE, env.evaluateScript("Java.to([()=>1],'java.lang.Runnable[]')[0] instanceof Java.type('java.lang.Runnable')"));
		assertThrows(JSException.class, () -> env.evaluateScript("Java.to([{a:1}],'java.lang.Runnable[]')"));
		assertThrows(JSException.class, () -> env.evaluateScript("Java.to([1],'java.util.Map')"));
		assertThrows(JSException.class, () -> env.evaluateScript("Java.to(5,'int[]')"));
		// Java.from()
		assertEquals("true,3,5", env.evaluateScript("var x=Java.from(Java.to([1,2,3],'int[]')); [Array.isArray(x), x.length, x[1]+x[2]].join()"));
		assertEquals("1,2", env.evaluateScript("var s=new (Java.type('java.util.TreeSet'))(); s.add(2); s.add(1); Java.from(s).join()"));
		assertThrows(JSException.class, () -> env.evaluateScript("Java.from({a:1})"));
		assertThrows(JSException.class, () -> env.evaluateScript("Java.from(null)"));
		// Array types: Java.type() and new
		assertEquals("3,int[]", env.evaluateScript("var a=new (Java.type('int[]'))(3); [a.length, a.$getClass().getSimpleName()].join()"));
		assertEquals("[[Ljava.lang.String;", env.evaluateScript("Java.to([], 'java.lang.String[][]').$getClass().getName()"));
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
