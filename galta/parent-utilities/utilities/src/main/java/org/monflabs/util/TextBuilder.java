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
package org.monflabs.util;

/**
 * 
 */ 
public class TextBuilder implements CharSequence {
	
	private static final int DEFAULT_SIZE = 512;
	
	private static final String INDENT_SPACES = "  ";
	
	private StringBuilder b;
	private int indent;
	private boolean nl = true;
	private int currentLine = 1;
	// The last character appended was a '\r', already turned into a line break: a '\n'
	// right after it is part of the same "\r\n" line break
	private boolean afterCR;
	
	public TextBuilder() {
		this(DEFAULT_SIZE);
	}
	public TextBuilder(int bufferSize) {
		b = new StringBuilder(bufferSize);
	}
	
	public TextBuilder(String content) {
		this(Math.max(content.length()*2, DEFAULT_SIZE ));
		// Through append() so the line count and the "at start of line" state are tracked
		append(content);
	}
	
	public TextBuilder(String content, Object...params) {
		this();
		append(StringFormat.format(content, params));
	}

	@Override
	public String toString() {
		return b.toString();
	}
	
	public int getCurrentLine() {
		return currentLine;
	}
	
	public TextBuilder clear() {
		b = new StringBuilder();
		indent = 0;
		nl = true;
		currentLine = 1;
		afterCR = false;
		return this;
	}
	
	public int getIndent() {
		return indent;
	}
	public void setIndent(int indent) {
		this.indent = indent;
	}
	
	public TextBuilder incIndent() {
		indent++;
		return this;
	}
	public TextBuilder decIndent() {
		indent--;
		return this;
	}
	
	/**
	 * Appends a character, indenting it if it starts a line. The line breaks are
	 * normalized to '\n': "\r\n" and a lone '\r' are both one line break.
	 */
	public TextBuilder append(char c) {
		if(c=='\r') {
			afterCR = false;
			append('\n');
			afterCR = true;
			return this;
		}
		if(afterCR) {
			afterCR = false;
			if(c=='\n') {
				return this;
			}
		}
		if(c=='\n') {
			nl = true;
			currentLine++;
		} else {
			if(nl) {
				for(int i=0; i<indent; i++) {
					b.append(INDENT_SPACES);
				}
				nl = false;
			}
		}
		b.append(c);
		return this;
	}
	
	public TextBuilder append(CharSequence seq) {
		// The runs between line breaks are appended in bulk (it used to be char by char)
		int length = seq.length();
		int start = 0;
		for(int i=0; i<length; i++) {
			char c = seq.charAt(i);
			if(c=='\n' || c=='\r') {
				appendRun(seq, start, i);
				append(c);
				start = i+1;
			}
		}
		appendRun(seq, start, length);
		return this;
	}

	// A run without line breaks
	private void appendRun(CharSequence seq, int start, int end) {
		if(start<end) {
			if(nl) {
				for(int i=0; i<indent; i++) {
					b.append(INDENT_SPACES);
				}
				nl = false;
			}
			b.append(seq, start, end);
		}
	}
	
	public TextBuilder append(String content, Object...params) {
		append(StringFormat.format(content, params));
		return this;
	}
	
	public TextBuilder append(int v) {
		append(Integer.toString(v));
		return this;
	}
	
	public TextBuilder append(long v) {
		append(Long.toString(v));
		return this;
	}

	
	public TextBuilder nl() {
		append('\n');
		return this;
	}

	public TextBuilder print(String msg) {
		append(msg);
		return this;
	}
	
	public TextBuilder print(String msg, Object...p) {
		append(StringFormat.format(msg,p));
		return this;
	}

	public TextBuilder println() {
		nl();
		return this;
	}

	public TextBuilder println(String msg) {
		append(msg);
		nl();
		return this;
	}

	public TextBuilder println(String msg, Object...p) {
		append(StringFormat.format(msg,p));
		nl();
		return this;
	}

	
	//
	// CharSequence
	//
	
	@Override
	public int length() {
		return b.length();
	}

	@Override
	public char charAt(int index) {
		return b.charAt(index);
	}

	@Override
	public CharSequence subSequence(int start, int end) {
		return b.subSequence(start,end);
	}
}