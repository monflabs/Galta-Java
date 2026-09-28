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

import java.nio.file.FileSystem;
import java.nio.file.Path;

import org.monflabs.util.path.FilesUtil;

public class SnippetFactory {
	
	private Path root;
	
	public SnippetFactory(FileSystem fs) {
		this.root = FilesUtil.getRoot(fs);
	}
	public SnippetFactory(Path root) {
		this.root = root;
	}

	public Path getRoot() {
		return root;
	}
	
	public Snippet getSnippet(String path) {
		Path file = root.resolve(path);
		return getSnippet(file);
	}
	
	public Snippet getSnippet(Path folder) {
		return new Snippet(folder);
	}
}