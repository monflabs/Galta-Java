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
package org.monflabs.playground;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.stream.Stream;

public class SnippetTree {

	private static Node[] EMPTY_NODES = new Node[0];

	public class Node {

		private Node parent;
		private Path folder;
		private Node[] children;

		private Node(Node parent, Path folder) {
			this.parent = parent;
			this.folder = folder;
		}

		public Node getParent() {
			return parent;
		}

		public Path getFolder() {
			return folder;
		}

		public boolean isLeaf() {
			return getChildren().length==0;
		}
		
		public Snippet getSnippet() {
			return factory.getSnippet(folder);
		}

		public Node[] getChildren() {
			if (children == null) {
				try (Stream<Path> stream = Files.list(folder)) {
				    children = stream
				    	.filter(Files::isDirectory)
				    	.filter( (p) -> Snippet.isVisible(p))
				    	.map( (p) -> {
				    		return new Node(this,p);
				    	})
				    	.toArray(Node[]::new);
					Arrays.sort(children, (n1, n2) -> {
						if(n1.isLeaf()!=n2.isLeaf()) {
							return n1.isLeaf() ? -1 : 1;
						}
						return n1.folder.getFileName().toString().compareTo(n2.folder.getFileName().toString());
					});
				} catch(Exception e) {
					children = EMPTY_NODES;
				}
			}
			return children;
		}
	}

	private SnippetFactory factory;
	private Node root;

	public SnippetTree(SnippetFactory factory) {
		this.factory = factory;
		this.root = new Node(null, factory.getRoot());
	}

	public SnippetFactory getFactory() {
		return factory;
	}

	public Node getRoot() {
		return root;
	}
}
