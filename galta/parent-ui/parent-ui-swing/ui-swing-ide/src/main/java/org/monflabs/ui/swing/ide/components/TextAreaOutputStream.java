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
package org.monflabs.ui.swing.ide.components;

import java.io.IOException;
import java.io.PrintStream;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

import org.monflabs.util.Console;
import org.monflabs.util.StringUtil;
import org.monflabs.util.io.WriterOutputStream;

/**
 *
 */
public class TextAreaOutputStream extends PrintStream {
	
	public static PrintStream getPrintStream(JTextArea textArea) {
		return getPrintStream(textArea,false);
	}
	public static PrintStream getPrintStream(JTextArea textArea, boolean clear) {
		if(clear) {
			clear(textArea);
		}
		return new TextAreaOutputStream(textArea);
	}

	public static void clear(JTextArea textArea) {
		textArea.setText("");
	}

    private JTextArea textArea;

	protected TextAreaOutputStream(JTextArea textArea) {
		super(new TextAreaWriter(textArea));
		this.textArea = textArea;
	}
	
	public JTextArea getTextArea() {
		return textArea;
	}
	
	
	/**
	 * Buffers the text and publishes it to the text area on the event dispatch
	 * thread with one coalesced {@code invokeLater()} per burst of writes, so
	 * a printing script never waits for the UI. {@link #flush()} - an explicit
	 * flush of the print stream - is the synchronization point: once it
	 * returns, the text area holds everything written so far.
	 */
	private static class TextAreaWriter extends WriterOutputStream {

		private final JTextArea textArea;
	    private final int maxSize;
	    private final int bufferSize;

	    // guarded by itself
	    private final StringBuilder pendingText = new StringBuilder(128);
	    private final AtomicBoolean pendingInvoke = new AtomicBoolean(false);
	    // true while write() runs: WriterOutputStream flushes itself after
	    // every write, and that internal flush must not wait for the UI.
	    // Only touched under the owning PrintStream's lock.
	    private boolean writing;

	    private TextAreaWriter(JTextArea textArea) {
	    	this(textArea,200_000, 20_000);
	    }
	    private TextAreaWriter(JTextArea textArea, int maxSize, int bufferSize) {
	    	super(null);
	    	this.textArea = textArea;
	        this.maxSize = maxSize;
	        this.bufferSize = bufferSize;
	    }

	    /**
	     * Appends the pending text to the text area - event dispatch thread only.
	     */
	    private void publishPending() {
			synchronized(pendingText) {
				try {
					String s = pendingText.toString();
					pendingText.setLength(0);
					if(!StringUtil.isEmpty(s)) {
						if(textArea.getDocument().getLength()+s.length()>=maxSize) {
							String newText = textArea.getText()+s;
							int len = Math.min(newText.length(), maxSize-bufferSize);
							newText = newText.substring(newText.length()-len,newText.length());
							textArea.setText(newText);
						} else {
							textArea.append(s);
						}
					}
				} finally {
					// Never leave the flag set, or all later output would stay buffered
					pendingInvoke.set(false);
				}
			}
	    }

	    @Override
		protected void write(char[] chars, int pos, int len) throws IOException {
	    	synchronized(pendingText) {
				pendingText.append(chars,pos,len);
			}
	    	if(!pendingInvoke.getAndSet(true)) {
	    		SwingUtilities.invokeLater(this::publishPending);
	    	}
	    }

	    @Override
	    public void write(byte[] b, int off, int len) throws IOException {
	    	writing = true;
	    	try {
	    		super.write(b, off, len);
	    	} finally {
	    		writing = false;
	    	}
	    }

	    @Override
	    public void flush() throws IOException {
	    	super.flush();
	    	if(writing) {
	    		return;
	    	}
    		if(SwingUtilities.isEventDispatchThread()) {
    			publishPending();
    			return;
    		}
    		try {
    			// queued after any pending invokeLater(): when it returns, all
    			// the text written so far is in the text area
				SwingUtilities.invokeAndWait(this::publishPending);
    		} catch(InterruptedException ex) {
    			// The publication is still queued and will run: just restore the interrupt
    			Thread.currentThread().interrupt();
    		} catch(Exception ex) {
    			Console.log(ex);
    		}
	    }
	}
}
