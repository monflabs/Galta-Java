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

import java.io.PrintStream;

import javax.swing.JTextArea;

import org.monflabs.playground.ExecutionContext;
import org.monflabs.playground.Snippet;
import org.monflabs.ui.swing.ide.components.TextAreaOutputStream;
import org.monflabs.util.Console;
import org.monflabs.util.StringUtil;

public abstract class PlaygroundExecutionContext extends ExecutionContext {
	
	private PlaygroundFrame frame;
	private PrintStream ps;
	
	public PlaygroundExecutionContext(PlaygroundFrame frame, Snippet snippet) {
		super(snippet);
		this.frame = frame;
		this.ps = TextAreaOutputStream.getPrintStream(frame.getConsoleTextArea());
	}
	
	public PlaygroundFrame getFrame() {
		return frame;
	}

	@Override
	public PrintStream getConsoleOut() {
		return ps;
	}
	
	@Override
	public PrintStream getConsoleErr() {
		return ps;
	}
	
	@Override
	public String getConsoleText() {
		// everything printed so far, read on the event dispatch thread
		ps.flush();
		String[] text = new String[1];
		PlaygroundFrame.onEdt( () -> text[0] = frame.getConsoleTextArea().getText() );
		return text[0];
	}
	
	@Override
	public Object getExecutionOption(String key, Object defaultValue) {
		return frame.getExecutionOption(key, defaultValue);
	}
	
	@Override
	public void printlnAtLine(int line, String msg) {
		try {
    		if(line>=0 && StringUtil.isNotEmpty(msg)) {
    			// The console is published asynchronously, but flush() waits for it
    			// (see TextAreaOutputStream): once the stream is flushed, the text area
    			// holds everything printed so far. It must still be read on the event
    			// dispatch thread.
    			ps.flush();
    			JTextArea ta = frame.getConsoleTextArea();
    			int[] count = new int[1];
    			PlaygroundFrame.onEdt( () -> count[0] = ta.getLineCount() );
    			int l = count[0];
    			for(int i=l; i<line; i++) {
        			ps.println();
    			}
    		}
    		ps.println(msg);
		} catch(Exception e) {
			Console.log(e);
		}
	}

}