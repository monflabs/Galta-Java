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

import java.nio.file.Files;
import java.nio.file.Path;

import org.monflabs.playground.SnippetFactory;
import org.monflabs.playground.SnippetTree;
import org.monflabs.playground.SnippetTree.Node;
import org.monflabs.util.path.FilesUtil;

import tests.ProjectTestCase;

/**
 * The names of the snippets in a URL ({@link Node#getUrlName()}) and their titles.
 */
public class UrlNameTest extends ProjectTestCase {

	public void testSegments() throws Exception {
		assertEquals("getting-started", SnippetTree.urlSegment("01 - Getting Started"));
		assertEquals("hello-galta", SnippetTree.urlSegment("Hello Galta"));
		assertEquals("no-match-returns-empty-array", SnippetTree.urlSegment("15- No match returns empty array"));
		assertEquals("authors-of-all-books-in-the-store", SnippetTree.urlSegment("01- Authors of all books in the store'"));
		assertEquals("math-sumprecise", SnippetTree.urlSegment("04 - Math.sumPrecise()"));
		assertEquals("es2015", SnippetTree.urlSegment("es2015"));
		assertEquals("2024", SnippetTree.urlSegment("2024"));
		assertEquals("Getting Started", SnippetTree.title("01 - Getting Started"));
		assertEquals("Hello Galta", SnippetTree.title("Hello Galta"));
	}

	public void testTree() throws Exception {
		Path root = Files.createTempDirectory("url-names");
		try {
			Files.createDirectories(root.resolve("01 - Getting Started/Hello Galta"));
			Files.createDirectories(root.resolve("_resources/Hidden"));
			Node top = new SnippetTree(new SnippetFactory(root)).getRoot();
			assertEquals("", top.getUrlName());
			assertEquals(1, top.getChildren().length);
			Node folder = top.getChildren()[0];
			assertEquals("getting-started", folder.getUrlName());
			assertEquals("Getting Started", folder.getTitle());
			Node snippet = folder.getChildren()[0];
			assertEquals("getting-started/hello-galta", snippet.getUrlName());
			assertEquals("Hello Galta", snippet.getTitle());
		} finally {
			FilesUtil.deleteRecursively(root);
		}
	}
}
