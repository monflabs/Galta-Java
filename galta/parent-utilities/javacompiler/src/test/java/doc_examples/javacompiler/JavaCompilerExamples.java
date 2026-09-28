package doc_examples.javacompiler;

import static org.junit.Assert.assertThrows;

import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Supplier;

import org.monflabs.filesystem.memory.MemoryFileSystem;
import org.monflabs.javacompiler.JavaCompiler;
import org.monflabs.javacompiler.JavaCompilerException;
import org.monflabs.javacompiler.JavaCompilerFactory;
import org.monflabs.javacompiler.factory.MapSourceFactory;
import org.monflabs.javacompiler.factory.MapTargetFactory;

import tests.ProjectTestCase;

/**
 * Samples for docs/Utilities/JavaCompiler.md.
 */
public class JavaCompilerExamples extends ProjectTestCase {

	public void testCompileAndLoad() throws Exception {
		MapSourceFactory sources = new MapSourceFactory()
				.put("com/acme/Greeter.java", """
						package com.acme;
						public class Greeter implements java.util.function.Supplier<String> {
						    public String get() { return "Hello from " + getClass().getName(); }
						}
						""");
		MapTargetFactory classes = new MapTargetFactory();

		try (JavaCompiler compiler = JavaCompilerFactory.newBuilder()
				.classLoader(getClass().getClassLoader())
				.sourceFactory(sources)
				.targetFactory(classes)
				.build()) {
			compiler.compile("com.acme.Greeter");   // a class name, not a file name

			assertTrue(classes.getFiles().containsKey("com/acme/Greeter.class"));

			Class<?> c = compiler.getClassLoader().loadClass("com.acme.Greeter");
			@SuppressWarnings("unchecked")
			Supplier<String> greeter = (Supplier<String>) c.getConstructor().newInstance();
			assertEquals("Hello from com.acme.Greeter", greeter.get());
		}
	}

	public void testSeveralUnitsTogether() throws Exception {
		MapSourceFactory sources = new MapSourceFactory()
				.put("Main.java", "public class Main { public static int run() { return Helper.twice(21); } }")
				.put("Helper.java", "public class Helper { static int twice(int x) { return x * 2; } }");
		MapTargetFactory classes = new MapTargetFactory();
		try (JavaCompiler compiler = JavaCompilerFactory.newBuilder()
				.classLoader(getClass().getClassLoader())
				.sourceFactory(sources)
				.targetFactory(classes)
				.build()) {
			compiler.compile("Main", "Helper");   // units referencing each other go in one call
			Object result = compiler.getClassLoader().loadClass("Main").getMethod("run").invoke(null);
			assertEquals(42, result);
		}
	}

	public void testSeparateCallsSeeEarlierClasses() throws Exception {
		MapSourceFactory sources = new MapSourceFactory()
				.put("Main.java", "public class Main { public static int run() { return Helper.twice(21); } }")
				.put("Helper.java", "public class Helper { static int twice(int x) { return x * 2; } }");
		MapTargetFactory classes = new MapTargetFactory();
		try (JavaCompiler compiler = JavaCompilerFactory.newBuilder()
				.classLoader(getClass().getClassLoader())
				.sourceFactory(sources)
				.targetFactory(classes)
				.build()) {
			compiler.compile("Helper");
			// Helper.class is in the target: javac finds it there
			compiler.compile("Main");
			Object result = compiler.getClassLoader().loadClass("Main").getMethod("run").invoke(null);
			assertEquals(42, result);
		}
	}

	public void testCompileBetweenFolders() throws Exception {
		try (FileSystem fs = MemoryFileSystem.newBuilder().build()) {
			Path src = Files.createDirectories(fs.getPath("/src/demo"));
			Path out = Files.createDirectories(fs.getPath("/classes"));
			Files.writeString(src.resolve("Answer.java"),
					"package demo; public class Answer { public static int value() { return 42; } }");

			try (JavaCompiler compiler = JavaCompilerFactory.newBuilder()
					.classLoader(getClass().getClassLoader())
					.sourceFolder(fs.getPath("/src"), StandardCharsets.UTF_8)
					.targetFolder(out)
					.build()) {
				compiler.compile("demo.Answer");
				assertTrue(Files.exists(fs.getPath("/classes/demo/Answer.class")));
				assertEquals(42, compiler.getClassLoader().loadClass("demo.Answer").getMethod("value").invoke(null));
			}
		}
	}

	public void testCompileError() throws Exception {
		MapSourceFactory sources = MapSourceFactory.of("Broken.java",
				"public class Broken {\n  int f() { return \"text\"; }\n}");
		try (JavaCompiler compiler = JavaCompilerFactory.newBuilder()
				.classLoader(getClass().getClassLoader())
				.sourceFactory(sources)
				.targetFactory(new MapTargetFactory())
				.build()) {
			JavaCompilerException e = assertThrows(JavaCompilerException.class, () -> compiler.compile("Broken"));
			// Unable to compile the source
			// [kind=ERROR, line=2, col=20, message=incompatible types: String cannot be converted to int]
			assertTrue(e.getMessage().startsWith("Unable to compile the source"));
			assertTrue(e.getMessage().contains("[kind=ERROR, line=2, col=20, message=incompatible types"));
		}
	}

	private static final String RAW_LIST = "public class Raw { java.util.List list = new java.util.ArrayList(); "
			+ "void add() { list.add(\"x\"); } }";

	public void testWarnings() throws Exception {
		// Warnings are tolerated by default...
		try (JavaCompiler compiler = JavaCompilerFactory.newBuilder()
				.classLoader(getClass().getClassLoader())
				.sourceFactory(MapSourceFactory.of("Raw.java", RAW_LIST))
				.targetFactory(new MapTargetFactory())
				.options(List.of("-Xlint:unchecked"))
				.build()) {
			compiler.compile("Raw");
		}
		// ...and fail the compilation with failOnWarnings(true)
		try (JavaCompiler compiler = JavaCompilerFactory.newBuilder()
				.classLoader(getClass().getClassLoader())
				.sourceFactory(MapSourceFactory.of("Raw.java", RAW_LIST))
				.targetFactory(new MapTargetFactory())
				.options(List.of("-Xlint:unchecked"))
				.failOnWarnings(true)
				.build()) {
			JavaCompilerException e = assertThrows(JavaCompilerException.class, () -> compiler.compile("Raw"));
			// [kind=MANDATORY_WARNING, line=1, col=90, message=unchecked call to add(E) as a member of the raw type java.util.List]
			assertTrue(e.getMessage().contains("kind=MANDATORY_WARNING"));
		}
	}

	public void testNotesNeverFail() throws Exception {
		// Without -Xlint:unchecked javac only emits a NOTE ("uses unchecked or unsafe operations"),
		// which is informational: it does not fail even with failOnWarnings(true)
		try (JavaCompiler compiler = JavaCompilerFactory.newBuilder()
				.classLoader(getClass().getClassLoader())
				.sourceFactory(MapSourceFactory.of("Raw.java", RAW_LIST))
				.targetFactory(new MapTargetFactory())
				.failOnWarnings(true)
				.build()) {
			compiler.compile("Raw");
		}
	}

	public void testClosedCompiler() throws Exception {
		JavaCompiler compiler = JavaCompilerFactory.newBuilder()
				.classLoader(getClass().getClassLoader())
				.sourceFactory(MapSourceFactory.of("A.java", "public class A {}"))
				.targetFactory(new MapTargetFactory())
				.build();
		compiler.close();
		JavaCompilerException e = assertThrows(JavaCompilerException.class, () -> compiler.compile("A"));
		assertEquals("Compiler is closed", e.getMessage());
	}

	public void testUsesApplicationClasspath() throws Exception {
		// Sources can use classes on the application class path (here: the utilities jar)
		MapSourceFactory sources = MapSourceFactory.of("UsesUtil.java",
				"public class UsesUtil { public static String run() { return org.monflabs.util.StringUtil.class.getSimpleName(); } }");
		try (JavaCompiler compiler = JavaCompilerFactory.newBuilder()
				.classLoader(getClass().getClassLoader())
				.sourceFactory(sources)
				.targetFactory(new MapTargetFactory())
				.build()) {
			compiler.compile("UsesUtil");
			assertEquals("StringUtil", compiler.getClassLoader().loadClass("UsesUtil").getMethod("run").invoke(null));
		}
	}

	public void testCompileAgainstAnotherCompiler() throws Exception {
		MapTargetFactory first = new MapTargetFactory();
		try (JavaCompiler compiler = JavaCompilerFactory.newBuilder()
				.classLoader(getClass().getClassLoader())
				.sourceFactory(MapSourceFactory.of("Helper.java", "public class Helper { public static int one() { return 1; } }"))
				.targetFactory(first)
				.build()) {
			compiler.compile("Helper");
			ClassLoader withHelper = compiler.getClassLoader();

			// A compiler using that class loader as parent compiles against Helper
			try (JavaCompiler second = JavaCompilerFactory.newBuilder()
					.classLoader(withHelper)
					.sourceFactory(MapSourceFactory.of("Main.java", "public class Main { public static int run() { return Helper.one() + 1; } }"))
					.targetFactory(new MapTargetFactory())
					.build()) {
				second.compile("Main");
				assertEquals(2, second.getClassLoader().loadClass("Main").getMethod("run").invoke(null));
			}
		}
	}

	public void testRecompile() throws Exception {
		MapSourceFactory sources = MapSourceFactory.of("Version.java", "public class Version { public static int get() { return 1; } }");
		try (JavaCompiler compiler = JavaCompilerFactory.newBuilder()
				.classLoader(getClass().getClassLoader())
				.sourceFactory(sources)
				.targetFactory(new MapTargetFactory())
				.build()) {
			compiler.compile("Version");
			assertEquals(1, compiler.getClassLoader().loadClass("Version").getMethod("get").invoke(null));

			sources.put("Version.java", "public class Version { public static int get() { return 2; } }");
			compiler.compile("Version");
			// getClassLoader() returns a new class loader after a recompilation
			assertEquals(2, compiler.getClassLoader().loadClass("Version").getMethod("get").invoke(null));
		}
	}
}
