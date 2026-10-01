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
package org.monflabs.ui.swing.ide.syntax;

import java.awt.Color;

import javax.swing.UIManager;

import org.fife.ui.rsyntaxtextarea.Theme;
import org.fife.ui.rtextarea.RTextArea;
import org.monflabs.util.Console;

/**
 *
 */
@SuppressWarnings("serial")
public class TextArea extends RTextArea {
	
	public TextArea() {
		Theme t = SyntaxTextArea.syntaxAreaTheme();
		if(t!=null) {
			try {
				apply(t,this);
			} catch(RuntimeException ex) {
				Console.log(ex);
			}
		}
	}
	
    
	// Should be in theme!
	private static void apply(Theme t, RTextArea textArea) {
		Color fg = UIManager.getColor("TextArea.foreground");
		textArea.setForeground(fg);
		textArea.setFont(t.baseFont);
		textArea.setBackground(t.bgColor);
		textArea.setCaretColor(t.caretColor);
		textArea.setSelectedTextColor(t.selectionFG);
		textArea.setSelectionColor(t.selectionBG);
		textArea.setRoundedSelectionEdges(t.selectionRoundedEdges);
		textArea.setCurrentLineHighlightColor(t.currentLineHighlight);
		textArea.setFadeCurrentLineHighlight(t.fadeCurrentLineHighlight);
		textArea.setMarginLineColor(t.marginLineColor);
		textArea.setMarkAllHighlightColor(t.markAllHighlightColor);
	}
}
