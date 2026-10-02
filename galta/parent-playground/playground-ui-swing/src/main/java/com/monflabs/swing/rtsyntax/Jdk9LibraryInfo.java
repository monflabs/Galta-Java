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
 *
 * Derived from the RSTALanguageSupport library of RSyntaxTextArea (its library
 * information classes), Copyright (c) 2021, Robert Futrell. All rights reserved.
 * Distributed under the BSD 3-Clause License
 * (see META-INF/licenses/RSyntaxTextArea-BSD-3-Clause.txt):
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *   * Redistributions of source code must retain the above copyright notice,
 *     this list of conditions and the following disclaimer.
 *   * Redistributions in binary form must reproduce the above copyright notice,
 *     this list of conditions and the following disclaimer in the documentation
 *     and/or other materials provided with the distribution.
 *   * Neither the name of the author nor the names of its contributors may be
 *     used to endorse or promote products derived from this software without
 *     specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.monflabs.swing.rtsyntax;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.fife.rsta.ac.java.PackageMapNode;
import org.fife.rsta.ac.java.buildpath.ClasspathLibraryInfo;
import org.fife.rsta.ac.java.buildpath.DirLibraryInfo;
import org.fife.rsta.ac.java.buildpath.JarLibraryInfo;
import org.fife.rsta.ac.java.buildpath.LibraryInfo;
import org.fife.rsta.ac.java.buildpath.SourceLocation;
import org.fife.rsta.ac.java.classreader.ClassFile;


/**
 * Information about the JDK 9+ runtime classes to add to the "build path".
 *
 * Note: this introspects the modules delivered as part of the JDK, in jmods.
 * A more complete implementation could look into the ct.sym file and rely on
 * a JDK version to find the proper signatures:
 *   https://www.morling.dev/blog/the-anatomy-of-ct-sym-how-javac-ensures-backwards-compatibility/
 *
 * The jmod files are opened once and stay open, and the package of each class
 * is indexed to its jmod when the package map is built: a class lookup reads
 * one entry of one already open jmod, instead of opening and closing every
 * jmod (~70 of them) in turn.
 *
 * @author Robert Futrell
 * @author Philippe Riand
 * @version 1.0
 * @see DirLibraryInfo
 * @see ClasspathLibraryInfo
 * @see JarLibraryInfo
 */
public class Jdk9LibraryInfo extends LibraryInfo {

	/**
	 * The open jmods and the package index - shared by the clones of this
	 * library info (LibraryInfo is Cloneable), and guarded by itself.
	 */
	private static final class Jmods {
		final File[] files;
		final Map<File,JarFile> open = new HashMap<>();
		Map<String,File> packageIndex;	// "java/lang" -> java.base.jmod
		PackageMapNode packageMap;

		Jmods(File[] files) {
			this.files = files;
		}

		JarFile open(File file) throws IOException {
			JarFile jar = open.get(file);
			if(jar==null) {
				jar = new JarFile(file);
				open.put(file, jar);
			}
			return jar;
		}

		void index() throws IOException {
			if(packageIndex!=null) {
				return;
			}
			PackageMapNode root = new PackageMapNode();
			Map<String,File> index = new HashMap<>();
			for(File file: files) {
				JarFile jar = open(file);
				Enumeration<JarEntry> e = jar.entries();
				while (e.hasMoreElements()) {
					String entryName = e.nextElement().getName();
					if(entryName.startsWith("classes/") && entryName.endsWith(".class")) {
						entryName = entryName.substring(8);
						root.add(entryName);
						index.putIfAbsent(packageOf(entryName), file);
					}
				}
			}
			packageMap = root;
			packageIndex = index;
		}
	}

	private final Jmods jmods;

	public Jdk9LibraryInfo(File[] jmodFiles) {
		this(jmodFiles, null);
	}


	public Jdk9LibraryInfo(File[] jmodFiles, SourceLocation sourceLoc) {
		checkJmodFiles(jmodFiles);
		this.jmods = new Jmods(jmodFiles.clone());
		setSourceLocation(sourceLoc);
	}

	static String packageOf(String entryName) {
		int slash = entryName.lastIndexOf('/');
		return slash>0 ? entryName.substring(0, slash) : "";
	}


	// The jmods stay open: nothing to open or close per bulk
	@Override
	public void bulkClassFileCreationEnd() {
	}


	@Override
	public void bulkClassFileCreationStart() {
	}


	/**
	 * Compares this <code>LibraryInfo</code> to another one.  Two instances of
	 * this class are only considered equal if they represent the same class
	 * file location.  Source attachment is irrelevant.
	 *
	 * @return The sort order of these two library infos.
	 */
	@Override
	public int compareTo(LibraryInfo info) {
		if (info==this) {
			return 0;
		}
		if (info instanceof Jdk9LibraryInfo other && other.jmods==jmods) {
			return 0;	// a clone
		}
		// A total order (it used to answer -1 both ways): by kind, then by
		// the jmod files - the same files are the same library
		return LibraryInfo2.compare(this, info);
	}

	/**
	 * The jmod files, as a string: what identifies this library.
	 */
	String jmodsKey() {
		return java.util.Arrays.toString(jmods.files);
	}


	@Override
	public ClassFile createClassFile(String entryName) throws IOException {
		synchronized(jmods) {
			jmods.index();
			File file = jmods.packageIndex.get(packageOf(entryName));
			if(file!=null) {
				ClassFile c = createClassFileImpl(jmods.open(file), entryName);
				if(c!=null) {
					return c;
				}
			}
		}
		return null;
	}


	@Override
	public ClassFile createClassFileBulk(String entryName) throws IOException {
		return createClassFile(entryName);
	}


	private static ClassFile createClassFileImpl(JarFile jar,
			String entryName) throws IOException {
		JarEntry entry = (JarEntry)jar.getEntry("classes/"+entryName);
		if (entry==null) {
			return null;
		}
		try (DataInputStream in = new DataInputStream(
				new BufferedInputStream(jar.getInputStream(entry)))) {
			return new ClassFile(in);
		}
	}


	/**
	 * The package map, built once (it is the costly part: every entry of every
	 * jmod) and then shared.
	 */
	@Override
	public PackageMapNode createPackageMap() throws IOException {
		synchronized(jmods) {
			jmods.index();
			return jmods.packageMap;
		}
	}


	@Override
	public long getLastModified() {
		return 0;
	}


	@Override
	public String getLocationAsString() {
		return "";
	}


	@Override
	public int hashCodeImpl() {
		int h = 0;
		for( File jarFile: jmods.files) {
			h += jarFile.hashCode();
		}
		return h;
	}


	private static void checkJmodFiles(File[] jmodFiles) {
		for( File jarFile: jmodFiles) {
			if (jarFile==null || !jarFile.exists()) {
				String name = jarFile==null ? "null" : jarFile.getAbsolutePath();
				throw new IllegalArgumentException("Jar does not exist: " + name);
			}
		}
	}


	/**
	 * Returns a string representation of this jar information.  Useful for
	 * debugging.
	 *
	 * @return A string representation of this object.
	 */
	@Override
	public String toString() {
		return "[Jdk9LibraryInfo: " +
			"jmods=" + java.util.Arrays.toString(jmods.files) +
			"; source=" + getSourceLocation() +
			"]";
	}
}
