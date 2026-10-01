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
package tests.javac;

import static org.junit.Assert.assertThrows;

import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.ServiceLoader;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.monflabs.javacompiler.FactoryClassLoader;
import org.monflabs.javacompiler.JavaCompiler;
import org.monflabs.javacompiler.JavaCompilerException;
import org.monflabs.javacompiler.JavaCompilerFactory;
import org.monflabs.javacompiler.PathFileFactory;
import org.monflabs.javacompiler.factory.MapSourceFactory;
import org.monflabs.javacompiler.factory.MapTargetFactory;

import tests.ProjectTestCase;

/**
 * Regression tests for the compiler class loading and class path handling.
 */
public class JavaCompilerRegressionTest extends ProjectTestCase {

	private JavaCompiler newCompiler(MapSourceFactory src, MapTargetFactory tgt) {
		return JavaCompilerFactory.newBuilder()
				.classLoader(getClass().getClassLoader())
				.sourceFactory(src)
				.targetFactory(tgt)
				.build();
	}

	public void testRecompiledClassIsNotStale() throws Exception {
		MapSourceFactory src = MapSourceFactory.of("V.java", "public class V { public static int v() { return 1; } }");
		MapTargetFactory tgt = new MapTargetFactory();
		try(JavaCompiler c = newCompiler(src, tgt)) {
			c.compile("V");
			ClassLoader first = c.getClassLoader();
			assertEquals(1, first.loadClass("V").getMethod("v").invoke(null));

			src.put("V.java", "public class V { public static int v() { return 2; } }");
			c.compile("V");
			ClassLoader second = c.getClassLoader();
			assertNotSame(first, second);
			assertEquals(2, second.loadClass("V").getMethod("v").invoke(null));
			// The class already loaded through the first loader is unchanged
			assertEquals(1, first.loadClass("V").getMethod("v").invoke(null));
		}
	}

	public void testClassLoaderKeptWhenNothingLoaded() throws Exception {
		MapSourceFactory src = new MapSourceFactory()
				.put("A.java", "public class A {}")
				.put("B.java", "public class B {}");
		try(JavaCompiler c = newCompiler(src, new MapTargetFactory())) {
			ClassLoader cl = c.getClassLoader();
			c.compile("A");
			c.compile("B");
			assertSame(cl, c.getClassLoader());
			assertSame(c.getClassLoader(), c.getClassLoader());
		}
	}

	public void testCompiledClassesTakePrecedence() throws Exception {
		assertEquals("classpath", ShadowedOnClassPath.origin());
		MapSourceFactory src = MapSourceFactory.of("tests/javac/ShadowedOnClassPath.java",
				"package tests.javac; public class ShadowedOnClassPath { public static String origin() { return \"compiled\"; } }");
		try(JavaCompiler c = newCompiler(src, new MapTargetFactory())) {
			c.compile("tests.javac.ShadowedOnClassPath");
			Class<?> cl = c.getClassLoader().loadClass("tests.javac.ShadowedOnClassPath");
			assertNotSame(ShadowedOnClassPath.class, cl);
			assertEquals("compiled", cl.getMethod("origin").invoke(null));
		}
	}

	public void testJavaPackagesAreAlwaysDelegated() throws Exception {
		MapTargetFactory tgt = new MapTargetFactory();
		tgt.getFiles().put("java/lang/String.class", new byte[] {1,2,3});
		FactoryClassLoader cl = new FactoryClassLoader(getClass().getClassLoader(), tgt);
		assertSame(String.class, cl.loadClass("java.lang.String"));
		assertFalse(cl.hasDefinedClasses());
	}

	public void testCompiledClassReadableAsResource() throws Exception {
		MapTargetFactory tgt = new MapTargetFactory();
		try(JavaCompiler c = newCompiler(MapSourceFactory.of("R.java", "public class R {}"), tgt)) {
			c.compile("R");
			try(InputStream is = c.getClassLoader().getResourceAsStream("R.class")) {
				assertNotNull(is);
				byte[] b = is.readAllBytes();
				assertEquals(0xCA, b[0] & 0xFF);
				assertEquals(0xFE, b[1] & 0xFF);
			}
			// Other resources still come from the parent
			assertNull(c.getClassLoader().getResourceAsStream("does/not/exist.class"));
		}
	}

	public void testInnerAndPackagedClasses() throws Exception {
		MapSourceFactory src = MapSourceFactory.of("p/q/Outer.java",
				"package p.q; public class Outer { public static class Inner { public int v() { return 5; } } }");
		MapTargetFactory tgt = new MapTargetFactory();
		try(JavaCompiler c = newCompiler(src, tgt)) {
			c.compile("p.q.Outer");
			assertTrue(tgt.getFiles().containsKey("p/q/Outer$Inner.class"));
			Class<?> inner = c.getClassLoader().loadClass("p.q.Outer$Inner");
			assertEquals(5, inner.getMethod("v").invoke(inner.getConstructor().newInstance()));
			assertEquals(List.of("p/q/Outer$Inner.class", "p/q/Outer.class"), sorted(tgt.listClassFiles("p/q")));
			assertTrue(tgt.listClassFiles("p").isEmpty());
		}
	}

	public void testMissingSourceReportsItsName() throws Exception {
		try(JavaCompiler c = newCompiler(new MapSourceFactory(), new MapTargetFactory())) {
			JavaCompilerException e = assertThrows(JavaCompilerException.class, () -> c.compile("com.acme.Nope"));
			assertTrue(e.getMessage(), e.getMessage().contains("com/acme/Nope.java"));
		}
	}

	public void testWarningsAreAvailable() throws Exception {
		String raw = "public class Raw { java.util.List list = new java.util.ArrayList(); void add() { list.add(\"x\"); } }";
		try(JavaCompiler c = JavaCompilerFactory.newBuilder()
				.classLoader(getClass().getClassLoader())
				.sourceFactory(MapSourceFactory.of("Raw.java", raw))
				.targetFactory(new MapTargetFactory())
				.options(List.of("-Xlint:unchecked"))
				.build()) {
			assertTrue(c.getWarnings().isEmpty());
			c.compile("Raw");
			assertEquals(1, c.getWarnings().size());
			assertTrue(c.getWarnings().get(0).startsWith("[kind=MANDATORY_WARNING"));
		}
	}

	public void testConcurrentCompilations() throws Exception {
		MapSourceFactory src = new MapSourceFactory();
		for(int i=0; i<20; i++) {
			src.put("C"+i+".java", "public class C"+i+" { public static int v() { return "+i+"; } }");
		}
		MapTargetFactory tgt = new MapTargetFactory();
		ExecutorService ex = Executors.newFixedThreadPool(4);
		try(JavaCompiler c = newCompiler(src, tgt)) {
			List<Future<?>> futures = new ArrayList<>();
			for(int i=0; i<20; i++) {
				String name = "C"+i;
				futures.add(ex.submit(() -> { c.compile(name); return null; }));
			}
			for(Future<?> f: futures) {
				f.get();
			}
			for(int i=0; i<20; i++) {
				assertEquals(i, c.getClassLoader().loadClass("C"+i).getMethod("v").invoke(null));
			}
		} finally {
			ex.shutdown();
		}
	}

	public void testUrlClassLoaderIsOnCompileClassPath() throws Exception {
		Path dir = support.getTargetTempDirectory("javac-urlcl", true).toPath();
		try(JavaCompiler c = JavaCompilerFactory.newBuilder()
				.classLoader(getClass().getClassLoader())
				.sourceFactory(MapSourceFactory.of("lib/Lib.java", "package lib; public class Lib { public static int one() { return 1; } }"))
				.targetFolder(dir)
				.build()) {
			c.compile("lib.Lib");
		}
		// Only reachable through the URL class loader: not in java.class.path, not a FactoryClassLoader
		try(URLClassLoader ucl = new URLClassLoader(new URL[] {dir.toUri().toURL()}, getClass().getClassLoader());
			JavaCompiler c = newCompilerWithParent(ucl, MapSourceFactory.of("Main.java", "public class Main { public static int run() { return lib.Lib.one()+1; } }"))) {
			c.compile("Main");
			assertEquals(2, c.getClassLoader().loadClass("Main").getMethod("run").invoke(null));
		}
	}

	private JavaCompiler newCompilerWithParent(ClassLoader parent, MapSourceFactory src) {
		return JavaCompilerFactory.newBuilder()
				.classLoader(parent)
				.sourceFactory(src)
				.targetFactory(new MapTargetFactory())
				.build();
	}

	public void testAnnotationProcessorOutputs() throws Exception {
		MapTargetFactory tgt = new MapTargetFactory();
		try(JavaCompiler c = JavaCompilerFactory.newBuilder()
				.classLoader(getClass().getClassLoader())
				.sourceFactory(MapSourceFactory.of("UsesGenerated.java", "public class UsesGenerated { public static int run() { return gen.Generated.value(); } }"))
				.targetFactory(tgt)
				.options(List.of("-processor", GeneratingProcessor.class.getName()))
				.build()) {
			c.compile("UsesGenerated");
			// The generated source is compiled, but not written with the classes
			assertTrue(tgt.getFiles().containsKey("gen/Generated.class"));
			assertFalse(tgt.getFiles().containsKey("gen/Generated.java"));
			// A resource created in CLASS_OUTPUT goes to the target
			assertEquals("generated", new String(tgt.getFiles().get("gen/info.txt"), StandardCharsets.UTF_8));
			assertEquals(7, c.getClassLoader().loadClass("UsesGenerated").getMethod("run").invoke(null));
		}
	}

	public void testPathFileFactory() throws Exception {
		PathFileFactory missing = new PathFileFactory(Path.of("target/does-not-exist"), StandardCharsets.UTF_8);
		assertFalse(missing.isValid());
		assertNull(missing.readBytes("A.class"));
		assertTrue(missing.listClassFiles("").isEmpty());

		// A relative root is resolved against the working directory
		Path rel = Path.of("target/temp/javac-relative");
		support.getTargetTempDirectory("javac-relative", true);
		PathFileFactory f = new PathFileFactory(rel, StandardCharsets.UTF_8);
		assertTrue(f.isValid());
		try(JavaCompiler c = JavaCompilerFactory.newBuilder()
				.classLoader(getClass().getClassLoader())
				.sourceFactory(MapSourceFactory.of("a/A.java", "package a; public class A {}"))
				.targetFactory(f)
				.build()) {
			c.compile("a.A");
		}
		assertTrue(Files.isRegularFile(rel.resolve("a/A.class")));
		assertEquals(List.of("a/A.class"), sorted(f.listClassFiles("a")));
		assertNotNull(f.readBytes("a/A.class"));
	}

	public void testAnnotationProcessorsOffByDefault() throws Exception {
		// A processor registered as a service on the host class path
		Path dir = support.getTargetTempDirectory("javac-probe", true).toPath();
		Files.createDirectories(dir.resolve("META-INF/services"));
		Files.writeString(dir.resolve("META-INF/services/javax.annotation.processing.Processor"), ProbeProcessor.class.getName());
		try(URLClassLoader ucl = new URLClassLoader(new URL[] {dir.toUri().toURL()}, getClass().getClassLoader())) {
			int before = ProbeProcessor.ROUNDS.get();
			try(JavaCompiler c = newCompilerWithParent(ucl, MapSourceFactory.of("P.java", "public class P {}"))) {
				c.compile("P");
			}
			assertEquals("discovered processors must not run by default", before, ProbeProcessor.ROUNDS.get());
			// They run when the options ask for annotation processing
			try(JavaCompiler c = JavaCompilerFactory.newBuilder()
					.classLoader(ucl)
					.sourceFactory(MapSourceFactory.of("P.java", "public class P {}"))
					.targetFactory(new MapTargetFactory())
					.options(List.of("-proc:full"))
					.build()) {
				c.compile("P");
			}
			assertTrue(ProbeProcessor.ROUNDS.get() > before);
		}
	}

	public void testFailedCompilationWritesNothing() throws Exception {
		MapSourceFactory src = new MapSourceFactory()
				.put("Good.java", "public class Good { static class Nested {} }")
				.put("Bad.java", "public class Bad { int f() { return \"x\"; } }");
		MapTargetFactory tgt = new MapTargetFactory();
		try(JavaCompiler c = newCompiler(src, tgt)) {
			assertThrows(JavaCompilerException.class, () -> c.compile("Good", "Bad"));
			assertTrue(tgt.getFiles().toString(), tgt.getFiles().isEmpty());
		}
		// Same when the compilation fails on warnings
		String raw = "public class Raw { java.util.List list = new java.util.ArrayList(); void add() { list.add(\"x\"); } }";
		MapTargetFactory tgt2 = new MapTargetFactory();
		try(JavaCompiler c = JavaCompilerFactory.newBuilder()
				.classLoader(getClass().getClassLoader())
				.sourceFactory(MapSourceFactory.of("Raw.java", raw))
				.targetFactory(tgt2)
				.options(List.of("-Xlint:unchecked"))
				.failOnWarnings(true)
				.build()) {
			assertThrows(JavaCompilerException.class, () -> c.compile("Raw"));
			assertTrue(tgt2.getFiles().isEmpty());
		}
	}

	public void testFailedRecompilationKeepsPreviousClasses() throws Exception {
		MapSourceFactory src = MapSourceFactory.of("V.java", "public class V { public static int v() { return 1; } }");
		MapTargetFactory tgt = new MapTargetFactory();
		try(JavaCompiler c = newCompiler(src, tgt)) {
			c.compile("V");
			ClassLoader first = c.getClassLoader();
			assertEquals(1, first.loadClass("V").getMethod("v").invoke(null));
			byte[] before = tgt.getFiles().get("V.class");
			src.put("V.java", "public class V { public static int v() { return nope; } }");
			assertThrows(JavaCompilerException.class, () -> c.compile("V"));
			assertSame(before, tgt.getFiles().get("V.class"));
			// Nothing changed: the class loader is kept
			assertSame(first, c.getClassLoader());
		}
	}

	public void testClassLoaderKeptWhenOtherClassesCompiled() throws Exception {
		MapSourceFactory src = new MapSourceFactory()
				.put("A.java", "public class A { public int v() { return 3; } }")
				.put("B.java", "public class B { public static int use(A a) { return a.v()*2; } }");
		try(JavaCompiler c = newCompiler(src, new MapTargetFactory())) {
			c.compile("A");
			ClassLoader first = c.getClassLoader();
			Class<?> a = first.loadClass("A");
			Object instance = a.getConstructor().newInstance();
			c.compile("B");
			// A was not rewritten: the loader is kept, and B works with the existing A instances
			ClassLoader second = c.getClassLoader();
			assertSame(first, second);
			assertSame(a, second.loadClass("A"));
			assertEquals(6, second.loadClass("B").getMethod("use", a).invoke(null, instance));
		}
	}

	public void testStaleNestedClassesArePruned() throws Exception {
		MapSourceFactory src = MapSourceFactory.of("p/Outer.java",
				"package p; public class Outer { static class Gone {} Runnable r = new Runnable() { public void run() {} }; }");
		MapTargetFactory tgt = new MapTargetFactory();
		tgt.getFiles().put("p/Other.class", new byte[] {1});
		try(JavaCompiler c = newCompiler(src, tgt)) {
			c.compile("p.Outer");
			assertEquals(List.of("p/Other.class", "p/Outer$1.class", "p/Outer$Gone.class", "p/Outer.class"), sorted(tgt.listClassFiles("p")));
			src.put("p/Outer.java", "package p; public class Outer {}");
			c.compile("p.Outer");
			assertEquals(List.of("p/Other.class", "p/Outer.class"), sorted(tgt.listClassFiles("p")));
		}
		// Also in a folder
		Path dir = support.getTargetTempDirectory("javac-prune", true).toPath();
		MapSourceFactory src2 = MapSourceFactory.of("Outer.java", "public class Outer { static class Gone {} }");
		try(JavaCompiler c = JavaCompilerFactory.newBuilder()
				.classLoader(getClass().getClassLoader())
				.sourceFactory(src2)
				.targetFolder(dir)
				.build()) {
			c.compile("Outer");
			assertTrue(Files.exists(dir.resolve("Outer$Gone.class")));
			src2.put("Outer.java", "public class Outer {}");
			c.compile("Outer");
			assertFalse(Files.exists(dir.resolve("Outer$Gone.class")));
			assertTrue(Files.exists(dir.resolve("Outer.class")));
		}
	}

	public void testDiagnosticsNameTheSource() throws Exception {
		MapSourceFactory src = new MapSourceFactory()
				.put("ok/Fine.java", "package ok; public class Fine {}")
				.put("ko/Broken.java", "package ko; public class Broken { int f() { return \"x\"; } }");
		try(JavaCompiler c = newCompiler(src, new MapTargetFactory())) {
			JavaCompilerException e = assertThrows(JavaCompilerException.class, () -> c.compile("ok.Fine", "ko.Broken"));
			assertTrue(e.getMessage(), e.getMessage().contains("[kind=ERROR, source=ko/Broken.java, line=1, col="));
		}
	}

	public void testInvalidCallsThrowCompilerExceptions() throws Exception {
		try(JavaCompiler c = newCompiler(MapSourceFactory.of("A.java", "public class A {}"), new MapTargetFactory())) {
			JavaCompilerException e = assertThrows(JavaCompilerException.class, () -> c.compile(List.of()));
			assertEquals("No source to compile", e.getMessage());
		}
		try(JavaCompiler c = JavaCompilerFactory.newBuilder()
				.classLoader(getClass().getClassLoader())
				.sourceFactory(MapSourceFactory.of("A.java", "public class A {}"))
				.targetFactory(new MapTargetFactory())
				.options(List.of("-no-such-option"))
				.build()) {
			JavaCompilerException e = assertThrows(JavaCompilerException.class, () -> c.compile("A"));
			assertTrue(e.getMessage(), e.getMessage().startsWith("Invalid compiler option"));
		}
	}

	public void testGeneratedResourcesAreServed() throws Exception {
		MapTargetFactory tgt = new MapTargetFactory();
		try(JavaCompiler c = JavaCompilerFactory.newBuilder()
				.classLoader(getClass().getClassLoader())
				.sourceFactory(MapSourceFactory.of("UsesGenerated.java", "public class UsesGenerated { public static int run() { return gen.Generated.value(); } }"))
				.targetFactory(tgt)
				.options(List.of("-processor", GeneratingProcessor.class.getName()))
				.build()) {
			c.compile("UsesGenerated");
			ClassLoader cl = c.getClassLoader();
			try(InputStream is = cl.getResourceAsStream("gen/info.txt")) {
				assertNotNull(is);
				assertEquals("generated", new String(is.readAllBytes(), StandardCharsets.UTF_8));
			}
			URL u = cl.getResource("gen/info.txt");
			assertNotNull(u);
			try(InputStream is = u.openStream()) {
				assertEquals("generated", new String(is.readAllBytes(), StandardCharsets.UTF_8));
			}
			assertEquals(1, Collections.list(cl.getResources("gen/info.txt")).size());
			// The factory root cannot be escaped
			assertNull(cl.getResourceAsStream("x/../../gen/info.txt"));
		}
		// Service files in the factory are visible to the ServiceLoader
		MapTargetFactory services = new MapTargetFactory();
		services.getFiles().put("META-INF/services/java.lang.Runnable", "svc.Task".getBytes(StandardCharsets.UTF_8));
		try(JavaCompiler c = newCompiler(MapSourceFactory.of("svc/Task.java", "package svc; public class Task implements Runnable { public void run() {} }"), services)) {
			c.compile("svc.Task");
			List<String> found = new ArrayList<>();
			ServiceLoader.load(Runnable.class, c.getClassLoader()).forEach(r -> found.add(r.getClass().getName()));
			assertEquals(List.of("svc.Task"), found);
		}
	}

	public void testPlatformPackagesAreParentFirst() throws Exception {
		MapTargetFactory tgt = new MapTargetFactory();
		tgt.getFiles().put("javax/tools/ToolProvider.class", new byte[] {1,2,3});
		FactoryClassLoader cl = new FactoryClassLoader(getClass().getClassLoader(), tgt);
		assertSame(javax.tools.ToolProvider.class, cl.loadClass("javax.tools.ToolProvider"));
		assertFalse(cl.hasDefinedClasses());
	}

	private static List<String> sorted(java.util.Collection<String> c) {
		List<String> l = new ArrayList<>(c);
		Collections.sort(l);
		return l;
	}

	public void testBuilderValidatesRequiredFields() {
		// A missing factory used to surface later as a NullPointerException inside javac
		org.junit.Assert.assertThrows(org.monflabs.util.ObjectBuilderException.class, () -> JavaCompilerFactory.newBuilder()
				.classLoader(getClass().getClassLoader())
				.targetFactory(new MapTargetFactory())
				.build());
		org.junit.Assert.assertThrows(org.monflabs.util.ObjectBuilderException.class, () -> JavaCompilerFactory.newBuilder()
				.sourceFactory(MapSourceFactory.of("A.java", "public class A {}"))
				.targetFactory(new MapTargetFactory())
				.build());
		org.junit.Assert.assertThrows(org.monflabs.util.ObjectBuilderException.class, () -> JavaCompilerFactory.newBuilder()
				.classLoader(getClass().getClassLoader())
				.sourceFactory(MapSourceFactory.of("A.java", "public class A {}"))
				.build());
	}

	public void testPlatformPackagesAreParentFirst() throws Exception {
		MapTargetFactory tgt = new MapTargetFactory();
		tgt.getFiles().put("javax/net/SocketFactory.class", new byte[] {1,2,3});
		tgt.getFiles().put("jdk/internal/misc/Unsafe.class", new byte[] {1,2,3});
		FactoryClassLoader cl = new FactoryClassLoader(getClass().getClassLoader(), tgt);
		// A class file in the factory used to shadow (or fail to redefine) the platform's
		assertSame(javax.net.SocketFactory.class, cl.loadClass("javax.net.SocketFactory"));
		assertFalse(cl.hasDefinedClasses());
	}

	public void testCompiledClassVisibleThroughAllResourceMethods() throws Exception {
		MapTargetFactory tgt = new MapTargetFactory();
		try(JavaCompiler c = newCompiler(MapSourceFactory.of("p/Res.java", "package p; public class Res {}"), tgt)) {
			c.compile("p.Res");
			ClassLoader cl = c.getClassLoader();
			byte[] expected = tgt.getFiles().get("p/Res.class");
			java.net.URL u = cl.getResource("p/Res.class");
			// getResourceAsStream() saw the compiled class, getResource()/getResources() did not
			assertNotNull(u);
			try(java.io.InputStream in = u.openStream()) {
				assertTrue(java.util.Arrays.equals(expected, in.readAllBytes()));
			}
			java.util.List<java.net.URL> all = java.util.Collections.list(cl.getResources("p/Res.class"));
			assertEquals(1, all.size());
			try(java.io.InputStream in = cl.getResourceAsStream("p/Res.class")) {
				assertTrue(java.util.Arrays.equals(expected, in.readAllBytes()));
			}
			assertNull(cl.getResource("p/Missing.class"));
		}
	}

	public void testMapTargetFactoryRecursiveListing() throws Exception {
		MapTargetFactory tgt = new MapTargetFactory();
		tgt.getFiles().put("a/A.class", new byte[0]);
		tgt.getFiles().put("a/b/B.class", new byte[0]);
		tgt.getFiles().put("a/b/info.txt", new byte[0]);
		tgt.getFiles().put("ab/C.class", new byte[0]);
		tgt.getFiles().put("D.class", new byte[0]);
		assertEquals(java.util.List.of("a/A.class"), new java.util.ArrayList<>(tgt.listClassFiles("a")));
		assertEquals(java.util.List.of("a/A.class", "a/b/B.class"), new java.util.ArrayList<>(tgt.listClassFiles("a", true)));
		assertEquals(java.util.List.of("D.class"), new java.util.ArrayList<>(tgt.listClassFiles("")));
		assertEquals(5 - 1, tgt.listClassFiles("", true).size());
	}
}
