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
package org.monflabs.tests;

import java.io.IOException;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import junit.framework.JUnit4TestAdapter;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

/**
 * Guard against test classes missing from a module's suites.
 * <p>
 * Surefire only runs the <code>All*Tests</code> suites of a module (see its <code>includes</code>):
 * a test class that is not registered in one of them never runs. Add the guard to the main suite
 * of the module, giving it every suite surefire runs for the module:
 * <pre>
 * suite.addTest(SuiteGuard.newTest(AllUtilTests.class, AllUtilDocExamplesTests.class));
 * </pre>
 * It scans the test classes folder of the first suite class for the test classes - concrete
 * {@link TestCase} subclasses with <code>test*</code> methods, and JUnit 4 classes with
 * <code>&#64;Test</code> methods - and fails when one of them is not in the suites (a class is also
 * covered when one of its subclasses is). It also fails when the suites run no test at all.
 * <p>
 * A class that is deliberately not part of the suites (a benchmark, a test run by another test)
 * is annotated with {@link NotInSuite}, which gives the reason.
 */
public final class SuiteGuard {

	/**
	 * Mark a test class that is deliberately not registered in the module's suites.
	 */
	@Documented
	@Retention(RetentionPolicy.RUNTIME)
	@Target(ElementType.TYPE)
	public @interface NotInSuite {
		/**
		 * Why the class is not in the suites (e.g. "benchmark, run manually").
		 * @return the reason
		 */
		String value();
	}

	private SuiteGuard() {
	}

	/**
	 * Create the test checking that the suites are complete.
	 * @param suiteClasses the suite classes surefire runs for the module (with a static
	 * <code>suite()</code> method); the test classes are found next to the first one
	 * @return the test
	 */
	public static Test newTest(Class<?>... suiteClasses) {
		return new GuardTest(suiteClasses);
	}

	/**
	 * The test checking the suites.
	 */
	public static class GuardTest extends TestCase {
		private final Class<?>[] suiteClasses;
		GuardTest(Class<?>[] suiteClasses) {
			super("testSuiteIsComplete");
			this.suiteClasses = suiteClasses;
		}
		public void testSuiteIsComplete() throws Exception {
			assertComplete(suiteClasses);
		}
	}

	/**
	 * Check that the suites contain every test class of their test classes folder, and at least one test.
	 * @param suiteClasses the suite classes
	 * @throws Exception if a suite cannot be built
	 */
	public static void assertComplete(Class<?>... suiteClasses) throws Exception {
		Set<Class<?>> included = new LinkedHashSet<>();
		int count = 0;
		for(Class<?> c: suiteClasses) {
			Test suite = (Test)c.getMethod("suite").invoke(null);
			collect(suite, included);
			// The guard itself does not count
			count += suite.countTestCases() - countGuards(suite);
		}
		included.remove(GuardTest.class);
		if(count<=0) {
			throw new AssertionError("The suites "+names(suiteClasses)+" run no test");
		}
		List<String> missing = findMissing(suiteClasses[0], included);
		if(!missing.isEmpty()) {
			throw new AssertionError("Test classes not registered in "+names(suiteClasses)+" (they never run): "+missing
					+". Register them in a suite, or annotate them with @SuiteGuard.NotInSuite(\"reason\")");
		}
	}

	private static int countGuards(Test t) {
		if(t instanceof GuardTest) {
			return 1;
		}
		int n = 0;
		if(t instanceof TestSuite s) {
			for(Enumeration<Test> e=s.tests(); e.hasMoreElements(); ) {
				n += countGuards(e.nextElement());
			}
		}
		return n;
	}

	private static String names(Class<?>[] classes) {
		List<String> l = new ArrayList<>();
		for(Class<?> c: classes) {
			l.add(c.getSimpleName());
		}
		return l.toString();
	}

	/**
	 * Collect the test classes of a suite.
	 */
	private static void collect(Test t, Set<Class<?>> out) {
		if(t instanceof TestSuite s) {
			for(Enumeration<Test> e=s.tests(); e.hasMoreElements(); ) {
				collect(e.nextElement(), out);
			}
		} else if(t instanceof JUnit4TestAdapter a) {
			out.add(a.getTestClass());
		} else if(t!=null) {
			out.add(t.getClass());
		}
	}

	/**
	 * Find the test classes of the folder of a suite class that are not in a set of classes.
	 * @param suiteClass the suite class, whose folder is scanned
	 * @param included the classes in the suites
	 * @return the names of the missing classes, sorted
	 * @throws IOException
	 */
	public static List<String> findMissing(Class<?> suiteClass, Set<Class<?>> included) throws IOException {
		Path root = classesRoot(suiteClass);
		List<String> missing = new ArrayList<>();
		if(root==null) {
			return missing;
		}
		ClassLoader loader = suiteClass.getClassLoader();
		try(Stream<Path> s = Files.walk(root)) {
			for(Path p: (Iterable<Path>)s::iterator) {
				String rel = root.relativize(p).toString().replace(p.getFileSystem().getSeparator(), "/");
				if(!rel.endsWith(".class") || rel.endsWith("module-info.class") || rel.endsWith("package-info.class")) {
					continue;
				}
				String name = rel.substring(0, rel.length()-".class".length()).replace('/', '.');
				Class<?> c;
				try {
					c = Class.forName(name, false, loader);
				} catch(Throwable t) {
					continue;
				}
				if(isTestClass(c) && !isCovered(c, included)) {
					missing.add(c.getName());
				}
			}
		}
		Collections.sort(missing);
		return missing;
	}

	private static boolean isCovered(Class<?> c, Set<Class<?>> included) {
		for(Class<?> i: included) {
			if(c.isAssignableFrom(i)) {
				return true;
			}
		}
		return false;
	}

	private static Path classesRoot(Class<?> c) {
		CodeSource cs = c.getProtectionDomain().getCodeSource();
		URL url = cs!=null ? cs.getLocation() : null;
		if(url==null || !"file".equals(url.getProtocol())) {
			return null;
		}
		try {
			Path p = Path.of(url.toURI());
			return Files.isDirectory(p) ? p : null;
		} catch(URISyntaxException | IllegalArgumentException ex) {
			return null;
		}
	}

	/**
	 * Tell if a class is a test class a runner would run: a concrete, static, {@link TestCase}
	 * with public <code>test*</code> methods, or a class with JUnit 4 <code>&#64;Test</code> methods.
	 * A class annotated with {@link NotInSuite} is not.
	 * @param c the class
	 * @return true for a test class
	 */
	public static boolean isTestClass(Class<?> c) {
		int m = c.getModifiers();
		if(c.isInterface() || Modifier.isAbstract(m) || c.isAnonymousClass() || c.isLocalClass()
				|| (c.isMemberClass() && !Modifier.isStatic(m)) || c.isAnnotationPresent(NotInSuite.class)) {
			return false;
		}
		Method[] methods;
		try {
			methods = c.getMethods();
		} catch(Throwable t) {
			return false;
		}
		boolean junit3 = TestCase.class.isAssignableFrom(c);
		for(Method method: methods) {
			if(method.getParameterCount()!=0 || Modifier.isStatic(method.getModifiers())) {
				continue;
			}
			if(junit3 && method.getName().startsWith("test") && method.getReturnType()==void.class) {
				return true;
			}
			if(method.isAnnotationPresent(org.junit.Test.class)) {
				return true;
			}
		}
		return false;
	}
}
