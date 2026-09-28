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

import java.lang.reflect.Constructor;
import java.util.List;

import org.monflabs.javacompiler.FactoryClassLoader;
import org.monflabs.javacompiler.JavaCompiler;
import org.monflabs.javacompiler.JavaCompilerFactory;
import org.monflabs.javacompiler.factory.MapSourceFactory;
import org.monflabs.javacompiler.factory.MapTargetFactory;

import tests.ProjectTestCase;

public class SimpleHelloWorldTest extends ProjectTestCase {
	
	private static final String TEST_JAVA =
"""
public class Test {
	public String welcome() {
	    return Imp.welcome();
	}
}			
""";
	private static final String IMP_JAVA =
"""
public class Imp {
	public static String welcome() {
	    return "Hello, Imported World!";
	}
}			
""";
	
	public void testCompileHelloWorld() throws Exception {
		MapSourceFactory src = new MapSourceFactory();
		MapTargetFactory tgt = new MapTargetFactory();
		
		src.put("Test.java", TEST_JAVA)
		   .put("Imp.java", IMP_JAVA);
		
		try(JavaCompiler cp = JavaCompilerFactory.newBuilder()
				.classLoader(getClass().getClassLoader())
				.sourceFactory(src)
				.targetFactory(tgt)
				.options(List.of("-Xdiags:verbose"))
				.build() ) {
			cp.compile("Test","Imp");
			
			assertTrue( tgt.getFiles().containsKey("Test.class") );
			assertTrue( tgt.getFiles().containsKey("Imp.class") );
			
			FactoryClassLoader cl = new FactoryClassLoader(getClass().getClassLoader(), tgt);
			Constructor<?> ctor = cl.loadClass("Test").getConstructor();
			Object test = ctor.newInstance();
			
			String res = (String)test.getClass().getMethod("welcome").invoke(test);
			assertEquals("Hello, Imported World!",res);
		}
	}

	public void testCompileErrorThrows() throws Exception {
		MapSourceFactory src = new MapSourceFactory();
		MapTargetFactory tgt = new MapTargetFactory();
		src.put("Broken.java", "public class Broken { public int f() { return \"not an int\"; } }");
		try(JavaCompiler cp = JavaCompilerFactory.newBuilder()
				.classLoader(getClass().getClassLoader())
				.sourceFactory(src)
				.targetFactory(tgt)
				.build() ) {
			try {
				cp.compile("Broken");
				fail("A compilation error must throw");
			} catch(org.monflabs.javacompiler.JavaCompilerException e) {
				assertTrue(e.getMessage(), e.getMessage().contains("kind=ERROR"));
			}
		}
	}
}
