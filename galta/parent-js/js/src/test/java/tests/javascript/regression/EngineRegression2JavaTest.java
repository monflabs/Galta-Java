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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.environments.JavaScriptEnvironment;
import org.monflabs.galtajs.jsonfactory.JSObject.DESC_CHECK;
import org.monflabs.galtajs.preprocessor.ScriptPreProcessor;
import org.monflabs.galtajs.rt.builtins.primitives.array.arraylike.JSArrayJavaArray;
import org.monflabs.galtajs.rt.builtins.primitives.array.arraylike.JSArrayList;
import org.monflabs.galtajs.rt.builtins.primitives.symbol.Symbol;
import org.monflabs.galtajs.rt.util.WeakIdentityMap;
import org.monflabs.galtajs.transpiler.JSTranspiler;
import org.monflabs.galtajs.transpiler.JSTranspilerException;
import org.monflabs.galtajs.transpiler.JSTranspilerOptions;
import org.monflabs.galtajs.transpiler.path.PathTranspiler;
import org.monflabs.galtajs.util.SparseList;
import org.monflabs.tests.__BaseTestCase;

/**
 * Engine regressions (second audit round) checked through the Java API.
 */
public class EngineRegression2JavaTest extends __BaseTestCase {

	// Wrapped Java arrays: every index is writable, sort uses the comparator
	public void testJavaArrayWrapper() {
		JSEnvironment env = JavaScriptEnvironment.create();
		int[] ints = {3,1,2};
		JSArrayJavaArray a = JSArrayJavaArray.of(env, ints);
		assertTrue(a.arraySet(2, 9, DESC_CHECK.NONE));
		assertEquals(9, ints[2]);
		assertFalse(a.arraySet(3, 9, DESC_CHECK.NONE));
		a.arraySort((x,y) -> Integer.compare((Integer)y,(Integer)x), DESC_CHECK.NONE);
		assertEquals("[9, 3, 1]", Arrays.toString(ints));
		Object[] objs = {"b", null, "a"};
		JSArrayJavaArray o = JSArrayJavaArray.of(env, objs);
		o.arraySort((x,y) -> x==null ? 1 : y==null ? -1 : ((String)x).compareTo((String)y), DESC_CHECK.NONE);
		assertEquals("[a, b, null]", Arrays.toString(objs));
		assertTrue(o.arrayDelete(1, DESC_CHECK.NONE));
		assertFalse(a.arrayDelete(1, DESC_CHECK.NONE)); // a primitive slot cannot hold undefined
	}

	// Wrapped java.util.List: delete/remove past the end is a no-op, not an exception
	public void testJavaListWrapper() {
		JSEnvironment env = JavaScriptEnvironment.create();
		List<Object> list = new ArrayList<>(List.of("a","b"));
		JSArrayList l = JSArrayList.of(env, list);
		assertFalse(l.arrayDelete(5, DESC_CHECK.NONE));
		assertFalse(l.arrayRemove(5, DESC_CHECK.NONE));
		assertTrue(l.arrayRemove(0, DESC_CHECK.NONE));
		assertEquals(List.of("b"), list);
	}

	// SparseList: iterator set/remove act on the element last returned
	public void testSparseListIterator() {
		SparseList<Object> s = new SparseList<>();
		s.add("a"); s.add("b"); s.add("c");
		ListIterator<Object> it = s.listIterator();
		assertEquals("a", it.next());
		it.set("A");
		assertEquals("b", it.next());
		it.remove();
		assertEquals("c", it.next());
		assertFalse(it.hasNext());
		assertEquals("[A, c]", Arrays.toString(s.toArray()));
		it.remove();
		assertThrows(IllegalStateException.class, it::remove); // no second remove
		assertEquals("[A]", Arrays.toString(s.toArray()));
		// toArray after removals: only live slots
		SparseList<Object> t = new SparseList<>();
		for(int i=0;i<10;i++) t.add(i);
		for(int i=0;i<8;i++) t.remove(0);
		assertEquals("[8, 9]", Arrays.toString(t.toArray()));
	}

	// WeakIdentityMap: views and an empty map
	public void testWeakIdentityMapViews() {
		WeakIdentityMap<Object,Object> m = new WeakIdentityMap<>();
		Object k = new Object();
		assertNull(m.remove(k)); // no NPE before the first put
		m.put(k, "v");
		assertFalse(m.values().isEmpty());
		assertTrue(m.keySet().contains(k));
		assertTrue(m.entrySet().contains(Map.entry(k, "v")));
		assertFalse(m.entrySet().contains(Map.entry(k, "w")));
		m.clear();
		assertTrue(m.values().isEmpty());
		assertFalse(m.keySet().contains(k));
	}

	// Symbol.for: thread-safe, no cap, one symbol per key
	public void testSymbolRegistryConcurrent() throws Exception {
		ExecutorService pool = Executors.newFixedThreadPool(8);
		Map<Integer,Symbol> seen = new java.util.concurrent.ConcurrentHashMap<>();
		List<Throwable> errors = new java.util.concurrent.CopyOnWriteArrayList<>();
		CountDownLatch done = new CountDownLatch(8);
		for(int t=0;t<8;t++) {
			pool.submit(() -> {
				try {
					for(int i=0;i<3000;i++) {
						Symbol s = Symbol.for_("concurrent-"+i);
						Symbol prev = seen.putIfAbsent(i, s);
						if(prev!=null && prev!=s) {
							errors.add(new AssertionError("two symbols for key "+i));
						}
					}
				} catch(Throwable e) {
					errors.add(e);
				} finally {
					done.countDown();
				}
			});
		}
		assertTrue(done.await(60, TimeUnit.SECONDS));
		pool.shutdown();
		assertTrue(errors.toString(), errors.isEmpty());
		assertTrue(Symbol.for_("concurrent-1").isRegistered());
	}

	// Preprocessor: several #elif, and a missing #endif is an error
	public void testPreprocessor() {
		Map<String,Object> sym = new HashMap<>();
		sym.put("C", true);
		String out = ScriptPreProcessor.preprocess("//#if A\na\n//#elif B\nb\n//#elif C\nc\n//#else\nd\n//#endif\n", sym);
		assertTrue(out, out.contains("\nc\n") || out.startsWith("c\n") || out.contains("\nc"));
		assertTrue(out, out.contains("//a"));
		assertThrows(RuntimeException.class, () -> ScriptPreProcessor.preprocess("//#if A\na\n", sym));
	}

	// Transpiled comments: backslashes cannot form a unicode escape for javac
	public void testCommentSafe() {
		assertEquals("a\\u005cu000ab", JSTranspiler.commentSafe("a\\u000ab"));
		assertEquals("plain", JSTranspiler.commentSafe("plain"));
		assertNull(JSTranspiler.commentSafe(null));
	}

	// PathTranspiler: two sources mapping to the same class fail; commonJS is applied
	public void testPathTranspilerNamesAndCommonJS() throws Exception {
		Path src = Files.createTempDirectory("pt-src");
		Path out = Files.createTempDirectory("pt-out");
		// "fooBar.js" and "foobar.js" map to classes p.FooBar and p.Foobar,
		// whose files would overwrite each other on a case-insensitive file system
		Files.writeString(src.resolve("fooBar.js"), "var x=1;");
		Files.writeString(src.resolve("foobar.js"), "var x=2;");
		PathTranspiler t = PathTranspiler.newBuilder()
				.options(JSTranspilerOptions.newBuilder().build())
				.sourceFolder(src).outputFolder(out).jsPackage("p")
				.pathFactory(() -> List.of(src.resolve("fooBar.js"), src.resolve("foobar.js")))
				.build();
		assertThrows(JSTranspilerException.class, t::execute);
		Path src2 = Files.createTempDirectory("pt-src2");
		Files.writeString(src2.resolve("m.js"), "module.exports = 1;");
		PathTranspiler cjs = PathTranspiler.newBuilder()
				.options(JSTranspilerOptions.newBuilder().commonJS(true).build())
				.sourceFolder(src2).outputFolder(out).jsPackage("q")
				.pathFactory(() -> List.of(src2.resolve("m.js")))
				.build();
		cjs.execute();
		String java = Files.readString(out.resolve("q").resolve("M.java"));
		assertTrue(java, java.contains("isCommonJS()"));
	}

	// PathTranspiler: an unchanged output file is not rewritten (it keeps its modification
	// time, so an incremental Java build does not compile it again); a changed one is
	public void testPathTranspilerKeepsUnchangedFiles() throws Exception {
		Path src = Files.createTempDirectory("pt-keep-src");
		Path out = Files.createTempDirectory("pt-keep-out");
		Files.writeString(src.resolve("a.js"), "var x=1;");
		java.util.function.Supplier<PathTranspiler> transpiler = () -> PathTranspiler.newBuilder()
				.options(JSTranspilerOptions.newBuilder().build())
				.sourceFolder(src).outputFolder(out).jsPackage("k").sourceFile(true)
				.pathFactory(() -> List.of(src.resolve("a.js")))
				.build();
		transpiler.get().execute();
		Path javaFile = out.resolve("k").resolve("A.java");
		Path js = out.resolve("k").resolve("A.js");
		java.nio.file.attribute.FileTime old = java.nio.file.attribute.FileTime.fromMillis(1_000_000_000_000L);
		Files.setLastModifiedTime(javaFile, old);
		Files.setLastModifiedTime(js, old);
		transpiler.get().execute();
		assertEquals(old, Files.getLastModifiedTime(javaFile));
		assertEquals(old, Files.getLastModifiedTime(js));
		Files.writeString(src.resolve("a.js"), "var x=2;");
		transpiler.get().execute();
		assertFalse(old.equals(Files.getLastModifiedTime(javaFile)));
		assertTrue(Files.readString(javaFile).contains("2"));
	}
}
