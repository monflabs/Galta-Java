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

import static org.junit.Assert.assertThrows;

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;

import org.monflabs.util.FileUtil;
import org.monflabs.util.path.FilesUtil;

import tests.ProjectTestCase;

public class FilesUtilTest extends ProjectTestCase {

	public void testGetFileExtension() throws Exception {
		assertEquals("txt", FilesUtil.getFileExtension(Path.of("/a/b.txt")));
		assertEquals("gz", FilesUtil.getFileExtension(Path.of("a.tar.gz")));
		assertEquals("", FilesUtil.getFileExtension(Path.of("/a/Makefile")));
		assertEquals("", FilesUtil.getFileExtension(Path.of("/a/b.")));
		assertNull(FilesUtil.getFileExtension(null));
		// A root path has no file name: it used to throw a NullPointerException
		assertEquals("", FilesUtil.getFileExtension(Path.of("/")));
		// A dotfile has no extension: ".bashrc" used to be all extension
		assertEquals("", FilesUtil.getFileExtension(Path.of("/x/.bashrc")));
		assertEquals("txt", FilesUtil.getFileExtension(Path.of("/x/.notes.txt")));
	}

	public void testSetExtension() throws Exception {
		assertEquals(Path.of("/a/b.md"), FilesUtil.setExtension(Path.of("/a/b.txt"), "md"));
		assertEquals(Path.of("/a/b.md"), FilesUtil.setExtension(Path.of("/a/b.txt"), ".md"));
		assertEquals(Path.of("/a/Makefile.bak"), FilesUtil.setExtension(Path.of("/a/Makefile"), "bak"));
		assertEquals(Path.of("a.tar.bz2"), FilesUtil.setExtension(Path.of("a.tar.gz"), "bz2"));
		// ".bashrc" used to become ".txt"
		assertEquals(Path.of("/x/.bashrc.txt"), FilesUtil.setExtension(Path.of("/x/.bashrc"), "txt"));
		assertNull(FilesUtil.setExtension(null, "txt"));
		// A root path used to throw a NullPointerException
		assertThrows(IllegalArgumentException.class, () -> FilesUtil.setExtension(Path.of("/"), "txt"));
	}

	public void testDeleteRecursivelyDoesNotFollowLinks() throws Exception {
		Path tmp = Files.createTempDirectory("filesutiltest");
		try {
			Path target = Files.createDirectories(tmp.resolve("target"));
			Path keep = Files.writeString(target.resolve("keep.txt"), "keep");
			Path dir = Files.createDirectories(tmp.resolve("dir/sub"));
			Files.writeString(dir.resolve("f.txt"), "f");
			Files.createSymbolicLink(dir.resolve("link"), target);

			FilesUtil.clearDirectory(tmp.resolve("dir"));
			assertTrue(Files.isDirectory(tmp.resolve("dir")));
			assertEquals(0, tmp.resolve("dir").toFile().list().length);
			assertTrue(Files.exists(keep));

			Path link = Files.createSymbolicLink(tmp.resolve("link2"), target);
			FilesUtil.deleteRecursively(link);
			assertFalse(Files.exists(link, LinkOption.NOFOLLOW_LINKS));
			assertTrue(Files.exists(keep));

			FilesUtil.deleteRecursively(target);
			assertFalse(Files.exists(target));
		} finally {
			FileUtil.deleteFile(tmp.toFile());
		}
	}

	public void testIsRootAndReadWrite() throws Exception {
		assertTrue(FilesUtil.isRoot(Path.of("/")));
		assertFalse(FilesUtil.isRoot(Path.of("/a")));
		assertFalse(FilesUtil.isRoot(Path.of("a")));
		Path f = Files.createTempFile("filesutil", ".txt");
		try {
			Files.writeString(f, "abc");
			assertEquals("abc", FilesUtil.readString(f));
			assertEquals(3, FilesUtil.readAllBytes(f).length);
		} finally {
			Files.delete(f);
		}
		assertThrows(RuntimeException.class, () -> FilesUtil.readString(Path.of("/nonexistent/x.txt")));
	}
}
