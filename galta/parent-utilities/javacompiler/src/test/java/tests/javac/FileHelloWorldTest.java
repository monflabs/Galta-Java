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
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.monflabs.filesystem.memory.MemoryFileSystem;
import org.monflabs.javacompiler.JavaCompiler;
import org.monflabs.javacompiler.JavaCompilerFactory;
import org.monflabs.util.path.PathClassLoader;

import tests.ProjectTestCase;

public class FileHelloWorldTest extends ProjectTestCase {
	
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
		FileSystem fs = MemoryFileSystem.newBuilder()
			.build();
		
		Path srcFolder = fs.getPath("src");
		Path tgtFolder = fs.getPath("tgt");
		
		Files.createDirectories(srcFolder);
		Files.createDirectories(tgtFolder);
		
		
		Files.writeString(srcFolder.resolve("Test.java"), TEST_JAVA, StandardCharsets.UTF_8);
		Files.writeString(srcFolder.resolve("Imp.java"), IMP_JAVA, StandardCharsets.UTF_8);
		
		try(JavaCompiler cp = JavaCompilerFactory.newBuilder()
				.classLoader(getClass().getClassLoader())
				.sourceFolder(srcFolder,StandardCharsets.UTF_8)
				.targetFolder(tgtFolder)
				.options(List.of("-Xdiags:verbose"))
				.build() ) {
			cp.compile("Test","Imp");
			
			assertTrue( Files.exists(tgtFolder.resolve("Test.class")) );
			assertTrue( Files.exists(tgtFolder.resolve("Imp.class")) );
			
			PathClassLoader cl = new PathClassLoader(getClass().getClassLoader(), tgtFolder);
			Constructor<?> ctor = cl.loadClass("Test").getConstructor();
			Object test = ctor.newInstance();
			
			String res = (String)test.getClass().getMethod("welcome").invoke(test);
			assertEquals("Hello, Imported World!",res);
		}
	}
}
