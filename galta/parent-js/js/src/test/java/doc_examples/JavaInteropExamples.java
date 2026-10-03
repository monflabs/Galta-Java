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

import java.util.List;
import java.util.function.Function;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.JSException;
import org.monflabs.galtajs.environments.GaltaJSEnvironment;
import org.monflabs.galtajs.environments.JavaScriptEnvironment;
import org.monflabs.galtajs.library.StaticLibrary;
import org.monflabs.galtajs.library.java.JavaLibrary;
import org.monflabs.galtajs.rt.builtins.standard.StandardLibrary;
import org.monflabs.tests.__BaseTestCase;
import org.monflabs.util.model.ClassMetadata;

import static doc_examples.DocExampleSupport.*;

/**
 * Samples for docs/GaltaJS/UserGuide/JavaInterop.md
 */
public class JavaInteropExamples extends __BaseTestCase {

	/** A plain Java bean used by the samples. */
	public static class Person {
		private String name;
		private int age;
		public Person() {}
		public Person(String name, int age) { this.name = name; this.age = age; }
		public String getName() { return name; }
		public void setName(String name) { this.name = name; }
		public int getAge() { return age; }
		public String greet(String who) { return "Hi " + who + ", I am " + name; }
		public static Person of(String name) { return new Person(name, 0); }
		public void run(Runnable r) { r.run(); }
		public String map(Function<String,String> f) { return f.apply(name); }
	}

	public void testLoadClassAndConstruct() {
		JSEnvironment env = GaltaJSEnvironment.create();
		Object r = env.evaluateScript("""
			const Person = Java.type('doc_examples.JavaInteropExamples$Person');
			const p = new Person('Ann', 30);
			[p.getName(), p.getAge(), p.greet('Bob'), typeof p, p instanceof Person]
			""");
		assertEquals(List.of("Ann", 30, "Hi Bob, I am Ann", "object", true), list(r));
	}

	public void testBeanPropertiesAndOverloads() {
		JSEnvironment env = GaltaJSEnvironment.create();
		Object r = env.evaluateScript("""
			const Person = Java.type('doc_examples.JavaInteropExamples$Person');
			const p = new Person('Ann', 30);
			p.name = 'Anne';                 // calls setName()
			const Math = Java.type('java.lang.Math');
			[p.name, p.age, Math.abs(-3), Math.abs(-3.5), Math.max(1, 2)]
			""");
		assertEquals(List.of("Anne", 30, 3, 3.5, 2), list(r));
	}

	public void testStaticMembers() {
		JSEnvironment env = GaltaJSEnvironment.create();
		Object r = env.evaluateScript("""
			const Integer = Java.type('java.lang.Integer');
			const Person = Java.type('doc_examples.JavaInteropExamples$Person');
			[Integer.MAX_VALUE, Integer.parseInt('12'), Person.of('Zed').name]
			""");
		assertEquals(List.of(2147483647, 12, "Zed"), list(r));
	}

	public void testFunctionsAsFunctionalInterfaces() {
		JSEnvironment env = GaltaJSEnvironment.create();
		Object r = env.evaluateScript("""
			const Person = Java.type('doc_examples.JavaInteropExamples$Person');
			const p = new Person('Ann', 30);
			let ran = false;
			p.run(() => { ran = true });                  // JS function -> java.lang.Runnable
			[ran, p.map(s => s.toUpperCase() + '!')]      // JS function -> java.util.function.Function
			""");
		assertEquals(List.of(true, "ANN!"), list(r));
	}

	public void testJavaCollections() {
		JSEnvironment env = GaltaJSEnvironment.create();
		// java.util.List behaves like an Array, java.util.Map like a Map
		Object r = env.evaluateScript("""
			const ArrayList = Java.type('java.util.ArrayList');
			const list = new ArrayList();
			list.push('a', 'b');
			const HashMap = Java.type('java.util.HashMap');
			const map = new HashMap();
			map.set('k', 1);
			[list.length, list[1], list.map(s => s.toUpperCase()).join(','), list instanceof ArrayList,
			 map.get('k'), map.size, map.has('k'), map instanceof Map]
			""");
		assertEquals(List.of(2L, "b", "A,B", true, 1, 1, true, true), list(r));
	}

	public void testJavaArrays() {
		JSEnvironment env = GaltaJSEnvironment.create();
		Object r = env.evaluateScript("""
			const int = Java.type('int');
			const String = Java.type('java.lang.String');
			const ints = new int[3];              // Java-style array creation
			ints[1] = 5;
			const strings = new String[2];
			strings[0] = 'x'; strings[1] = 'y';
			[ints.length, ints[1], ints[2], String.join('-', strings)]
			""");
		assertEquals(List.of(3, 5, 0, "x-y"), list(r));
	}

	public void testJavaToAndFrom() {
		JSEnvironment env = GaltaJSEnvironment.create();
		Object r = env.evaluateScript("""
			const Arrays = Java.type('java.util.Arrays');
			const ints = Java.to([1, 2.7, '3'], 'int[]');     // JS -> int[], with ToNumber
			const names = Java.to(['a', 'b'], Java.type('java.lang.String[]'));
			const back = Java.from(ints);                      // Java array -> JS array
			[Arrays.toString(ints), names.length, Array.isArray(back), back.map(n => n * 10).join(),
			 Java.from(Java.type('java.util.List').of('x', 'y')).join('-'), new (Java.type('long[]'))(2).length]
			""");
		assertEquals(List.of("[1, 2, 3]", 2, true, "10,20,30", "x-y", 2), list(r));
	}

	public void testDollarPrefixReachesTheJavaObject() {
		JSEnvironment env = GaltaJSEnvironment.create();
		// A JavaScript value is a Java object; "$" gives access to its Java members
		Object r = env.evaluateScript("""
			const list = Java.type('java.util.ArrayList');
			const l = new list(); l.push(1);
			[(42).$getClass().getSimpleName(), 'abc'.$getClass().getName(), l.$size(), l.$isEmpty(), [1,2].$getClass().getSimpleName()]
			""");
		assertEquals(List.of("Integer", "java.lang.String", 1, false, "BuiltinArray"), list(r));
	}

	public void testJavaObjectsAsGlobals() {
		StaticLibrary globals = new StaticLibrary();
		globals.addStaticGlobal("owner", new Person("Global", 1));
		globals.addStaticGlobal("PersonClass", Person.class);
		JSEnvironment env = GaltaJSEnvironment.newBuilder().registerLibrary(globals).build();
		assertEquals("Hi x, I am Global", env.evaluateExpression("owner.greet('x')"));
		// A java.lang.Class global is the Class object, not a constructor: use Java.type() for `new`
		assertEquals("doc_examples.JavaInteropExamples$Person", (Object)env.evaluateExpression("PersonClass.getName()"));
	}

	public void testRestrictingClassAccess() {
		JavaLibrary restricted = new JavaLibrary(new ClassMetadata.AccessManager() {
			@Override
			public boolean canLoadClass(String className) {
				return !className.startsWith("java.io.");
			}
		});
		JSEnvironment env = JSEnvironment.newBuilder()
				.enableGaltaJSExtensions()
				.registerLibrary(new StandardLibrary())
				.registerLibrary(restricted)
				.build();
		assertEquals(3, (Object)env.evaluateExpression("Java.type('java.lang.Math').abs(-3)"));
		try {
			env.evaluateExpression("Java.type('java.io.File')");
			fail();
		} catch(JSException e) {
			assertTrue(e.getMessage().contains("cannot be loaded"));
		}
	}

	public void testJavaExceptionsInJavaScript() {
		JSEnvironment env = GaltaJSEnvironment.create();
		Object r = env.evaluateScript("""
			const Integer = Java.type('java.lang.Integer');
			let r;
			try { Integer.parseInt('abc') }
			catch(e) { r = [e instanceof Error, e.message, e.__java_exception__.getCause().getClass().getSimpleName()] }
			r
			""");
		// __java_exception__ is the reflection wrapper (ModelException); its cause is the original exception
		assertEquals(List.of(true, "Java Exception: NumberFormatException: For input string: \"abc\"", "NumberFormatException"), list(r));
	}

	public void testJavaLibraryIsOptional() {
		JSEnvironment env = JavaScriptEnvironment.create();
		assertEquals("undefined", (Object)env.evaluateExpression("typeof Java"));
	}
}
