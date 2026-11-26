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
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class SnippetTree {

	private static Node[] EMPTY_NODES = new Node[0];

	// The ordering number of a folder: "01 - Getting Started", "15- No match"
	private static final Pattern ORDER_PREFIX = Pattern.compile("^[0-9]+\\s*-\\s*");

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

		/**
		 * The name of the folder, without its ordering number ({@code Getting Started}
		 * for {@code 01 - Getting Started}).
		 */
		public String getTitle() {
			return folder.getFileName()!=null ? title(folder.getFileName().toString()) : "";
		}

		/**
		 * The name of the node in a URL: its path from the root, each folder without its
		 * ordering number, in lower case with hyphens ({@code getting-started/hello-galta}
		 * for {@code 01 - Getting Started/Hello Galta}); empty for the root.
		 */
		public String getUrlName() {
			if(parent==null) {
				return "";
			}
			String parentName = parent.getUrlName();
			String name = urlSegment(folder.getFileName().toString());
			return parentName.isEmpty() ? name : parentName+"/"+name;
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

	/**
	 * A folder name without its ordering number ({@code 01 - Getting Started} gives
	 * {@code Getting Started}).
	 */
	public static String title(String folderName) {
		return ORDER_PREFIX.matcher(folderName).replaceFirst("");
	}

	/**
	 * A folder name as a segment of a URL name: without its ordering number, in lower
	 * case, every run of characters other than {@code [a-z0-9]} replaced by a hyphen,
	 * without leading or trailing hyphens.
	 */
	public static String urlSegment(String folderName) {
		return title(folderName).toLowerCase(Locale.ROOT)
				.replaceAll("[^a-z0-9]+", "-")
				.replaceAll("^-|-$", "");
	}

	public SnippetFactory getFactory() {
		return factory;
	}

	public Node getRoot() {
		return root;
	}
}
