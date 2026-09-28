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
package tests.path;

import java.util.Collections;
import java.nio.file.Files;
import java.net.URL;
import java.io.InputStream;
import static org.junit.Assert.assertThrows;

import java.io.File;
import java.nio.file.Path;

import org.monflabs.util.path.PathClassLoader;

import tests.ProjectTestCase;


/**
 * PathClassloader test
 *
 * @author priand
 */
public class PathClassLoaderTest extends ProjectTestCase {

	public void testClassLoader() throws Exception {
		File root = support.getTargetTextClassesDirectory();
		Path path = root.toPath();
		
		PathClassLoader cl = new PathClassLoader(null, path);
		
		Class<?> c = cl.loadClass("sample.Class1");
		@SuppressWarnings("deprecation")
		String ss = c.newInstance().toString();
		
		assertEquals("Class #1", ss);
		assertThrows(ClassNotFoundException.class, () -> cl.loadClass("sample.Class2") );
	}

	public void testResourcesAndPackage() throws Exception {
		Path root = Files.createTempDirectory("pathclassloader");
		try {
			Files.createDirectories(root.resolve("sample"));
			Files.copy(support.getTargetTextClassesDirectory().toPath().resolve("sample/Class1.class"), root.resolve("sample/Class1.class"));
			Files.writeString(root.resolve("sample/res.txt"), "resource");
			Files.writeString(root.resolve("../outside-pcl.txt"), "outside");
			try {
				PathClassLoader cl = new PathClassLoader(null, root);
				// Resources used to be invisible
				URL url = cl.getResource("sample/res.txt");
				assertNotNull(url);
				try(InputStream is = url.openStream()) {
					assertEquals("resource", new String(is.readAllBytes()));
				}
				try(InputStream is = cl.getResourceAsStream("sample/res.txt")) {
					assertEquals("resource", new String(is.readAllBytes()));
				}
				assertEquals(1, Collections.list(cl.getResources("sample/res.txt")).size());
				assertNull(cl.getResource("sample/missing.txt"));
				assertNull(cl.getResource("sample"));   // a directory is not a resource
				// A resource name cannot escape the root
				assertNull(cl.getResource("../outside-pcl.txt"));
				assertNull(cl.getResourceAsStream("../outside-pcl.txt"));

				// The package is defined
				Class<?> c = cl.loadClass("sample.Class1");
				assertNotNull(c.getPackage());
				assertEquals("sample", c.getPackage().getName());
				assertSame(cl, c.getClassLoader());
				assertSame(c, cl.alreadyLoaded("sample.Class1"));
			} finally {
				Files.delete(root.resolve("../outside-pcl.txt"));
			}
		} finally {
			org.monflabs.util.FileUtil.deleteFile(root.toFile());
		}
	}

	public void testFileSystemConstructor() throws Exception {
		// The class loader over the root of another file system (here a zip file)
		Path zip = Files.createTempFile("pathclassloader", ".zip");
		Files.delete(zip);
		try {
			try(java.nio.file.FileSystem fs = java.nio.file.FileSystems.newFileSystem(zip, java.util.Map.of("create", "true"))) {
				Files.createDirectories(fs.getPath("/sample"));
				Files.copy(support.getTargetTextClassesDirectory().toPath().resolve("sample/Class1.class"), fs.getPath("/sample/Class1.class"));
				Files.writeString(fs.getPath("/sample/res.txt"), "zipped");
			}
			try(java.nio.file.FileSystem fs = java.nio.file.FileSystems.newFileSystem(zip)) {
				PathClassLoader cl = new PathClassLoader(null, fs);
				@SuppressWarnings("deprecation")
				String ss = cl.loadClass("sample.Class1").newInstance().toString();
				assertEquals("Class #1", ss);
				try(InputStream is = cl.getResourceAsStream("sample/res.txt")) {
					assertEquals("zipped", new String(is.readAllBytes()));
				}
			}
		} finally {
			Files.deleteIfExists(zip);
		}
	}
}
