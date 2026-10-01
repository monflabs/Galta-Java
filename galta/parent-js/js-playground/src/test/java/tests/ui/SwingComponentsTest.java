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

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.swing.Icon;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

import org.monflabs.ui.lookup.StringArrayLookup;
import org.monflabs.ui.swing.components.image.ImageUtil;
import org.monflabs.ui.swing.components.models.LookupListModel;
import org.monflabs.ui.swing.ide.components.TextAreaOutputStream;
import org.monflabs.ui.swing.layouts.VerticalFlowLayout;
import org.monflabs.ui.swing.settings.MultipartTextFile;

import tests.ProjectTestCase;

/**
 * Headless tests of the Swing helpers (ui-swing and ui-swing-ide have no test module of their own).
 */
public class SwingComponentsTest extends ProjectTestCase {

	private static class RecordingIcon implements Icon {
		List<Rectangle> painted = new ArrayList<>();
		@Override public void paintIcon(Component c, Graphics g, int x, int y) { painted.add(new Rectangle(x, y, 0, 0)); }
		@Override public int getIconWidth() { return 10; }
		@Override public int getIconHeight() { return 20; }
	}

	public void testImageVerticalStretch() {
		BufferedImage img = new BufferedImage(200, 200, BufferedImage.TYPE_INT_ARGB);
		Graphics g = img.getGraphics();
		RecordingIcon icon = new RecordingIcon();
		// Vertical stretch must not change the horizontal layout (it used to set the width)
		ImageUtil.drawIcon(null, g, ImageUtil.HAlign.MOSAIC, ImageUtil.VAlign.STRETCH, icon, new Rectangle(0, 0, 100, 50), null);
		// width 100 / icon width 10 = 10 columns, 1 row
		assertEquals(10, icon.painted.size());
		assertEquals(90, icon.painted.get(9).x);
	}

	private static JPanel fixed(int w, int h) {
		JPanel p = new JPanel();
		p.setPreferredSize(new Dimension(w, h));
		p.setMinimumSize(new Dimension(w, h));
		return p;
	}

	public void testVerticalFlowLayoutGaps() {
		JPanel panel = new JPanel(new VerticalFlowLayout(VerticalFlowLayout.TOP, 3, 7, false, false));
		panel.add(fixed(10, 20));
		panel.add(fixed(10, 20));
		// rows separated by vgap, framed by vgap on both sides
		assertEquals(20+7+20+2*7, panel.getPreferredSize().height);
		assertEquals(panel.getPreferredSize().height, panel.getMinimumSize().height);
		assertEquals(10+2*3, panel.getPreferredSize().width);
		// no parent: layout must not fail
		panel.setSize(100, 100);
		panel.doLayout();
	}

	public void testVerticalFlowLayoutInvisibleFirst() {
		JPanel panel = new JPanel(new VerticalFlowLayout(VerticalFlowLayout.TOP, 3, 7, false, false));
		JPanel hidden = fixed(10, 20);
		hidden.setVisible(false);
		panel.add(hidden);
		panel.add(fixed(10, 20));
		assertEquals(20+2*7, panel.getPreferredSize().height);
	}

	public void testLookupListModel() {
		LookupListModel<String> m = new LookupListModel<>(new StringArrayLookup("a", "b"));
		assertEquals(2, m.getSize());
		assertEquals("b", m.getElementAt(1));
	}

	public void testLookupListModelFollowsTheLookup() throws Exception {
		StringArrayLookup lookup = new StringArrayLookup("a", "b");
		LookupListModel<String> m = new LookupListModel<>(lookup);
		List<String> events = new ArrayList<>();
		javax.swing.event.ListDataListener l = new javax.swing.event.ListDataListener() {
			@Override public void intervalAdded(javax.swing.event.ListDataEvent e) { events.add("added"); }
			@Override public void intervalRemoved(javax.swing.event.ListDataEvent e) { events.add("removed"); }
			@Override public void contentsChanged(javax.swing.event.ListDataEvent e) { events.add("changed"); }
		};
		m.addListDataListener(l);
		lookup.notifyLookupChanged();		// from a non-EDT thread: forwarded on the EDT
		SwingUtilities.invokeAndWait(() -> {});
		assertEquals(List.of("changed"), events);
		// No more listener on the model: it stops listening to the lookup
		m.removeListDataListener(l);
		lookup.notifyLookupChanged();
		SwingUtilities.invokeAndWait(() -> {});
		assertEquals(List.of("changed"), events);
	}

	public void testVerticalFlowLayoutUsesFlowLayoutGaps() {
		VerticalFlowLayout layout = new VerticalFlowLayout(VerticalFlowLayout.TOP, 3, 7, false, false);
		// The gaps are FlowLayout's own: the getters report them, the setters change the layout
		assertEquals(3, layout.getHgap());
		assertEquals(7, layout.getVgap());
		JPanel panel = new JPanel(layout);
		panel.add(fixed(10, 20));
		panel.add(fixed(10, 20));
		layout.setVgap(1);
		layout.setHgap(0);
		assertEquals(20+1+20+2*1, panel.getPreferredSize().height);
		assertEquals(10, panel.getPreferredSize().width);
	}

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

	enum Color { RED }
	record Point(int x, int y) {}
	static class Node {
		String name;
		Node next;
		Color color = Color.RED;
		Point point = new Point(1, 2);
		java.util.concurrent.atomic.AtomicInteger counter = new java.util.concurrent.atomic.AtomicInteger(5);
		Node(String name) { this.name = name; }
	}

	public void testAstDescriptionCyclesAndOpaqueTypes() {
		Node a = new Node("a"), b = new Node("b");
		a.next = b;
		b.next = a;	// a cycle: used to recurse until a StackOverflowError
		org.monflabs.util.TextBuilder tb = new org.monflabs.util.TextBuilder();
		playground.impl.GaltaJSPlaygroundFrame.readObject(tb, a);
		String s = tb.toString();
		assertTrue(s, s.contains("<cycle>"));
		// enums, records and JDK types print themselves (no reflective access to JDK internals)
		assertTrue(s, s.contains("color=RED"));
		assertTrue(s, s.contains("point=Point[x=1, y=2]"));
		assertTrue(s, s.contains("counter=5"));
	}

	public void testJdkLibraryInfos() throws Exception {
		java.io.File home = new java.io.File(System.getProperty("java.home"));
		java.util.List<org.fife.rsta.ac.java.buildpath.LibraryInfo> infos = new java.util.ArrayList<>();
		infos.add(new com.monflabs.swing.rtsyntax.JrtLibraryInfo());
		org.fife.rsta.ac.java.buildpath.LibraryInfo main = com.monflabs.swing.rtsyntax.LibraryInfo2.getJreJarInfo(home);
		assertNotNull(main);	// jmods when present, else jrt:/
		infos.add(main);
		for(org.fife.rsta.ac.java.buildpath.LibraryInfo info: infos) {
			assertNotNull(info.createPackageMap());
			assertSame("built once", info.createPackageMap(), info.createPackageMap());
			org.fife.rsta.ac.java.classreader.ClassFile string = info.createClassFile("java/lang/String.class");
			assertNotNull(info.toString(), string);
			assertEquals("java.lang.String", string.getClassName(true));
			assertNotNull(info.createClassFile("java/util/concurrent/ConcurrentHashMap.class"));
			assertNull(info.createClassFile("no/such/Clazz.class"));
		}
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
}
