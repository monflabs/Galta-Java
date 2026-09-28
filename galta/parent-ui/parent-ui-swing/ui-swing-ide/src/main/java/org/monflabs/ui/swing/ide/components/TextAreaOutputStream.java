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
	
	
	private static class TextAreaWriter extends WriterOutputStream { 

		private JTextArea textArea;
	    private int maxSize;
	    private int bufferSize;
	
	    private TextAreaWriter(JTextArea textArea) {
	    	this(textArea,200_000, 20_000);
	    	
	    }
	    private TextAreaWriter(JTextArea textArea, int maxSize, int bufferSize) {
	    	super(null);
	    	this.textArea = textArea;
	        this.maxSize = maxSize;
	        this.bufferSize = bufferSize;
	    }
	    
	    private StringBuilder pendingText = new StringBuilder(128);
	    private AtomicBoolean pendingInvoke = new AtomicBoolean(false);
	    
	    private void updateTextArea() {
	    	if(!pendingInvoke.getAndSet(true)) {
	    		Runnable flush = () -> {
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
	    		};
	    		if(SwingUtilities.isEventDispatchThread()) {
	    			// invokeAndWait() cannot be called from the EDT
	    			flush.run();
	    			return;
	    		}
	    		try {
					SwingUtilities.invokeAndWait(flush);
	    		} catch(InterruptedException e) {
	    			// The flush is still queued and will run: just restore the interrupt
	    			Thread.currentThread().interrupt();
	    		} catch(Exception e) {
	    			Console.log(e);
	    		}
	    	}
	    }
	    
	    @Override
		protected void write(char[] chars, int pos, int len) throws IOException {
	    	synchronized(pendingText) {
				pendingText.append(chars,pos,len);
			};
			updateTextArea();
	    }
	}
}
