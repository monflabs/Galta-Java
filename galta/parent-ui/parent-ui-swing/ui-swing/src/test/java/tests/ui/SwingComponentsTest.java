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
import java.util.ArrayList;
import java.util.List;

import javax.swing.Icon;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import org.monflabs.ui.lookup.StringArrayLookup;
import org.monflabs.ui.swing.components.image.ImageUtil;
import org.monflabs.ui.swing.components.models.LookupListModel;
import org.monflabs.ui.swing.layouts.VerticalFlowLayout;

import tests.ProjectTestCase;

/**
 * Headless tests of the Swing components and layouts.
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

}
