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

import java.awt.Dimension;
import java.awt.Font;
import java.lang.ref.WeakReference;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.plaf.FontUIResource;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;

import org.monflabs.ui.swing.dialogs.JTreeUtil;
import org.monflabs.ui.swing.frame.JFrameCloseable;
import org.monflabs.ui.swing.layouts.VerticalFlowLayout;
import org.monflabs.ui.swing.util.FontUtil;
import org.monflabs.ui.swing.util.SwingIdleUpdater;
import org.monflabs.ui.swing.util.SwingUtil;

import tests.ProjectTestCase;

/**
 * Headless tests of the Swing utilities: tree expansion, fonts, layouts, the
 * idle updater.
 */
public class SwingUtilitiesTest extends ProjectTestCase {

	private static DefaultMutableTreeNode buildTree(int[] count, int depth, int fanout) {
		DefaultMutableTreeNode n = new DefaultMutableTreeNode("n"+count[0]++);
		if(depth>0) {
			for(int i=0; i<fanout; i++) {
				n.add(buildTree(count, depth-1, fanout));
			}
		}
		return n;
	}

	private static long expandLargeTree(int[] count) throws Exception {
		// 21 845 nodes: 1 + 4 + 16 + ... + 4^7
		count[0] = 0;
		DefaultMutableTreeNode root = buildTree(count, 7, 4);
		JTree tree = new JTree(new DefaultTreeModel(root));
		// a large tree is configured as such: fixed row height, large model
		tree.setRowHeight(16);
		tree.setLargeModel(true);
		long[] elapsed = new long[1];
		SwingUtilities.invokeAndWait(() -> {
			long start = System.nanoTime();
			JTreeUtil.expandAllNodes(tree);
			elapsed[0] = (System.nanoTime()-start)/1_000_000;
		});
		// every node is shown
		assertEquals(count[0], tree.getRowCount());
		assertNotNull("the UI is back", tree.getUI());
		return elapsed[0];
	}

	public void testExpandAllNodesIsFast() throws Exception {
		int[] count = new int[1];
		expandLargeTree(count);	// warm-up (class loading, JIT)
		long elapsed = expandLargeTree(count);
		assertTrue(count[0]>=20_000);
		// expanding row by row took about 6s on the event dispatch thread
		assertTrue("expanding "+count[0]+" nodes took "+elapsed+"ms", elapsed<500);
	}

	public void testExpandAllNodesVariableHeight() throws Exception {
		int[] count = new int[1];
		DefaultMutableTreeNode root = buildTree(count, 4, 3);
		JTree tree = new JTree(new DefaultTreeModel(root));
		SwingUtilities.invokeAndWait(() -> JTreeUtil.expandAllNodes(tree));
		assertEquals(count[0], tree.getRowCount());
		for(int i=0; i<tree.getRowCount(); i++) {
			assertTrue(tree.getModel().isLeaf(tree.getPathForRow(i).getLastPathComponent()) || tree.isExpanded(i));
		}
	}

	public void testExpandToADepth() throws Exception {
		int[] count = new int[1];
		DefaultMutableTreeNode root = buildTree(count, 3, 2);
		JTree tree = new JTree(new DefaultTreeModel(root));
		SwingUtilities.invokeAndWait(() -> JTreeUtil.expandAllNodes(tree, 2));
		// root + 2 children + 4 grandchildren (collapsed below)
		assertEquals(7, tree.getRowCount());
	}

	public void testBoldify() {
		Font f = new Font(Font.SANS_SERIF, Font.ITALIC, 12).deriveFont(13.5f);
		Font b = FontUtil.boldify(f);
		assertTrue(b.isBold());
		assertTrue("keeps the other style bits", b.isItalic());
		assertEquals("keeps a fractional size", 13.5f, b.getSize2D());
		FontUIResource r = new FontUIResource(f);
		assertTrue("stays a UI resource", FontUtil.boldify(r) instanceof FontUIResource);
		Font bold = new Font(Font.SANS_SERIF, Font.BOLD, 12);
		assertSame(bold, FontUtil.boldify(bold));
	}

	public void testScale() {
		// 100% in a headless test JVM: unchanged
		assertTrue(SwingUtil.scale(10)>=10);
	}

	private static JPanel fixed(int w, int h) {
		JPanel p = new JPanel();
		p.setPreferredSize(new Dimension(w, h));
		p.setMinimumSize(new Dimension(w, h));
		return p;
	}

	public void testVerticalFillStaysInTheBottomGap() {
		JPanel panel = new JPanel(new VerticalFlowLayout(VerticalFlowLayout.TOP, 3, 7, true, true));
		JPanel first = fixed(10, 20);
		JPanel last = fixed(10, 20);
		panel.add(first);
		panel.add(last);
		panel.setSize(100, 100);
		panel.doLayout();
		// the last component fills the column, down to the bottom gap
		assertEquals(7, first.getY());
		assertEquals(7+20+7, last.getY());
		assertEquals(100-7, last.getY()+last.getHeight());
	}

	public void testVerticalFillSkipsAnInvisibleLast() {
		JPanel panel = new JPanel(new VerticalFlowLayout(VerticalFlowLayout.TOP, 3, 7, true, true));
		JPanel first = fixed(10, 20);
		JPanel hidden = fixed(10, 20);
		hidden.setVisible(false);
		panel.add(first);
		panel.add(hidden);
		panel.setSize(100, 100);
		panel.doLayout();
		assertEquals(100-7, first.getY()+first.getHeight());
	}

	public void testIdleUpdater() throws Exception {
		SwingIdleUpdater u = SwingIdleUpdater.get();
		JButton button = new JButton();
		AtomicInteger calls = new AtomicInteger();
		CountDownLatch called = new CountDownLatch(1);
		u.attach(button, upd -> {
			upd.enable(button, false);
			calls.incrementAndGet();
			called.countDown();
		});
		assertTrue("installed on the first attach", u.isInstalled());
		assertTrue(u.isAttached(button));
		SwingUtilities.invokeAndWait(() -> {});
		SwingUtilities.invokeLater(() -> {});
		assertTrue(called.await(10, TimeUnit.SECONDS));
		SwingUtilities.invokeAndWait(() -> {});
		assertFalse(button.isEnabled());
		u.detach(button);
		assertFalse(u.isAttached(button));
		try {
			u.detach(button);
			fail();
		} catch(IllegalStateException e) {
			// expected
		}
	}

	private static WeakReference<JPanel> attachNewPanel() {
		JPanel panel = new JPanel();
		// the updater references its component, as they usually do
		SwingIdleUpdater.get().attach(panel, upd -> upd.visible(panel, true));
		return new WeakReference<>(panel);
	}

	public void testIdleUpdaterDoesNotKeepTheComponent() throws Exception {
		WeakReference<JPanel> ref = attachNewPanel();
		for(int i=0; i<50 && ref.get()!=null; i++) {
			System.gc();
			Thread.sleep(20);
		}
		assertNull("a never detached component can be collected", ref.get());
	}

	public void testExitOnLastFrameClosedIsConfigurable() {
		assertTrue(JFrameCloseable.isExitOnLastFrameClosed());
		JFrameCloseable.setExitOnLastFrameClosed(false);
		try {
			assertFalse(JFrameCloseable.isExitOnLastFrameClosed());
		} finally {
			JFrameCloseable.setExitOnLastFrameClosed(true);
		}
	}
}
