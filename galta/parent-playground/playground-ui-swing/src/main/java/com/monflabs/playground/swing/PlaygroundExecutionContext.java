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
import org.monflabs.playground.ExecutionController;
import org.monflabs.playground.Snippet;
import org.monflabs.ui.swing.ide.components.TextAreaOutputStream;
import org.monflabs.util.Console;
import org.monflabs.util.StringUtil;

/**
 * The execution context of the playground frame: the console is the frame's
 * console text area.
 * <p>
 * The console stream handed to an execution only writes while that
 * execution is the current one (see {@link ExecutionController.Run#gate(PrintStream)}):
 * a run superseded by a newer one cannot write over its console.
 */
public abstract class PlaygroundExecutionContext extends ExecutionContext {

	private final PlaygroundFrame frame;
	private final PrintStream ps;
	// The console line where the current run's output starts (0-based):
	// printlnAtLine() aligns on the run's own lines, after a preserved console
	private volatile int consoleBaseLine;

	public PlaygroundExecutionContext(PlaygroundFrame frame, Snippet snippet) {
		super(snippet);
		this.frame = frame;
		this.ps = TextAreaOutputStream.getPrintStream(frame.getConsoleTextArea());
	}

	public PlaygroundFrame getFrame() {
		return frame;
	}

	/**
	 * The console stream, gated by the run executing on the calling thread
	 * when there is one.
	 */
	protected PrintStream consoleStream() {
		ExecutionController.Run run = ExecutionController.currentThreadRun();
		return run!=null && run.getContext()==this ? run.gate(ps) : ps;
	}

	@Override
	public PrintStream getConsoleOut() {
		return consoleStream();
	}

	@Override
	public PrintStream getConsoleErr() {
		return consoleStream();
	}

	/**
	 * The raw console stream, not gated by any run.
	 */
	PrintStream getRawConsole() {
		return ps;
	}

	/**
	 * Sets the console line (0-based) where the output of the run starting
	 * now begins: called by the frame on the event dispatch thread.
	 */
	void setConsoleBaseLine(int line) {
		this.consoleBaseLine = line;
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

	/**
	 * Prints the message on the console line matching the source line, when
	 * the console has not gone past it: blank lines are added to get there.
	 * The lines are counted from the start of the current run's output, so a
	 * preserved console does not shift them. Once the console has been
	 * trimmed (it keeps the last 200 000 characters) the alignment is lost:
	 * the message is then just printed.
	 */
	@Override
	public void printlnAtLine(int line, String msg) {
		try {
			PrintStream out = consoleStream();
    		if(line>=0 && StringUtil.isNotEmpty(msg)) {
    			// The console is published asynchronously, but flush() waits for it
    			// (see TextAreaOutputStream): once the stream is flushed, the text area
    			// holds everything printed so far. It must still be read on the event
    			// dispatch thread.
    			out.flush();
    			JTextArea ta = frame.getConsoleTextArea();
    			int[] count = new int[1];
    			PlaygroundFrame.onEdt( () -> count[0] = ta.getLineCount() );
    			int target = consoleBaseLine + line;
    			for(int i=count[0]; i<target; i++) {
        			out.println();
    			}
    		}
    		out.println(msg);
		} catch(Exception e) {
			Console.log(e);
		}
	}

}
