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
package com.monflabs.swing.rtsyntax;

import java.io.File;
import java.nio.file.Path;

import org.fife.rsta.ac.java.buildpath.LibraryInfo;
import org.fife.rsta.ac.java.buildpath.ZipSourceLocation;

/**
 * Locates the Java runtime classes for the Java code completion - a Java 9+
 * runtime: its jmods when the JDK has them, else (for the running JVM only)
 * its module image through jrt:/.
 */
public class LibraryInfo2 {

	/**
	 * The runtime classes of the running JVM, or null if they cannot be located.
	 */
	public static LibraryInfo getMainJreJarInfo() {
		String javaHome = System.getProperty("java.home");
		return getJreJarInfo(new File(javaHome));
	}

	/**
	 * The runtime classes of a JDK, or null if they cannot be located.
	 */
	public static LibraryInfo getJreJarInfo(File jreHome) {
		LibraryInfo info = null;
		File mods = new File(jreHome,"jmods");
		if(mods.isDirectory()) {
			File[] files = mods.listFiles(f -> {
				String name = f.getName();
				return f.isFile() && name.endsWith(".jmod") && (name.startsWith("java.") || name.startsWith("jdk."));
			});
			if(files!=null && files.length>0) {
				info = new Jdk9LibraryInfo(files);
			}
		}
		if(info==null && isRunningJvm(jreHome)) {
			info = new JrtLibraryInfo();
		}
		if(info!=null) {
			File sourceZip = new File(jreHome,"lib"+File.separator+"src.zip");
			if (sourceZip.isFile()) {
				info.setSourceLocation(new ZipSourceLocation(sourceZip));
			}
		}
		return info;
	}

	private static boolean isRunningJvm(File jreHome) {
		Path home = Path.of(System.getProperty("java.home")).toAbsolutePath().normalize();
		return home.equals(jreHome.toPath().toAbsolutePath().normalize());
	}
}
