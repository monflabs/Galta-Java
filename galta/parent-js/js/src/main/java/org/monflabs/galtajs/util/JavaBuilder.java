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
package org.monflabs.galtajs.util;

import org.monflabs.util.TextBuilder;

/**
 * 
 */ 
public class JavaBuilder extends TextBuilder {

	public JavaBuilder() {
	}
	public JavaBuilder(int bufferSize) {
		super(bufferSize);
	}

	public void comment(String comment, Object... params) {
		append("// ");
		println(comment, params);
	}
	// Unlike comment(), does not treat the text as a StringFormat template - needed for
	// arbitrary source snippets, which may legitimately contain brace sequences (object
	// literals, destructuring, unicode code-point escapes) that StringFormat would
	// otherwise try to parse as placeholders.
	public void commentRaw(String comment) {
		append("// ");
		println(comment);
	}
	public void commentMulti(String comment, Object... params) {
		append("/*\n");
		println(comment, params);
		append("*/\n");
	}
}
