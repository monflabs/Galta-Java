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

import static org.monflabs.ui.swing.components.MultiSplitLayout.column;
import static org.monflabs.ui.swing.components.MultiSplitLayout.pane;
import static org.monflabs.ui.swing.components.MultiSplitLayout.row;

import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;

import javax.swing.JPanel;

import org.monflabs.ui.swing.components.MultiSplitLayout;
import org.monflabs.ui.swing.components.MultiSplitLayout.Divider;
import org.monflabs.ui.swing.components.MultiSplitPane;

import tests.ProjectTestCase;

public class MultiSplitPaneTest extends ProjectTestCase {

	private static JPanel box(int minW, int minH, int prefW, int prefH) {
		JPanel p = new JPanel(null);
		p.setMinimumSize(new Dimension(minW, minH));
		p.setPreferredSize(new Dimension(prefW, prefH));
		return p;
	}

	private static void layout(MultiSplitPane p, int w, int h) {
		p.setSize(w, h);
		p.doLayout();
	}

	public void testWeightsShareTheLength() {
		MultiSplitPane p = new MultiSplitPane(row(pane("a", 1), pane("b", 3)));
		p.setDividerSize(4);
		JPanel a = box(0, 0, 10, 10), b = box(0, 0, 10, 10);
		p.add(a, "a");
		p.add(b, "b");
		layout(p, 404, 100);
		assertEquals(new Rectangle(0, 0, 100, 100), a.getBounds());
		assertEquals(new Rectangle(104, 0, 300, 100), b.getBounds());
		Divider d = p.getMultiSplitLayout().getDividers().get(0);
		assertEquals(new Rectangle(100, 0, 4, 100), d.getBounds());
		assertTrue(d.isHorizontal());
	}

	public void testLengthsAlwaysAddUp() {
		// Three equal children in an odd length: the rounding must not lose pixels
		MultiSplitPane p = new MultiSplitPane(row(pane("a"), pane("b"), pane("c")));
		p.setDividerSize(0);
		JPanel a = box(0, 0, 1, 1), b = box(0, 0, 1, 1), c = box(0, 0, 1, 1);
		p.add(a, "a");
		p.add(b, "b");
		p.add(c, "c");
		for(int w=0; w<50; w++) {
			layout(p, w, 10);
			assertEquals(w, a.getWidth()+b.getWidth()+c.getWidth());
			assertEquals(a.getX()+a.getWidth(), b.getX());
			assertEquals(b.getX()+b.getWidth(), c.getX());
		}
	}

	public void testMinimumSizeIsHonoured() {
		MultiSplitPane p = new MultiSplitPane(row(pane("a", 1), pane("b", 9)));
		p.setDividerSize(0);
		JPanel a = box(50, 0, 10, 10), b = box(0, 0, 10, 10);
		p.add(a, "a");
		p.add(b, "b");
		layout(p, 200, 10);
		// a's share would be 20 pixels, its minimum wins
		assertEquals(50, a.getWidth());
		assertEquals(150, b.getWidth());
		// Not enough room for the minimums: everything shrinks by share
		p.remove(b);
		p.add(box(100, 0, 10, 10), "b");
		layout(p, 100, 10);
		assertEquals(10, a.getWidth());
	}

	public void testNestedRegions() {
		MultiSplitPane p = new MultiSplitPane(
				row(pane("nav", 1), column(pane("editor", 3), pane("console", 1)).weight(3)));
		p.setDividerSize(0);
		JPanel nav = box(0, 0, 1, 1), editor = box(0, 0, 1, 1), console = box(0, 0, 1, 1);
		p.add(nav, "nav");
		p.add(editor, "editor");
		p.add(console, "console");
		layout(p, 400, 200);
		assertEquals(new Rectangle(0, 0, 100, 200), nav.getBounds());
		assertEquals(new Rectangle(100, 0, 300, 150), editor.getBounds());
		assertEquals(new Rectangle(100, 150, 300, 50), console.getBounds());
		assertEquals(2, p.getMultiSplitLayout().getDividers().size());
	}

	public void testMoveDividerAndClamp() {
		MultiSplitPane p = new MultiSplitPane(row(pane("a"), pane("b"), pane("c")));
		p.setDividerSize(0);
		JPanel a = box(20, 0, 1, 1), b = box(20, 0, 1, 1), c = box(0, 0, 1, 1);
		p.add(a, "a");
		p.add(b, "b");
		p.add(c, "c");
		layout(p, 300, 10);
		MultiSplitLayout l = p.getMultiSplitLayout();
		l.moveDivider(l.getDividers().get(0), 150);
		p.doLayout();
		assertEquals(150, a.getWidth());
		assertEquals(50, b.getWidth());
		// Only the two neighbours changed
		assertEquals(100, c.getWidth());
		// Cannot squeeze b under its minimum
		l.moveDivider(l.getDividers().get(0), 290);
		p.doLayout();
		assertEquals(180, a.getWidth());
		assertEquals(20, b.getWidth());
		// Nor a
		l.moveDivider(l.getDividers().get(0), -40);
		p.doLayout();
		assertEquals(20, a.getWidth());
		// The shares follow the container when it grows
		layout(p, 600, 10);
		assertEquals(600, a.getWidth()+b.getWidth()+c.getWidth());
		assertEquals(200, c.getWidth());
	}

	public void testMoveDividerBetweenCollapsedPanes() {
		MultiSplitPane p = new MultiSplitPane(row(pane("a"), pane("b"), pane("c")));
		p.setDividerSize(0);
		JPanel a = box(0, 0, 1, 1), b = box(0, 0, 1, 1), c = box(0, 0, 1, 1);
		p.add(a, "a");
		p.add(b, "b");
		p.add(c, "c");
		layout(p, 300, 10);
		MultiSplitLayout l = p.getMultiSplitLayout();
		// Collapse a and b
		l.moveDivider(l.getDividers().get(1), 0);
		p.doLayout();
		l.moveDivider(l.getDividers().get(0), 0);
		p.doLayout();
		l.moveDivider(l.getDividers().get(1), 0);
		p.doLayout();
		assertEquals(0, a.getWidth());
		assertEquals(0, b.getWidth());
		// Moving the divider between the two collapsed panes: nothing to share
		// (used to turn their shares into NaN)
		l.moveDivider(l.getDividers().get(0), 50);
		for(double share: l.getDividers().get(0).getSplit().getShares()) {
			assertFalse(Double.isNaN(share));
		}
		p.doLayout();
		assertEquals(300, a.getWidth()+b.getWidth()+c.getWidth());
	}

	public void testMouseDrag() {
		MultiSplitPane p = new MultiSplitPane(column(pane("top"), pane("bottom")));
		p.setDividerSize(6);
		JPanel top = box(0, 0, 1, 1), bottom = box(0, 0, 1, 1);
		p.add(top, "top");
		p.add(bottom, "bottom");
		layout(p, 100, 206);
		assertEquals(100, top.getHeight());
		// Grab the divider 2 pixels below its top edge and drag it down by 40
		mouse(p, MouseEvent.MOUSE_PRESSED, 50, 102);
		assertTrue(p.isDragging());
		mouse(p, MouseEvent.MOUSE_DRAGGED, 50, 142);
		assertEquals(140, top.getHeight());
		assertEquals(60, bottom.getHeight());
		mouse(p, MouseEvent.MOUSE_RELEASED, 50, 142);
		assertFalse(p.isDragging());

		// Without continuous layout, nothing moves before the release
		p.setContinuousLayout(false);
		mouse(p, MouseEvent.MOUSE_PRESSED, 50, 142);
		mouse(p, MouseEvent.MOUSE_DRAGGED, 50, 62);
		assertEquals(140, top.getHeight());
		mouse(p, MouseEvent.MOUSE_RELEASED, 50, 62);
		assertEquals(60, top.getHeight());

		// A press away from a divider does nothing
		mouse(p, MouseEvent.MOUSE_PRESSED, 50, 10);
		assertFalse(p.isDragging());
	}

	private static void mouse(MultiSplitPane p, int id, int x, int y) {
		p.dispatchEvent(new MouseEvent(p, id, System.currentTimeMillis(),
				id==MouseEvent.MOUSE_PRESSED || id==MouseEvent.MOUSE_DRAGGED ? MouseEvent.BUTTON1_DOWN_MASK : 0,
				x, y, 1, false, MouseEvent.BUTTON1));
	}

	public void testComponentsOutsideTheModelAreHidden() {
		MultiSplitPane p = new MultiSplitPane(row(pane("a")));
		JPanel a = box(0, 0, 1, 1), stray = box(0, 0, 1, 1);
		p.add(a, "a");
		p.add(stray, "nowhere");
		stray.setBounds(5, 5, 5, 5);
		layout(p, 50, 50);
		assertEquals(new Rectangle(0, 0, 50, 50), a.getBounds());
		assertEquals(new Rectangle(0, 0, 0, 0), stray.getBounds());
		// A pane without a component leaves room, but nothing is placed there
		p.setModel(row(pane("a"), pane("empty")));
		p.setDividerSize(0);
		layout(p, 50, 50);
		assertEquals(25, a.getWidth());
	}

	public void testPreferredAndMinimumSize() {
		MultiSplitPane p = new MultiSplitPane(row(pane("a"), column(pane("b"), pane("c"))));
		p.setDividerSize(5);
		p.add(box(10, 20, 100, 50), "a");
		p.add(box(30, 5, 60, 40), "b");
		p.add(box(15, 5, 70, 30), "c");
		// width: 100 + 5 + max(60,70); height: max(50, 40+5+30)
		assertEquals(new Dimension(175, 75), p.getPreferredSize());
		// width: 10 + 5 + max(30,15); height: max(20, 5+5+5)
		assertEquals(new Dimension(45, 20), p.getMinimumSize());
	}

	public void testInvalidModels() {
		assertThrows(IllegalArgumentException.class, () -> new MultiSplitLayout(row(pane("a"), column(pane("b"), pane("a")))));
		assertThrows(IllegalArgumentException.class, () -> pane("a", 0));
		assertThrows(IllegalArgumentException.class, () -> pane(""));
		assertThrows(IllegalArgumentException.class, () -> row());
		MultiSplitPane p = new MultiSplitPane(row(pane("a")));
		assertThrows(IllegalArgumentException.class, () -> p.setLayout(new java.awt.FlowLayout()));
		assertThrows(IllegalArgumentException.class, () -> p.add(box(0, 0, 1, 1), Integer.valueOf(1)));
	}

	private static void assertThrows(Class<? extends Throwable> type, Runnable r) {
		try {
			r.run();
		} catch(Throwable t) {
			if(type.isInstance(t)) {
				return;
			}
			throw new AssertionError("Expected "+type.getSimpleName()+" but got "+t, t);
		}
		fail("Expected "+type.getSimpleName());
	}
}
