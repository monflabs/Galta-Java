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

import java.awt.Desktop;
import java.net.URI;
import java.net.URL;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import javax.swing.JEditorPane;
import javax.swing.event.HyperlinkEvent;
import javax.swing.text.html.HTMLDocument;

import org.commonmark.Extension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.monflabs.util.Console;

/**
 * A read-only view of a Markdown document, rendered as HTML (CommonMark, with
 * the GitHub tables extension).
 * <ul>
 * <li>The links open in the system browser, when the platform supports it.</li>
 * <li>The relative links and images resolve against the base folder (see
 * {@link #setBaseFolder(Path)}), when it is on the default file system.</li>
 * </ul>
 */
@SuppressWarnings("serial")
public class MarkdownRenderer extends JEditorPane {

	private static final List<Extension> EXTENSIONS = List.of(TablesExtension.create());
	// Both are immutable and thread-safe: built once
	private static final Parser PARSER = Parser.builder().extensions(EXTENSIONS).build();
	private static final HtmlRenderer RENDERER = HtmlRenderer.builder().extensions(EXTENSIONS).build();

	private static final Set<String> BROWSER_SCHEMES = Set.of("http", "https", "mailto");

	private URL base;

	public MarkdownRenderer() {
		setContentType("text/html");
		setEditable(false);
		addHyperlinkListener(e -> {
			if(e.getEventType()==HyperlinkEvent.EventType.ACTIVATED) {
				openLink(e.getURL(), e.getDescription());
			}
		});
	}

	/**
	 * The folder the relative links and images of the document resolve
	 * against, or null. Ignored when it is not on the default file system
	 * (the HTML view only loads URLs it can open, like file: ones).
	 */
	public void setBaseFolder(Path folder) {
		URL url = null;
		if(folder!=null && folder.getFileSystem()==FileSystems.getDefault()) {
			try {
				url = folder.toAbsolutePath().toUri().toURL();
			} catch(Exception e) {
				Console.log(e);
			}
		}
		this.base = url;
		applyBase();
	}

	public URL getBase() {
		return base;
	}

	private void applyBase() {
		if(getDocument() instanceof HTMLDocument doc) {
			doc.setBase(base);
		}
	}

	public void setMarkdown(String markdown) {
		// A fresh document each time, with its base set before the content is
		// parsed (the images are loaded while parsing)
		setDocument(getEditorKit().createDefaultDocument());
		applyBase();
		setText(toHtml(markdown));
		setCaretPosition(0);
	}

	/**
	 * Opens a link in the system browser: web and mail links, and the files
	 * the base folder resolves. Does nothing when the platform has no
	 * browsing support.
	 */
	protected void openLink(URL url, String description) {
		try {
			URI uri = url!=null ? url.toURI() : (description!=null ? new URI(description) : null);
			if(uri==null || uri.getScheme()==null) {
				return;	// a relative link without base folder, an anchor
			}
			String scheme = uri.getScheme().toLowerCase(java.util.Locale.ROOT);
			if(!BROWSER_SCHEMES.contains(scheme) && !"file".equals(scheme)) {
				return;
			}
			if(!Desktop.isDesktopSupported()) {
				return;
			}
			Desktop desktop = Desktop.getDesktop();
			if("mailto".equals(scheme) && desktop.isSupported(Desktop.Action.MAIL)) {
				desktop.mail(uri);
			} else if(desktop.isSupported(Desktop.Action.BROWSE)) {
				desktop.browse(uri);
			}
		} catch(Exception e) {
			Console.log(e);
		}
	}

	static String toHtml(String markdown) {
		Node document = PARSER.parse(markdown!=null ? markdown : "");
		return RENDERER.render(document);
	}
}
