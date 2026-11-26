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

import java.io.File;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import org.monflabs.playground.SnippetFactory;
import org.monflabs.playground.SnippetTree;
import org.monflabs.playground.SnippetTree.Node;

import tests.ProjectTestCase;

/**
 * Every snippet packaged with the playground has a unique, well-formed name in a URL
 * ({@link Node#getUrlName()}): the browser playground opens it from {@code ?snippet=NAME}.
 */
public class SnippetUrlNamesTest extends ProjectTestCase {

	public void testUniqueNames() throws Exception {
		Path root = new File(support.getProjectRoot(), "src/main/resources/snippets").toPath();
		Map<String,Path> names = new HashMap<>();
		collect(new SnippetTree(new SnippetFactory(root)).getRoot(), names);
		assertTrue(names.size()+" snippets", names.size()>20);
		assertTrue("getting-started/hello-galta", names.containsKey("getting-started/hello-galta"));
	}

	private static void collect(Node node, Map<String,Path> names) {
		for(Node child: node.getChildren()) {
			String name = child.getUrlName();
			assertTrue(name, name.matches("[a-z0-9-]+(/[a-z0-9-]+)*"));
			Path other = names.put(name, child.getFolder());
			assertNull(name+": "+other+" and "+child.getFolder(), other);
			collect(child, names);
		}
	}
}
