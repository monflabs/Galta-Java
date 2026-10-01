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
package com.monflabs.swing.components;

import javax.swing.JEditorPane;

import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;

/**
 * A read-only view of a Markdown document, rendered as HTML (CommonMark).
 */
@SuppressWarnings("serial")
public class MarkdownRenderer extends JEditorPane {

	// Both are immutable and thread-safe: built once
	private static final Parser PARSER = Parser.builder().build();
	private static final HtmlRenderer RENDERER = HtmlRenderer.builder().build();

	public MarkdownRenderer() {
		setContentType("text/html");
		setEditable(false);
	}

	public void setMarkdown(String markdown) {
		setText(toHtml(markdown));
		setCaretPosition(0);
	}

	static String toHtml(String markdown) {
		Node document = PARSER.parse(markdown!=null ? markdown : "");
		return RENDERER.render(document);
	}
}
