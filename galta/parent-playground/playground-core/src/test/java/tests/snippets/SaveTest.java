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
import java.util.stream.Stream;

import org.monflabs.playground.MemoryFileSystemSnippet;
import org.monflabs.playground.Snippet;
import org.monflabs.util.path.FilesUtil;

import junit.framework.TestCase;

/**
 * Reproduces PlaygroundFrame.saveSnippet()'s own logic directly against
 * {@link MemoryFileSystemSnippet}, without any Swing UI: list the snippet's
 * in-memory files, resolve each back to its physical file, and write out
 * changed content. This is the exact mechanism that silently failed to save
 * anything - {@link Snippet#createSnippetFs()} registers the physical-file
 * mapping under a relative key ("main.js"), while enumerating the snippet
 * filesystem's root (as the real save code does) yields absolute paths
 * ("/main.js"); a plain string-keyed map lookup then always missed.
 */
public class SaveTest extends TestCase {

	public void testSavedContentReachesThePhysicalFile() throws Exception {
		Path folder = Files.createTempDirectory("snippet-save-test");
		Path mainFile = folder.resolve("main.js");
		Files.writeString(mainFile, "1;\n", StandardCharsets.UTF_8);

		Snippet snippet = new Snippet(folder);
		MemoryFileSystemSnippet fs = snippet.createSnippetFs();

		// Simulate an edit in the playground's editor.
		Path inMemoryFile = fs.getPath("main.js");
		FilesUtil.writeString(inMemoryFile, "2;\n", StandardCharsets.UTF_8);

		// PlaygroundFrame.saveSnippet()'s own logic: list the snippet fs's
		// root (yields absolute paths), resolve each to its physical file,
		// and write out anything that changed.
		try (Stream<Path> stream = Files.list(FilesUtil.getRoot(fs))) {
			stream.filter(Files::isRegularFile).forEach(this::saveFile);
		}

		assertEquals("2;\n", Files.readString(mainFile, StandardCharsets.UTF_8));
	}

	private void saveFile(Path inMemoryFile) {
		MemoryFileSystemSnippet fs = (MemoryFileSystemSnippet) inMemoryFile.getFileSystem();
		Path physicalFile = fs.getPhysicalFile(inMemoryFile);
		assertNotNull("getPhysicalFile() must resolve " + inMemoryFile + " back to its real file", physicalFile);
		try {
			String newContent = FilesUtil.readString(inMemoryFile, StandardCharsets.UTF_8);
			Files.writeString(physicalFile, newContent, StandardCharsets.UTF_8);
		} catch (final Exception e) {
			throw new RuntimeException(e);
		}
	}
}
