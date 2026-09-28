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

import java.util.Date;
import java.util.List;
import java.util.Map;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.environments.JavaScriptEnvironment;
import org.monflabs.galtajs.library.GlobalLibrary;
import org.monflabs.galtajs.library.StaticLibrary;
import org.monflabs.galtajs.rt.builtins.AccessorFactory;
import org.monflabs.galtajs.rt.builtins.BaseMethod;
import org.monflabs.galtajs.rt.builtins.JSAccessor;
import org.monflabs.galtajs.rt.builtins.PropertyDescriptor;
import org.monflabs.tests.__BaseTestCase;

import static doc_examples.DocExampleSupport.*;

/**
 * Samples for docs/GaltaJS/Extending/Accessors.md
 */
public class AccessorsExamples extends __BaseTestCase {

	/** A Java value that describes its own JavaScript behaviour. */
	public static class Point implements AccessorFactory {
		public final int x, y;
		public Point(int x, int y) { this.x = x; this.y = y; }

		@Override
		public JSAccessor createAccessor(JSEnvironment env) {
			return new PointAccessor(env);
		}
	}

	/** Read-only x/y properties plus a toString() method; everything else comes from Object.prototype. */
	public static class PointAccessor extends JSAccessor {
		private final Object objectPrototype;
		private final BaseMethod toString;

		public PointAccessor(JSEnvironment env) {
			super(env);
			Object objectCtor = env.getStandardObjects().getConstructor("Object");
			this.objectPrototype = env.getAccessor(objectCtor).getProperty(objectCtor, "prototype", null);
			this.toString = new BaseMethod(env, "toString", 0) {
				@Override
				public Object call(Object thisValue, Object[] args) {
					Point p = (Point)thisValue;
					return "Point(" + p.x + "," + p.y + ")";
				}
			};
		}
		@Override
		public String getClassName(Object thisValue) {
			return "Point";
		}
		@Override
		public Object getPrototype(Object thisValue) {
			return objectPrototype;
		}
		@Override
		public Object getOwnProperty(Object thisValue, String member, Object defaultValue, Object receiver) {
			Point p = (Point)thisValue;
			return switch(member) {
				case "x" -> p.x;
				case "y" -> p.y;
				case "toString" -> toString;
				default -> defaultValue;
			};
		}
	}

	public void testAccessorFactory() {
		StaticLibrary globals = new StaticLibrary();
		globals.addStaticGlobal("pt", new Point(3, 4));
		JSEnvironment env = JavaScriptEnvironment.newBuilder().registerLibrary(globals).build();

		assertEquals(12, (Object)env.evaluateExpression("pt.x * pt.y"));
		assertEquals("object", (Object)env.evaluateExpression("typeof pt"));
		assertEquals("Point(3,4)", env.evaluateExpression("`${pt}`"));
		assertEquals(true, (Object)env.evaluateExpression("Object.prototype.hasOwnProperty.call(pt, 'x')"));
		assertSame(org.monflabs.galtajs.rt.RuntimeUtil.UNDEFINED, env.evaluateExpression("pt.z"));
	}

	/** A class that cannot be modified: the accessor is supplied by a library instead. */
	public static final class Temperature {
		final double celsius;
		public Temperature(double celsius) { this.celsius = celsius; }
	}

	public void testAccessorProvidedByALibrary() {
		GlobalLibrary lib = new GlobalLibrary() {
			@Override
			public JSAccessor createAccessor(JSEnvironment env, Class<?> clazz) {
				if(clazz != Temperature.class) {
					return null;   // not ours, let the next library answer
				}
				return new JSAccessor(env) {
					@Override public String getClassName(Object t) { return "Temperature"; }
					@Override public Object getOwnProperty(Object t, String member, Object def, Object receiver) {
						Temperature temp = (Temperature)t;
						return switch(member) {
							case "celsius" -> temp.celsius;
							case "fahrenheit" -> temp.celsius * 9 / 5 + 32;
							default -> def;
						};
					}
				};
			}
		};
		StaticLibrary globals = new StaticLibrary();
		globals.addStaticGlobal("t", new Temperature(100));
		JSEnvironment env = JavaScriptEnvironment.newBuilder().registerLibrary(lib).registerLibrary(globals).build();
		assertEquals(212.0, (Object)env.evaluateExpression("t.fahrenheit"));
		assertEquals(100.0, (Object)env.evaluateExpression("t.celsius"));
	}

	public void testWithoutAnAccessorTheValueIsOpaque() {
		StaticLibrary globals = new StaticLibrary();
		globals.addStaticGlobal("t", new Temperature(1));
		JSEnvironment env = JavaScriptEnvironment.newBuilder().registerLibrary(globals).build();
		try {
			env.evaluateExpression("t.celsius");
			fail();
		} catch(org.monflabs.galtajs.JSException e) {
			assertTrue(e.getMessage().contains("Unknown object type"));
		}
	}

	public void testBuiltInJavaTypeMappings() {
		JSEnvironment env = JavaScriptEnvironment.create();
		StaticLibrary globals = new StaticLibrary();
		globals.addStaticGlobal("when", new Date(0));
		globals.addStaticGlobal("names", List.of("a", "b"));
		globals.addStaticGlobal("scores", Map.of("ada", 10));
		JSEnvironment env2 = JavaScriptEnvironment.newBuilder().registerLibrary(globals).build();

		assertEquals(true, (Object)env2.evaluateExpression("when instanceof Date"));
		assertEquals(1970.0, (Object)env2.evaluateExpression("when.getUTCFullYear()"));
		assertEquals("A,B", env2.evaluateExpression("names.map(s => s.toUpperCase()).join(',')"));
		assertEquals(10, (Object)env2.evaluateExpression("scores.get('ada')"));
		assertEquals(true, (Object)env2.evaluateExpression("scores instanceof Map"));
		assertNotNull(env);
	}

	public void testPropertyDescriptorsAreAvailable() {
		JSEnvironment env = JavaScriptEnvironment.create();
		Object obj = env.evaluateExpression("Object.freeze({a: 1})");
		JSAccessor acc = env.getAccessor(obj);
		assertTrue(acc.isFrozen(obj));
		PropertyDescriptor desc = acc.getOwnPropertyDescriptor(obj, "a");
		assertFalse(desc.isWritable());
		assertEquals(1, acc.getProperty(obj, "a", null));
	}
}
