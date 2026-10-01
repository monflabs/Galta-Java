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
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

import org.monflabs.util.StringUtil;
import org.monflabs.util.io.WriterOutputStream;

/**
 * A {@link PrintStream} appending to a {@link JTextArea}, usable from any
 * thread.
 * <p>
 * The text is buffered and published to the text area on the event dispatch
 * thread with one coalesced {@code invokeLater()} per burst of writes, so a
 * printing thread never waits for the UI. {@link #flush()} is the
 * synchronization point: once it returns, the text area holds everything
 * written so far. The wait happens after the stream's own lock is released,
 * so the event dispatch thread can print to the same stream meanwhile.
 * <p>
 * The text area keeps the last {@code 200 000} characters at most.
 */
public class TextAreaOutputStream extends PrintStream {

	/**
	 * A print stream for the text area, encoding in UTF-8.
	 */
	public static PrintStream getPrintStream(JTextArea textArea) {
		return getPrintStream(textArea,false);
	}
	/**
	 * A print stream for the text area, encoding in UTF-8, after clearing the
	 * text area when {@code clear} is true (see {@link #clear(JTextArea)}).
	 */
	public static PrintStream getPrintStream(JTextArea textArea, boolean clear) {
		if(clear) {
			clear(textArea);
		}
		return new TextAreaOutputStream(textArea);
	}

	/**
	 * Empties the text area: right away on the event dispatch thread,
	 * otherwise queued on it (so it lands after the text already published,
	 * and before the text printed afterwards).
	 */
	public static void clear(JTextArea textArea) {
		if(SwingUtilities.isEventDispatchThread()) {
			textArea.setText("");
		} else {
			SwingUtilities.invokeLater(() -> textArea.setText(""));
		}
	}

    private final JTextArea textArea;
    private final TextAreaWriter writer;

	protected TextAreaOutputStream(JTextArea textArea) {
		this(textArea, StandardCharsets.UTF_8);
	}

	/**
	 * A stream encoding its text with the given charset - the same one is used
	 * to decode it back, whatever the platform default.
	 */
	protected TextAreaOutputStream(JTextArea textArea, Charset charset) {
		this(new TextAreaWriter(textArea, charset), textArea, charset);
	}

	private TextAreaOutputStream(TextAreaWriter writer, JTextArea textArea, Charset charset) {
		super(writer, false, charset);
		this.writer = writer;
		this.textArea = textArea;
	}

	public JTextArea getTextArea() {
		return textArea;
	}

	/**
	 * Flushes the stream and waits until the text area shows everything
	 * written so far. From the event dispatch thread, the text is published
	 * right away. A thread interrupted while waiting returns early, with its
	 * interrupt status set (the text is still published, just later).
	 */
	@Override
	public void flush() {
		// Under the stream lock: decode and queue the pending text, never wait
		super.flush();
		// Lock released: the event dispatch thread may print meanwhile
		writer.awaitPublished();
	}


	/**
	 * Decodes the bytes and queues the text for the event dispatch thread.
	 * None of its methods ever waits for the UI: the waiting is done by
	 * {@link TextAreaOutputStream#flush()}, out of the stream lock.
	 */
	private static class TextAreaWriter extends WriterOutputStream {

		private final JTextArea textArea;
	    private final int maxSize;
	    private final int bufferSize;

	    // guarded by itself
	    private final StringBuilder pendingText = new StringBuilder(128);
	    private final AtomicBoolean pendingInvoke = new AtomicBoolean(false);

	    private TextAreaWriter(JTextArea textArea, Charset charset) {
	    	this(textArea, charset, 200_000, 20_000);
	    }
	    private TextAreaWriter(JTextArea textArea, Charset charset, int maxSize, int bufferSize) {
	    	super(null, charset);
	    	this.textArea = textArea;
	        this.maxSize = maxSize;
	        this.bufferSize = bufferSize;
	    }

	    /**
	     * Appends the pending text to the text area - event dispatch thread only.
	     */
	    private void publishPending() {
	    	String s;
			synchronized(pendingText) {
				s = pendingText.toString();
				pendingText.setLength(0);
				// Reset before touching the UI: a write from now on schedules
				// a new publication
				pendingInvoke.set(false);
			}
			if(!StringUtil.isEmpty(s)) {
				int length = textArea.getDocument().getLength();
				if(length+s.length()>=maxSize) {
					String newText = textArea.getText()+s;
					int len = Math.min(newText.length(), maxSize-bufferSize);
					textArea.setText(newText.substring(newText.length()-len));
				} else {
					textArea.append(s);
				}
			}
	    }

	    /**
	     * Waits until the text written so far is in the text area.
	     */
	    void awaitPublished() {
    		if(SwingUtilities.isEventDispatchThread()) {
    			publishPending();
    			return;
    		}
    		// Queued after any pending publication: when it runs, all the text
    		// written before is in the text area
    		CountDownLatch done = new CountDownLatch(1);
    		SwingUtilities.invokeLater(() -> {
    			try {
    				publishPending();
    			} finally {
    				done.countDown();
    			}
    		});
    		try {
    			done.await();
    		} catch(InterruptedException ex) {
    			// The publication is still queued and will run: just restore the interrupt
    			Thread.currentThread().interrupt();
    		}
	    }

	    @Override
		protected void write(char[] chars, int pos, int len) throws IOException {
	    	boolean schedule;
	    	synchronized(pendingText) {
				pendingText.append(chars,pos,len);
				schedule = !pendingInvoke.getAndSet(true);
			}
	    	if(schedule) {
	    		SwingUtilities.invokeLater(this::publishPending);
	    	}
	    }

	    // The decoded text is queued after every write, whether or not the
	    // base class flushes on its own: a print shows up without an explicit
	    // flush(). Flushing the base class only decodes and queues - see
	    // flush() below, which never waits.
	    @Override
	    public void write(int b) throws IOException {
	    	super.write(b);
	    	super.flush();
	    }

	    @Override
	    public void write(byte[] b, int off, int len) throws IOException {
	    	super.write(b, off, len);
	    	super.flush();
	    }

	    /**
	     * Decodes and queues the pending bytes, without waiting for the UI:
	     * called under the print stream's lock.
	     */
	    @Override
	    public void flush() throws IOException {
	    	super.flush();
	    }
	}
}
