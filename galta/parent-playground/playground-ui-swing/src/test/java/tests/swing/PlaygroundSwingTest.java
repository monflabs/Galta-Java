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

	public void testLoadedTextIsNotUndoable() throws Exception {
		javax.swing.SwingUtilities.invokeAndWait(() -> {
			org.monflabs.ui.swing.ide.syntax.SyntaxTextArea ta = new org.monflabs.ui.swing.ide.syntax.SyntaxTextArea();
			com.monflabs.playground.swing.PlaygroundFrame.setInitialText(ta, "let a = 1;\n");
			// undo right after loading used to empty the editor
			assertFalse(ta.canUndo());
			assertEquals(0, ta.getCaretPosition());
			ta.insert("x", 0);
			assertTrue(ta.canUndo());
			ta.undoLastAction();
			assertEquals("let a = 1;\n", ta.getText());
			assertFalse(ta.canUndo());
		});
	}

	public void testMarkdownTablesAndBase() throws Exception {
		java.nio.file.Path folder = java.nio.file.Files.createTempDirectory("md-base");
		javax.swing.SwingUtilities.invokeAndWait(() -> {
			com.monflabs.swing.components.MarkdownRenderer md = new com.monflabs.swing.components.MarkdownRenderer();
			md.setBaseFolder(folder);
			assertNotNull(md.getBase());
			md.setMarkdown("| a | b |\n|---|---|\n| 1 | 2 |\n\n![img](pic.png) [link](https://example.com)");
			String html = md.getText().toLowerCase(java.util.Locale.ROOT);
			assertTrue(html, html.contains("<table"));
			assertTrue(html, html.contains("<td"));
			javax.swing.text.html.HTMLDocument doc = (javax.swing.text.html.HTMLDocument)md.getDocument();
			assertEquals("relative URLs resolve against the folder", md.getBase(), doc.getBase());
			// a base outside of the default file system is ignored
			md.setBaseFolder(null);
			assertNull(md.getBase());
		});
	}

	public void testLibraryInfoOrderIsAntisymmetric() throws Exception {
		java.io.File home = new java.io.File(System.getProperty("java.home"));
		org.fife.rsta.ac.java.buildpath.LibraryInfo jrt = new com.monflabs.swing.rtsyntax.JrtLibraryInfo();
		org.fife.rsta.ac.java.buildpath.LibraryInfo main = com.monflabs.swing.rtsyntax.LibraryInfo2.getJreJarInfo(home);
		org.fife.rsta.ac.java.buildpath.LibraryInfo jar = new org.fife.rsta.ac.java.buildpath.JarLibraryInfo(new java.io.File(org.fife.rsta.ac.java.buildpath.JarLibraryInfo.class.getProtectionDomain().getCodeSource().getLocation().toURI()));
		java.util.List<org.fife.rsta.ac.java.buildpath.LibraryInfo> all = java.util.List.of(jrt, main, jar);
		for(var a: all) {
			assertEquals(0, a.compareTo(a));
			assertEquals(a, a.clone());
			for(var b: all) {
				// both -1 used to break sorting and equals()
				assertEquals(a+" / "+b, Integer.signum(a.compareTo(b)), -Integer.signum(b.compareTo(a)));
			}
		}
		assertEquals(jrt, new com.monflabs.swing.rtsyntax.JrtLibraryInfo());
		java.util.List<org.fife.rsta.ac.java.buildpath.LibraryInfo> sorted = new java.util.ArrayList<>(all);
		java.util.Collections.sort(sorted);
		assertEquals(3, sorted.size());
	}

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
