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
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import org.monflabs.util.Console;

/**
 * The session state of one snippet: the snippet, the in-memory copy of its
 * files, the console streams and the execution options.
 * <p>
 * The files are read and written in one step ({@link #getContent(String)},
 * {@link #setContent(String, String)}): an execution thread reading a file
 * while the editor replaces it sees the old content or the new one, never a
 * partial one.
 */
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

	/**
	 * The content of a file, or null when there is no such file.
	 */
	public byte[] getBytes(String name) {
		return snippetFs.readBytes(snippetFs.getPath(name));
	}

	/**
	 * The text of a file (UTF-8), or null when there is no such file. A file
	 * that is not valid UTF-8 text ({@link #isTextFile(String)}) is decoded
	 * with replacement characters.
	 */
	public String getContent(String name) {
		byte[] b = getBytes(name);
		return b!=null ? new String(b, StandardCharsets.UTF_8) : null;
	}

	/**
	 * Replaces the text of a file (UTF-8), creating it if needed.
	 */
	public void setContent(String name, String text) {
		Path f = snippetFs.getPath(name);
		snippetFs.writeBytes(f, text.getBytes(StandardCharsets.UTF_8));
	}

	/**
	 * Whether a file holds text that can be edited: valid UTF-8 without NUL
	 * characters. False for a missing file.
	 */
	public boolean isTextFile(String name) {
		byte[] b = getBytes(name);
		return b!=null && isText(b);
	}

	/**
	 * Whether the bytes are text: valid UTF-8, without NUL characters.
	 */
	public static boolean isText(byte[] content) {
		for(byte b: content) {
			if(b==0) {
				return false;
			}
		}
		try {
			StandardCharsets.UTF_8.newDecoder()
				.onMalformedInput(CodingErrorAction.REPORT)
				.onUnmappableCharacter(CodingErrorAction.REPORT)
				.decode(ByteBuffer.wrap(content));
			return true;
		} catch(CharacterCodingException e) {
			return false;
		}
	}

	public PrintStream getConsoleOut() {
		return Console.outStream();
	}

	public PrintStream getConsoleErr() {
		return Console.errStream();
	}

	/**
	 * Prints a message to the console, aligned with a source line when the
	 * console supports it. The default implementation just prints it.
	 *
	 * @param line the 1-based line of the source the message is about
	 */
	public void printlnAtLine(int line, String msg) {
		getConsoleOut().println(msg); // by default
	}


	public abstract String getConsoleText();

	/**
	 * The value of an execution option, or the default value when it is not
	 * set.
	 */
	public Object getExecutionOption(String key, Object defaultValue) {
		return defaultValue;
	}

	/**
	 * The value of an execution option, typed: the default value when it is
	 * not set, or set with another type.
	 */
	public <T> T getExecutionOption(String key, Class<T> type, T defaultValue) {
		Object v = getExecutionOption(key, defaultValue);
		return type.isInstance(v) ? type.cast(v) : defaultValue;
	}

	/**
	 * A boolean execution option.
	 */
	public boolean getBooleanOption(String key, boolean defaultValue) {
		return getExecutionOption(key, Boolean.class, defaultValue);
	}
}
