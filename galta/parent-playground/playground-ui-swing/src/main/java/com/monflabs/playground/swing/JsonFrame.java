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
package com.monflabs.playground.swing;

import java.awt.BorderLayout;

import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rtextarea.RTextScrollPane;
import org.monflabs.ui.swing.ide.frame.IDEFrame;
import org.monflabs.ui.swing.ide.syntax.SyntaxTextArea;

/**
 * 
 */
@SuppressWarnings("serial")
public class JsonFrame extends IDEFrame {
	
	private SyntaxTextArea edJson;
	
    public JsonFrame() {
    	setTitle("JSON Document");
        this.setSize(800,550);
        getContentPane().setLayout(new BorderLayout(0, 0));
        
        RTextScrollPane textScrollPane = new RTextScrollPane();
        textScrollPane.setViewportBorder(null);
        getContentPane().add(textScrollPane, BorderLayout.CENTER);
        
        edJson = new SyntaxTextArea();
        textScrollPane.setViewportView(edJson);
    }

    public void setText(String text, boolean json) {
    	if(json) {
            edJson.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_JSON_WITH_COMMENTS);
    	} else {
            edJson.setSyntaxEditingStyle(null);
    	}
    	edJson.setText(text);
		edJson.setCaretPosition(0);
    }
} 