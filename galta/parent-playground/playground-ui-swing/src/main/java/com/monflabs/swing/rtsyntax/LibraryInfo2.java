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
import java.io.FileFilter;

import org.fife.rsta.ac.java.buildpath.JarLibraryInfo;
import org.fife.rsta.ac.java.buildpath.LibraryInfo;
import org.fife.rsta.ac.java.buildpath.ZipSourceLocation;

public class LibraryInfo2 {
	
	/**
	 * The runtime classes of the running JVM, or null if they cannot be located.
	 */
	public static LibraryInfo getMainJreJarInfo() {
		String javaHome = System.getProperty("java.home");
		return getJreJarInfo(new File(javaHome));
	}
	
	public static LibraryInfo getJreJarInfo(File jreHome) {
		// Check if the Jre is made of modules
		File mods = new File(jreHome,"jmods");
		if(mods.isDirectory()) {
			File[] files = mods.listFiles( new FileFilter() {
				@Override
				public boolean accept(File pathname) {
					if(pathname.isFile()) {
						String name = pathname.getName();
						return name.endsWith(".jmod") && (name.startsWith("java.") || name.startsWith("jdk."));
					}
					return false;
				}
			});

			if(files==null || files.length==0) {
				return null;
			}
			LibraryInfo info = new Jdk9LibraryInfo(files);
			File sourceZip = new File(jreHome,"lib"+File.separator+"src.zip");
			if (sourceZip.isFile()) { // Make sure our last guess actually exists
				info.setSourceLocation(new ZipSourceLocation(sourceZip));
			}

			return info;
		}

		LibraryInfo info = null;

		File mainJar = new File(jreHome, "lib/rt.jar"); // Sun JRE's
		File sourceZip;

		if (mainJar.isFile()) { // Sun JRE's
			sourceZip = new File(jreHome, "src.zip");
			if (!sourceZip.isFile()) {
				// Might be a JRE inside a JDK
				sourceZip = new File(jreHome, "../src.zip");
			}
		}

		else { // Might be OS X
			mainJar = new File(jreHome, "../Classes/classes.jar");
			// ${java.home}/src.jar is the common location on OS X.
			sourceZip = new File(jreHome, "src.jar");
		}

		if (mainJar.isFile()) {
			info = new JarLibraryInfo(mainJar);
			if (sourceZip.isFile()) { // Make sure our last guess actually exists
				info.setSourceLocation(new ZipSourceLocation(sourceZip));
			}
		}
		else {
			System.err.println("[ERROR]: Cannot locate JRE jar in " +
								jreHome.getAbsolutePath());
			mainJar = null;
		}

		return info;

	}
}

