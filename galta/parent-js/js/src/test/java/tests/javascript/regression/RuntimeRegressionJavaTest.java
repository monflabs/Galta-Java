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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.environments.JavaScriptEnvironment;
import org.monflabs.galtajs.jsonfactory.JSArray;
import org.monflabs.galtajs.jsonfactory.JSArrayImpl;
import org.monflabs.galtajs.jsonfactory.JSObject;
import org.monflabs.galtajs.jsonfactory.JSObjectImpl;
import org.monflabs.galtajs.library.platform.HostLibrary;
import org.monflabs.galtajs.rt.JSRuntimeException;
import org.monflabs.galtajs.rt.JSRuntimeUncatchableException;
import org.monflabs.galtajs.rt.JSScriptExecutor;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.builtins.errors.Error;
import org.monflabs.galtajs.rt.interpreter.InterpretedGlobalRuntimeContext;
import org.monflabs.tests.__BaseTestCase;

/**
 * Runtime regressions (environment, executor, collections) checked through
 * the Java API.
 */
public class RuntimeRegressionJavaTest extends __BaseTestCase {

	// An environment is equal to itself (equals() used to delegate to the configuration)
	public void testEnvironmentEquality() {
		JSEnvironment env = JavaScriptEnvironment.create();
		assertTrue(env.equals(env));
		assertFalse(env.equals(JavaScriptEnvironment.create()));
		assertEquals(System.identityHashCode(env), env.hashCode());
	}

	// getProperty(key,def) returns the default for a missing key
	public void testEnvironmentPropertyDefault() {
		JSEnvironment env = JavaScriptEnvironment.newBuilder().putProperty("a", "x").build();
		assertEquals("x", env.getProperty("a", "d"));
		assertEquals("d", env.getProperty("missing", "d"));
		assertEquals(7, env.getPropertyInt("missing", 7));
	}

	// The program cache is keyed by the compile flags too: the same text
	// compiled as a script and as eval code gives two different programs
	public void testScriptCacheKeyedByFlags() {
		JSEnvironment env = JavaScriptEnvironment.newBuilder().scriptCacheSize(10).evalCacheSize(10).build();
		assertFalse(env.createScript("1+1", "s").getProgram().isEval());
		assertTrue(env.createEvalScript("1+1", "e").getProgram().isEval());
		assertFalse(env.createScript("1+1", "s").getProgram().isEval());
		assertTrue(env.createEvalScript("1+1", "e").getProgram().isEval());
		// Both are cached
		assertSame(env.createScript("2+2", "s").getProgram(), env.createScript("2+2", "s").getProgram());
		assertSame(env.createEvalScript("2+2", "e").getProgram(), env.createEvalScript("2+2", "e").getProgram());
	}

	// An error created from a Java exception keeps that exception, not itself
	public void testErrorJavaCause() {
		JSEnvironment env = JavaScriptEnvironment.create();
		IllegalStateException cause = new IllegalStateException("boom");
		for(String type: new String[] {"error","evalError","rangeError","referenceError","syntaxError","typeError","uriError","aggregateError"}) {
			JSRuntimeException ex = env.getRealmContext().with(() -> switch(type) {
				case "error" -> RuntimeUtil.error(cause, "m {0}", 1);
				case "evalError" -> RuntimeUtil.evalError(cause, "m {0}", 1);
				case "rangeError" -> RuntimeUtil.rangeError(cause, "m {0}", 1);
				case "referenceError" -> RuntimeUtil.referenceError(cause, "m {0}", 1);
				case "syntaxError" -> RuntimeUtil.syntaxError(cause, "m {0}", 1);
				case "typeError" -> RuntimeUtil.typeError(cause, "m {0}", 1);
				case "uriError" -> RuntimeUtil.uriError(cause, "m {0}", 1);
				default -> RuntimeUtil.aggregateError(cause, "m {0}", 1);
			});
			JSObject jsError = (JSObject)ex.getJavascriptException();
			assertSame(type, cause, jsError.getProperty(Error.JAVA_EXCEPTION));
			assertEquals(type, "m 1", jsError.getProperty("message"));
		}
	}

	// A filter that throws restores the previous filter context
	public void testFilterContextRestored() {
		JSEnvironment env = JavaScriptEnvironment.create();
		InterpretedGlobalRuntimeContext ctx = env.getRealmContext();
		Object before = ctx.getFilterContext();
		try {
			ctx.executeWithFilterContext("item", () -> { throw new IllegalStateException(); });
			fail();
		} catch(IllegalStateException e) {
			// expected
		}
		assertSame(before, ctx.getFilterContext());
	}

	// Path search: the setter writes the new value, the remover removes the array item
	public void testFindAccessors() {
		JSEnvironment env = JavaScriptEnvironment.create();
		env.getRealmContext().run(() -> {
			JSObject o = JSObject.create(env);
			o.setOwnProperty("k", "old");
			RuntimeUtil._find(env, (base, index, getter, setter, remover) -> setter.accept("new"), o, null, true);
			assertEquals("new", o.getProperty("k"));

			JSArray a = JSArray.create(env);
			a.arrayAdd("a"); a.arrayAdd("b"); a.arrayAdd("c");
			RuntimeUtil._find(env, (base, index, getter, setter, remover) -> {
				if(base==a) {
					assertTrue(remover.getAsBoolean());
				}
			}, a, 1, true);
			assertEquals(2L, a.arrayLength());
			assertEquals("c", a.arrayGet(1, null));
		});
	}

	// A thread interrupted while the event loop waits gets an interrupt
	// exception, not a successful (undefined) completion
	public void testInterruptedEventLoop() throws Exception {
		JSEnvironment env = JavaScriptEnvironment.newBuilder().registerLibrary(new HostLibrary()).build();
		AtomicReference<Object> outcome = new AtomicReference<>();
		Thread t = new Thread(() -> {
			try {
				outcome.set(new JSScriptExecutor(env).execute("setTimeout(() => {}, 60000); 1"));
			} catch(Throwable ex) {
				outcome.set(ex);
			}
		});
		long start = System.nanoTime();
		t.start();
		Thread.sleep(300);
		t.interrupt();
		t.join(20000);
		assertFalse(t.isAlive());
		assertTrue(String.valueOf(outcome.get()), outcome.get() instanceof JSRuntimeUncatchableException);
		assertTrue((System.nanoTime()-start)/1_000_000 < 20000);
	}

	// Java views of an object's properties
	public void testObjectViews() {
		JSEnvironment env = JavaScriptEnvironment.create();
		env.getRealmContext().run(() -> {
			JSObjectImpl o = JSObject.create(env);
			Collection<Object> values = o.values();
			assertTrue(values.isEmpty());
			o.setOwnProperty("a", 1);
			o.setOwnProperty("b", 2);
			assertFalse(values.isEmpty());

			assertTrue(o.entrySet().contains(Map.entry("a", 1)));
			assertFalse(o.entrySet().contains(Map.entry("a", 2)));
			assertFalse(o.entrySet().remove(Map.entry("a", 2)));
			assertTrue(o.entrySet().remove(Map.entry("a", 1)));
			assertFalse(o.containsKey("a"));

			o.setOwnProperty("c", 3);
			String[] keys = o.keySet().toArray(new String[0]);
			assertEquals(List.of("b","c"), List.of(keys));
			Object[] big = o.keySet().toArray(new Object[5]);
			assertEquals("b", big[0]);
			assertNull(big[2]);
		});
	}

	// Map-like iteration: abandoned or concurrent iterators leave no
	// removed entries behind, and see the entries added after a removal
	public void testIteratorsAndRemovals() {
		JSEnvironment env = JavaScriptEnvironment.create();
		env.getRealmContext().run(() -> {
			JSObjectImpl o = JSObject.create(env);
			for(int i=0; i<5; i++) {
				o.setOwnProperty("k"+i, i);
			}
			Iterator<String> abandoned = o.keySet().iterator();
			abandoned.next();
			Iterator<String> live = o.keySet().iterator();
			assertEquals("k0", live.next());
			assertEquals("k1", live.next());
			o.remove("k1");
			o.remove("k2");
			o.remove("k4");
			o.setOwnProperty("k5", 5);
			List<String> rest = new ArrayList<>();
			live.forEachRemaining(rest::add);
			assertEquals(List.of("k3","k5"), rest);
			assertEquals(List.of("k0","k3","k5"), new ArrayList<>(o.keySet()));
		});
	}

	// WeakIdentityMap iteration: every entry once, an empty map iterates
	public void testWeakIdentityMapIteration() {
		org.monflabs.galtajs.rt.util.WeakIdentityMap<Object,Object> m = new org.monflabs.galtajs.rt.util.WeakIdentityMap<>();
		assertFalse(m.keySet().iterator().hasNext());
		List<Object> keys = new ArrayList<>();
		for(int i=0; i<100; i++) {
			Object k = new Object();
			keys.add(k);
			m.put(k, i);
		}
		int count = 0;
		java.util.Set<Object> seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
		for(Object k: m.keySet()) {
			assertTrue(seen.add(k));
			count++;
		}
		assertEquals(100, count);
		Iterator<Object> it = m.values().iterator();
		for(int i=0; i<100; i++) {
			it.next(); // next() without hasNext()
		}
		assertFalse(it.hasNext());
	}

	// ConcurrentWeakIdentitytMap grows by doubling and stays consistent
	public void testConcurrentWeakIdentityMapGrowth() {
		org.monflabs.galtajs.rt.util.ConcurrentWeakIdentitytMap<Object,Object> m = new org.monflabs.galtajs.rt.util.ConcurrentWeakIdentitytMap<>();
		List<Object> keys = new ArrayList<>();
		for(int i=0; i<1000; i++) {
			Object k = new Object();
			keys.add(k);
			m.put(k, i);
			assertNull(m.get(new Object())); // misses trigger purges
		}
		assertEquals(1000, m.size());
		for(int i=0; i<1000; i++) {
			assertEquals(i, m.get(keys.get(i)));
		}
	}

	// The java.util.List view of an array with holes: JS indexes, holes read as undefined
	public void testArrayListView() {
		JSEnvironment env = JavaScriptEnvironment.create();
		env.getRealmContext().run(() -> {
			JSArrayImpl a = (JSArrayImpl)JSArray.create(env);
			a.arraySet(2, "x", org.monflabs.galtajs.jsonfactory.JSObject.DESC_CHECK.NONE);
			a.arraySet(4, "y", org.monflabs.galtajs.jsonfactory.JSObject.DESC_CHECK.NONE);
			a.arrayDelete(3, org.monflabs.galtajs.jsonfactory.JSObject.DESC_CHECK.NONE);
			assertEquals(5, a.size());
			List<Object> items = new ArrayList<>();
			a.iterator().forEachRemaining(items::add);
			assertEquals(List.of(RuntimeUtil.UNDEFINED, RuntimeUtil.UNDEFINED, "x", RuntimeUtil.UNDEFINED, "y"), items);
			List<Object> each = new ArrayList<>();
			a.forEach(each::add);
			assertEquals(items, each);
			assertEquals(2, a.indexOf("x"));
			assertEquals(4, a.lastIndexOf("y"));
			assertTrue(a.contains("y"));
			assertEquals(List.of("x", RuntimeUtil.UNDEFINED), a.subList(2, 4));
			assertEquals(5L, a.stream().count());
			assertEquals(RuntimeUtil.UNDEFINED, a.set(0, (Object)"z"));
			assertEquals("z", a.get(0));
			assertEquals(RuntimeUtil.UNDEFINED, a.remove(3));
			assertEquals(4, a.size());
			assertEquals("y", a.get(3));
		});
	}

	// A cloned array shares neither its non-index members nor its index accessors
	public void testArrayCloneIsDeep() {
		JSEnvironment env = JavaScriptEnvironment.create();
		env.getRealmContext().run(() -> {
			JSArrayImpl a = (JSArrayImpl)JSArray.create(env);
			a.arrayAdd(1);
			a.setOwnProperty("tag", "x");
			JSArrayImpl c = a.clone();
			c.setOwnProperty("tag", "y");
			c.setOwnProperty("other", 1);
			assertEquals("x", a.getProperty("tag"));
			assertFalse(a.hasProperty("other"));
			assertEquals("y", c.getProperty("tag"));
		});
	}
}
