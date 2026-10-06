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
package playground;

import java.io.File;
import java.lang.reflect.Field;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.Map;

/**
 * Lets the Eclipse compiler (ecj) read the JDK classes on a runtime without
 * lib/jrt-fs.jar, like CheerpJ in the browser.
 * <p>
 * ecj opens the jrt file system of the Java home with FileSystems.newFileSystem(jrt:/,
 * java.home), which needs lib/jrt-fs.jar, and reads the version from its release file:
 * CheerpJ has neither, while its own jrt:/ file system has the classes. This class
 * seeds the two caches of ecj (JRTUtil.JRT_FILE_SYSTEMS and Jdk.pathToRelease) for the
 * Java home with the runtime's jrt:/ file system and version, so ecj uses them. It does
 * nothing when lib/jrt-fs.jar exists (a JDK), or when ecj is not on the class path.
 */
public final class EcjJrtSupport {

	private EcjJrtSupport() {
	}

	/**
	 * @return whether the caches were seeded
	 */
	@SuppressWarnings("unchecked")
	public static boolean install() {
		String javaHome = System.getProperty("java.home");
		if(javaHome==null || new File(javaHome, "lib/jrt-fs.jar").exists()) {
			return false;
		}
		try {
			FileSystem jrt = FileSystems.getFileSystem(URI.create("jrt:/"));
			Path home = new File(javaHome).toPath().toAbsolutePath().normalize();

			Field fileSystems = Class.forName("org.eclipse.jdt.internal.compiler.util.JRTUtil").getDeclaredField("JRT_FILE_SYSTEMS");
			fileSystems.setAccessible(true);
			((Map<Path,FileSystem>)fileSystems.get(null)).putIfAbsent(home, jrt);

			if(!new File(javaHome, "release").exists()) {
				Field releases = Class.forName("org.eclipse.jdt.internal.compiler.util.Jdk").getDeclaredField("pathToRelease");
				releases.setAccessible(true);
				((Map<Path,String>)releases.get(null)).putIfAbsent(home, System.getProperty("java.version"));
			}
			return true;
		} catch(Throwable t) {
			// No jrt file system or no ecj: the compiler reports its own error
			System.err.println("Cannot prepare the Java compiler for this runtime: "+t);
			return false;
		}
	}
}
