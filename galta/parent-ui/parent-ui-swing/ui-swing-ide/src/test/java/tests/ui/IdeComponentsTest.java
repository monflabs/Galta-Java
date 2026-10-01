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
package tests.ui;

import java.io.PrintStream;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

import org.monflabs.ui.swing.ide.components.TextAreaOutputStream;
import org.monflabs.ui.swing.settings.MultipartTextFile;

import tests.ProjectTestCase;

/**
 * Headless tests of the IDE components: console stream, settings files.
 */
public class IdeComponentsTest extends ProjectTestCase {

	public void testMultipartTextFile() {
		MultipartTextFile f = new MultipartTextFile();
		String s = f.serialize(ser -> { ser.serialize("a", "one\n"); ser.serialize("b", "two\n"); });
		Map<String,String> parts = new LinkedHashMap<>();
		f.deserialize(s, parts::put);
		assertEquals(Map.of("a", "one\n", "b", "two\n"), parts);
		// A truncated file (header without its end) must not throw
		Map<String,String> partial = new LinkedHashMap<>();
		f.deserialize("---------- PART: [a]\none\n---------- PART: [trunc", partial::put);
		assertEquals("one\n", partial.get("a"));
	}

	public void testTextAreaOutputStreamFromTheEdt() throws Exception {
		JTextArea ta = new JTextArea();
		PrintStream ps = TextAreaOutputStream.getPrintStream(ta);
		// From the event dispatch thread (e.g. a Swing callback printing): used to throw an
		// Error from invokeAndWait() and then buffer every later output forever
		SwingUtilities.invokeAndWait(() -> ps.print("edt;"));
		ps.print("worker;");
		ps.flush();
		String[] text = new String[1];
		SwingUtilities.invokeAndWait(() -> text[0] = ta.getText());
		assertEquals("edt;worker;", text[0]);
	}

	public void testTextAreaOutputStreamDoesNotWaitForTheEdt() throws Exception {
		JTextArea ta = new JTextArea();
		PrintStream ps = TextAreaOutputStream.getPrintStream(ta);
		// Keep the event dispatch thread busy: a print must not wait for it
		java.util.concurrent.CountDownLatch edtBusy = new java.util.concurrent.CountDownLatch(1);
		java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
		SwingUtilities.invokeLater(() -> {
			edtBusy.countDown();
			try {
				release.await(10, java.util.concurrent.TimeUnit.SECONDS);
			} catch(InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		});
		assertTrue(edtBusy.await(10, java.util.concurrent.TimeUnit.SECONDS));
		long start = System.nanoTime();
		for(int i=0; i<1000; i++) {
			ps.print("x");
		}
		ps.println();
		long elapsedMs = (System.nanoTime()-start)/1_000_000;
		release.countDown();
		assertTrue("printing waited for the EDT: "+elapsedMs+"ms", elapsedMs<5_000);
		// flush() is the synchronization point: everything is visible afterwards
		ps.flush();
		String[] text = new String[1];
		SwingUtilities.invokeAndWait(() -> text[0] = ta.getText());
		assertEquals("x".repeat(1000)+System.lineSeparator(), text[0]);
	}

	private static String text(JTextArea ta) throws Exception {
		String[] text = new String[1];
		SwingUtilities.invokeAndWait(() -> text[0] = ta.getText());
		return text[0];
	}

	/**
	 * Blocks the event dispatch thread until the returned latch is released.
	 */
	private static java.util.concurrent.CountDownLatch blockEdt() throws Exception {
		java.util.concurrent.CountDownLatch edtBusy = new java.util.concurrent.CountDownLatch(1);
		java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
		SwingUtilities.invokeLater(() -> {
			edtBusy.countDown();
			try {
				release.await(30, java.util.concurrent.TimeUnit.SECONDS);
			} catch(InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		});
		assertTrue(edtBusy.await(10, java.util.concurrent.TimeUnit.SECONDS));
		return release;
	}

	public void testWriteIntDoesNotWaitForTheEdt() throws Exception {
		JTextArea ta = new JTextArea();
		PrintStream ps = TextAreaOutputStream.getPrintStream(ta);
		java.util.concurrent.CountDownLatch release = blockEdt();
		long start = System.nanoTime();
		try {
			// byte by byte: the base class flushes after each one, which used
			// to wait for the event dispatch thread every time
			for(byte b: "abc\n".getBytes(java.nio.charset.StandardCharsets.UTF_8)) {
				ps.write(b);
			}
		} finally {
			release.countDown();
		}
		long elapsedMs = (System.nanoTime()-start)/1_000_000;
		assertTrue("write(int) waited for the EDT: "+elapsedMs+"ms", elapsedMs<5_000);
		ps.flush();
		assertEquals("abc\n", text(ta));
	}

	public void testEdtPrintDuringAWorkerFlushDoesNotDeadlock() throws Exception {
		JTextArea ta = new JTextArea();
		PrintStream ps = TextAreaOutputStream.getPrintStream(ta);
		java.util.concurrent.CountDownLatch release = blockEdt();
		// The worker flushes while the EDT is busy: it waits for the EDT...
		Thread worker = new Thread(() -> {
			ps.print("worker;");
			ps.flush();
		}, "worker");
		worker.start();
		// ... give it the time to be waiting, then the EDT prints to the same
		// stream: it used to block on the stream lock, held by the waiting worker
		Thread.sleep(200);
		SwingUtilities.invokeLater(() -> ps.print("edt;"));
		release.countDown();
		worker.join(10_000);
		assertFalse("deadlock between the worker flush and the EDT print", worker.isAlive());
		ps.flush();
		assertEquals("worker;edt;", text(ta));
	}

	private static class Latin1Stream extends TextAreaOutputStream {
		Latin1Stream(JTextArea ta) {
			super(ta, java.nio.charset.StandardCharsets.ISO_8859_1);
		}
	}

	public void testNonAsciiRoundTrip() throws Exception {
		// The default stream encodes and decodes UTF-8, whatever the platform
		// charset (-Dfile.encoding=ISO-8859-1 used to garble every non-ASCII
		// character: encoded with the default charset, decoded as UTF-8)
		JTextArea ta = new JTextArea();
		PrintStream ps = TextAreaOutputStream.getPrintStream(ta);
		String s = "héllo € 日本";
		ps.print(s);
		// raw UTF-8 bytes, as a child process would write them
		ps.write("ü".getBytes(java.nio.charset.StandardCharsets.UTF_8));
		ps.flush();
		assertEquals(s+"ü", text(ta));

		// A stream built for another charset decodes with that same charset
		JTextArea latin = new JTextArea();
		PrintStream lps = new Latin1Stream(latin);
		lps.print("héllo ü");
		lps.flush();
		assertEquals("héllo ü", text(latin));
	}

	public void testClearFromAWorker() throws Exception {
		JTextArea ta = new JTextArea();
		PrintStream ps = TextAreaOutputStream.getPrintStream(ta);
		ps.print("before");
		ps.flush();
		// off the event dispatch thread: queued on it, after the published text
		PrintStream ps2 = TextAreaOutputStream.getPrintStream(ta, true);
		ps2.print("after");
		ps2.flush();
		assertEquals("after", text(ta));
	}

	public void testTrimmedToTheMaximumSize() throws Exception {
		JTextArea ta = new JTextArea();
		PrintStream ps = TextAreaOutputStream.getPrintStream(ta);
		String line = "0123456789".repeat(10)+"\n";
		for(int i=0; i<2_500; i++) {
			ps.print(line);
		}
		ps.flush();
		String t = text(ta);
		assertTrue(t.length()+"", t.length()<200_000);
		assertTrue(t.endsWith(line));
	}
}
