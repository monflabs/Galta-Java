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

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.monflabs.util.Console;
import org.monflabs.util.path.FilesUtil;

public abstract class ExecutionContext {
	
	private Snippet snippet;
	private MemoryFileSystemSnippet snippetFs;
	
	public ExecutionContext(Snippet snippet) {
		this.snippet = snippet;
		this.snippetFs = snippet.createSnippetFs();
	}
	
	public boolean isLogStatements() {
		return false;
	}

	public Snippet getSnippet() {
		return snippet;
	}
	
	public MemoryFileSystemSnippet getSnippetFs() {
		return snippetFs;
	}
	
	// synchronized with setContent(): the editor writes while the execution
	// thread reads, and a read must never see a partially written file
	public synchronized String getContent(String name) {
		Path f = snippetFs.getPath(name);
		if(Files.isRegularFile(f)) {
			return FilesUtil.readString(f,StandardCharsets.UTF_8);
		}
		return null;
	}
	
	public synchronized void setContent(String name, String text) {
		Path f = snippetFs.getPath(name);
		FilesUtil.writeString(f,text,StandardCharsets.UTF_8);
	}
	
	public PrintStream getConsoleOut() {
		return Console.outStream();
	}
	
	public PrintStream getConsoleErr() {
		return Console.errStream();
	}
	
	public void printlnAtLine(int line, String msg) {
		getConsoleOut().println(msg); // by default
	}

	
	public abstract String getConsoleText();
	
	public Object getExecutionOption(String key, Object defaultValue) {
		return defaultValue;
	}
}
