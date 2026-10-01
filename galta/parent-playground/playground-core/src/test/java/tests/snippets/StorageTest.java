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
package tests.snippets;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import org.monflabs.playground.ExecutionContext;
import org.monflabs.playground.Snippet;
import org.monflabs.playground.SnippetStorage;
import org.monflabs.playground.SnippetStorage.SaveResult;
import org.monflabs.util.path.FilesUtil;

import junit.framework.TestCase;

/**
 * Loading and saving the files of a snippet: binary files, hidden files,
 * atomic and conflict-checked saves, line breaks.
 */
public class StorageTest extends TestCase {

	static class Context extends ExecutionContext {
		Context(Path folder) {
			super(new Snippet(folder));
		}
		@Override
		public String getConsoleText() {
			return "";
		}
		@Override
		public Object getExecutionOption(String key, Object defaultValue) {
			return switch(key) {
				case "flag" -> Boolean.TRUE;
				case "text" -> "yes";
				default -> defaultValue;
			};
		}
	}

	private static final byte[] BINARY = { 0x00, (byte)0xff, (byte)0xfe, 0x01, (byte)0xc3 };

	private Path folder;

	@Override
	protected void setUp() throws Exception {
		super.setUp();
		folder = Files.createTempDirectory("snippet-storage");
		Files.writeString(folder.resolve("main.js"), "1;\n", StandardCharsets.UTF_8);
	}

	private static List<String> names(Path dir) throws Exception {
		try(Stream<Path> s = Files.list(dir)) {
			return s.map(p -> p.getFileName().toString()).sorted().toList();
		}
	}

	public void testHiddenFilesAreNotLoaded() throws Exception {
		Files.writeString(folder.resolve(".DS_Store"), "x");
		Files.writeString(folder.resolve(".gitattributes"), "x");
		Files.writeString(folder.resolve("_res.md"), "x");
		Context ctx = new Context(folder);
		assertEquals(List.of("main.js"), names(FilesUtil.getRoot(ctx.getSnippetFs())));
	}

	public void testBinaryFiles() throws Exception {
		Files.write(folder.resolve("sample.bin"), BINARY);
		Context ctx = new Context(folder);
		assertTrue(ctx.isTextFile("main.js"));
		assertFalse(ctx.isTextFile("sample.bin"));
		assertFalse(ctx.isTextFile("missing.txt"));
		// reading it as text does not throw
		assertNotNull(ctx.getContent("sample.bin"));
		assertTrue(ExecutionContext.isText("héllo".getBytes(StandardCharsets.UTF_8)));
		assertFalse("NUL", ExecutionContext.isText(new byte[] { 'a', 0, 'b' }));

		// saving skips the binary file, and never corrupts it
		ctx.setContent("main.js", "2;\n");
		SaveResult r = new SnippetStorage(ctx).save(false);
		assertEquals(List.of(folder.resolve("main.js")), r.getSaved());
		assertEquals(1, r.getSkippedBinary().size());
		assertTrue(java.util.Arrays.equals(BINARY, Files.readAllBytes(folder.resolve("sample.bin"))));
	}

	public void testSaveOnlyWritesTheChangedFiles() throws Exception {
		Files.writeString(folder.resolve("data.json"), "{}\n");
		Context ctx = new Context(folder);
		SaveResult r = new SnippetStorage(ctx).save(false);
		assertTrue(r.getSaved().isEmpty());
		ctx.setContent("data.json", "{\"a\":1}\n");
		r = new SnippetStorage(ctx).save(false);
		assertEquals(List.of(folder.resolve("data.json")), r.getSaved());
		assertEquals("{\"a\":1}\n", Files.readString(folder.resolve("data.json")));
		// no temporary file left behind
		assertEquals(List.of("data.json", "main.js"), names(folder));
	}

	public void testExternalModificationIsAConflict() throws Exception {
		Context ctx = new Context(folder);
		ctx.setContent("main.js", "mine;\n");
		// someone else changes the file meanwhile
		Files.writeString(folder.resolve("main.js"), "theirs;\n");
		SaveResult r = new SnippetStorage(ctx).save(false);
		assertTrue(r.hasConflicts());
		assertEquals(List.of(folder.resolve("main.js")), r.getConflicts());
		assertTrue(r.getSaved().isEmpty());
		assertEquals("nothing written", "theirs;\n", Files.readString(folder.resolve("main.js")));
		// forced
		r = new SnippetStorage(ctx).save(true);
		assertFalse(r.hasConflicts());
		assertEquals("mine;\n", Files.readString(folder.resolve("main.js")));
		// the saved content is the new reference: no conflict anymore
		ctx.setContent("main.js", "mine2;\n");
		r = new SnippetStorage(ctx).save(false);
		assertFalse(r.hasConflicts());
		assertEquals("mine2;\n", Files.readString(folder.resolve("main.js")));
	}

	public void testCrlfIsKept() throws Exception {
		Files.writeString(folder.resolve("win.js"), "a;\r\nb;\r\n");
		Context ctx = new Context(folder);
		// the editor works with \n
		ctx.setContent("win.js", "a;\nb;\nc;\n");
		ctx.setContent("main.js", "x;\r\ny;\n");
		new SnippetStorage(ctx).save(false);
		assertEquals("a;\r\nb;\r\nc;\r\n", Files.readString(folder.resolve("win.js")));
		// an LF file stays LF
		assertEquals("x;\ny;\n", Files.readString(folder.resolve("main.js")));
	}

	public void testNewFileIsSavedInTheFolder() throws Exception {
		Context ctx = new Context(folder);
		ctx.setContent("README.md", "## Doc");
		SaveResult r = new SnippetStorage(ctx).save(false);
		assertEquals(List.of(folder.resolve("README.md")), r.getSaved());
		assertEquals("## Doc", Files.readString(folder.resolve("README.md")));
	}

	public void testReadOnlyFolderIsReported() throws Exception {
		Path zip = folder.resolve("s.zip");
		try(var zfs = java.nio.file.FileSystems.newFileSystem(zip, java.util.Map.of("create", "true"))) {
			Files.writeString(zfs.getPath("/main.js"), "1;");
		}
		try(var zfs = java.nio.file.FileSystems.newFileSystem(zip, java.util.Map.of("accessMode", "readOnly"))) {
			Path root = zfs.getPath("/");
			if(!zfs.isReadOnly()) {
				return;	// this JDK's zip provider ignores the mode
			}
			Context ctx = new Context(root);
			ctx.setContent("main.js", "2;");
			try {
				new SnippetStorage(ctx).save(false);
				fail("a read-only folder must not report a successful save");
			} catch(java.io.IOException e) {
				assertTrue(e.getMessage(), e.getMessage().contains("read-only"));
			}
		}
	}

	public void testSetContentIsAtomicForReaders() throws Exception {
		Context ctx = new Context(folder);
		String a = "a".repeat(200_000);
		String b = "b".repeat(100_000);
		ctx.setContent("big.txt", a);
		AtomicBoolean stop = new AtomicBoolean();
		AtomicReference<String> bad = new AtomicReference<>();
		Thread reader = new Thread(() -> {
			while(!stop.get()) {
				String s = ctx.getContent("big.txt");
				if(!s.equals(a) && !s.equals(b)) {
					bad.set("partial content: "+s.length());
				}
				byte[] raw = ctx.getBytes("big.txt");
				if(raw.length!=a.length() && raw.length!=b.length()) {
					bad.set("partial bytes: "+raw.length);
				}
			}
		});
		reader.start();
		for(int i=0; i<300; i++) {
			ctx.setContent("big.txt", (i%2==0) ? b : a);
		}
		stop.set(true);
		reader.join();
		assertNull(bad.get(), bad.get());
	}

	public void testTypedOptions() throws Exception {
		Context ctx = new Context(folder);
		assertTrue(ctx.getBooleanOption("flag", false));
		assertFalse(ctx.getBooleanOption("missing", false));
		// another type: the default
		assertFalse(ctx.getBooleanOption("text", false));
		assertEquals("yes", ctx.getExecutionOption("text", String.class, "no"));
		assertEquals(Integer.valueOf(3), ctx.getExecutionOption("flag", Integer.class, 3));
	}
}
