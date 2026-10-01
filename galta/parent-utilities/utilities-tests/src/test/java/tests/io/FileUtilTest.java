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
package tests.io;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.monflabs.util.FileUtil;
import org.monflabs.util.IOStreamUtil;

import tests.ProjectTestCase;

public class FileUtilTest extends ProjectTestCase {

	private Path tmp;

	@Override
	public void setUp() throws Exception {
		super.setUp();
		tmp = Files.createTempDirectory("fileutiltest");
	}

	@Override
	public void tearDown() throws Exception {
		FileUtil.deleteFile(tmp.toFile());
		super.tearDown();
	}

	// A directory containing a symbolic link to another directory, holding a file to keep
	private Path[] linkSetup() throws Exception {
		Path target = Files.createDirectories(tmp.resolve("target"));
		Path keep = Files.writeString(target.resolve("keep.txt"), "keep");
		Path dir = Files.createDirectories(tmp.resolve("todelete"));
		Files.writeString(dir.resolve("f.txt"), "x");
		Files.createSymbolicLink(dir.resolve("link"), target);
		return new Path[] {dir, target, keep};
	}

	public void testDeleteFileDoesNotFollowLinks() throws Exception {
		Path[] p = linkSetup();
		assertTrue(FileUtil.deleteFile(p[0].toFile()));
		assertFalse(Files.exists(p[0]));
		// The content of the link target used to be deleted too
		assertTrue(Files.exists(p[2]));
	}

	public void testEmptyDirectoryDoesNotFollowLinks() throws Exception {
		Path[] p = linkSetup();
		assertTrue(FileUtil.emptyDirectory(p[0].toFile()));
		assertTrue(Files.isDirectory(p[0]));
		assertEquals(0, p[0].toFile().list().length);   // the link itself is removed
		assertTrue(Files.exists(p[2]));
	}

	public void testPrepareDirectoryDoesNotFollowLinks() throws Exception {
		Path[] p = linkSetup();
		FileUtil.prepareDirectory(p[0].toFile(), true);
		assertTrue(Files.isDirectory(p[0]));
		assertEquals(0, p[0].toFile().list().length);
		assertTrue(Files.exists(p[2]));
	}

	@SuppressWarnings("deprecation")
	public void testPrepareEmptyDirectory() throws Exception {
		File dir = Files.createTempDirectory("prep").toFile();
		try {
			File sub = new File(dir, "sub");
			FileUtil.prepareEmptyDirectory(sub);
			assertTrue(sub.isDirectory());
			Files.writeString(new File(sub, "a.txt").toPath(), "a");
			FileUtil.prepareDirectory(sub, false);           // kept
			assertEquals(1, sub.list().length);
			FileUtil.prepareEmptyDirectory(sub);             // emptied
			assertEquals(0, sub.list().length);
			Files.writeString(new File(sub, "b.txt").toPath(), "b");
			FileUtil.prepareDirectory(sub);                  // deprecated: still clears
			assertEquals(0, sub.list().length);
		} finally {
			FileUtil.deleteFile(dir);
		}
	}

	public void testDeleteSymbolicLinkItself() throws Exception {
		Path[] p = linkSetup();
		Path link = p[0].resolve("link");
		assertTrue(FileUtil.deleteFile(link.toFile()));
		assertFalse(Files.exists(link, java.nio.file.LinkOption.NOFOLLOW_LINKS));
		assertTrue(Files.exists(p[2]));
	}

	public void testDeleteMissingAndEmptyNonDirectory() throws Exception {
		assertFalse(FileUtil.deleteFile(tmp.resolve("missing").toFile()));
		File f = Files.writeString(tmp.resolve("a.txt"), "a").toFile();
		assertFalse(FileUtil.emptyDirectory(f));   // not a directory
		assertTrue(f.exists());
	}

	public void testNestedDelete() throws Exception {
		Path deep = Files.createDirectories(tmp.resolve("a/b/c"));
		Files.writeString(deep.resolve("x.txt"), "x");
		assertTrue(FileUtil.deleteFile(tmp.resolve("a").toFile()));
		assertFalse(Files.exists(tmp.resolve("a")));
	}

	public void testDefaultCharsetIsUtf8() throws Exception {
		File f = tmp.resolve("utf8.txt").toFile();
		FileUtil.setContent(f, "café €");
		assertEquals("café €", Files.readString(f.toPath(), StandardCharsets.UTF_8));
		assertEquals("café €", FileUtil.readContent(f));
	}

	public void testCopy() throws Exception {
		File src = Files.writeString(tmp.resolve("src.txt"), "content").toFile();
		File tgt = tmp.resolve("tgt.txt").toFile();
		FileUtil.copy(src, tgt);
		assertEquals("content", FileUtil.readContent(tgt));
	}

	public void testIOStreamUtilDefaultCharsetIsUtf8() throws Exception {
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		IOStreamUtil.setContent(bos, "été");
		assertEquals("été", new String(bos.toByteArray(), StandardCharsets.UTF_8));
		assertEquals("été", IOStreamUtil.readContent(new ByteArrayInputStream(bos.toByteArray())));
	}
}
