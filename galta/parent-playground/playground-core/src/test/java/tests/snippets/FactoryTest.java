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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import org.monflabs.filesystem.memory.MemoryFileSystem;
import org.monflabs.playground.PlaygroundConfiguration;
import org.monflabs.playground.Snippet;
import org.monflabs.playground.SnippetFactory;
import org.monflabs.playground.SnippetTree;
import org.monflabs.playground.SnippetTree.Node;
import org.monflabs.util.Console;
import org.monflabs.util.TextBuilder;
import org.monflabs.util.path.FilesUtil;

import tests.ProjectTestCase;

public class FactoryTest extends ProjectTestCase {

	public void testSnippets() throws Exception {
		SnippetFactory f = PlaygroundConfiguration.get().getSnippetFactory();
		SnippetTree tree = new SnippetTree(f);
		
		Node root = tree.getRoot();
		
		String hierarchy = addSnippet(new TextBuilder(), root).toString();
		
		support.assertTextResult(hierarchy, "snippets.txt");
		
	}	
	private TextBuilder addSnippet(TextBuilder b, Node node) throws IOException {
		String name = node.getFolder().toString(); 
		b.append(name).nl();
		b.incIndent();
			Snippet s = node.getSnippet();
			MemoryFileSystem fs = s.createSnippetFs();
			Path dir = fs.getPath("/");
			try (Stream<Path> stream = Files.list(dir)) {
			    stream.forEach(System.out::println);
			}
			try (Stream<Path> stream = Files.list(FilesUtil.getRoot(fs))) {
			    stream
					.map( (fd) -> { Console.log(">> "+fd.toString()); return fd.getFileName().toString(); } )
					//.map( (fd) -> fd.getFileName().toString() )
					.sorted()
			    	.forEach( (f) -> {
						b.append(f).append(": ").append(FilesUtil.readString(fs.getPath(f))).nl();
				});
			}
		b.decIndent();
		
		Node[] children = node.getChildren();
		for(int i=0; i<children.length; i++) {
			addSnippet(b,children[i]);
		}
		return b;
	}
	public void testFolderTree() throws Exception {
		SnippetFactory f = PlaygroundConfiguration.get().getSnippetFactory();
		SnippetTree tree = new SnippetTree(f);
		
		Node root = tree.getRoot();
		
		String hierarchy = addTreeHierarchy(new TextBuilder(), root).toString();
		
		support.assertTextResult(hierarchy, "hierarchy.txt");
		
	}	
	private TextBuilder addTreeHierarchy(TextBuilder b, Node node) {
		b.incIndent();
		
		Node[] children = node.getChildren();
		for(int i=0; i<children.length; i++) {
			b.append(children[i].getFolder().getFileName().toString()).nl();
			addTreeHierarchy(b,children[i]);
		}
		
		b.decIndent();
		return b;
	}

}
