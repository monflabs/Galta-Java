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
package tests.swing;

import tests.ProjectTestCase;

/**
 * Headless tests of the playground Swing support classes.
 */
public class PlaygroundSwingTest extends ProjectTestCase {

	public void testJdkLibraryInfos() throws Exception {
		java.io.File home = new java.io.File(System.getProperty("java.home"));
		java.util.List<org.fife.rsta.ac.java.buildpath.LibraryInfo> infos = new java.util.ArrayList<>();
		infos.add(new com.monflabs.swing.rtsyntax.JrtLibraryInfo());
		org.fife.rsta.ac.java.buildpath.LibraryInfo main = com.monflabs.swing.rtsyntax.LibraryInfo2.getJreJarInfo(home);
		assertNotNull(main);	// jmods when present, else jrt:/
		infos.add(main);
		for(org.fife.rsta.ac.java.buildpath.LibraryInfo info: infos) {
			assertNotNull(info.createPackageMap());
			assertSame("built once", info.createPackageMap(), info.createPackageMap());
			org.fife.rsta.ac.java.classreader.ClassFile string = info.createClassFile("java/lang/String.class");
			assertNotNull(info.toString(), string);
			assertEquals("java.lang.String", string.getClassName(true));
			assertNotNull(info.createClassFile("java/util/concurrent/ConcurrentHashMap.class"));
			assertNull(info.createClassFile("no/such/Clazz.class"));
		}
	}

}
