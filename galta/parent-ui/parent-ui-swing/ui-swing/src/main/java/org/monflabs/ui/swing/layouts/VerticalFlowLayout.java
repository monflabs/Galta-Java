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
package org.monflabs.ui.swing.layouts;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Insets;
import java.awt.Rectangle;

import javax.swing.JScrollPane;


public class VerticalFlowLayout extends FlowLayout {

	private static final long serialVersionUID = 1L;
	
	public static final int TOP = 0;
	public static final int MIDDLE = 1;
	public static final int BOTTOM = 2;

	private int hgap;
	private int vgap;
	private boolean hfill;
	private boolean vfill;

	public VerticalFlowLayout() {
		this(TOP, 5, 5, true, false);
	}

	public VerticalFlowLayout(boolean hfill, boolean vfill) {
		this(TOP, 5, 5, hfill, vfill);
	}

	public VerticalFlowLayout(int align) {
		this(align, 5, 5, true, false);
	}

	public VerticalFlowLayout(int align, boolean hfill, boolean vfill) {
		this(align, 5, 5, hfill, vfill);
	}

	public VerticalFlowLayout(int align, int hgap, int vgap, boolean hfill, boolean vfill) {
		setAlignment(align);
		this.hgap = hgap;
		this.vgap = vgap;
		this.hfill = hfill;
		this.vfill = vfill;
	}

	@Override
	public Dimension preferredLayoutSize(Container target) {
		Dimension tarsiz = new Dimension(0, 0);
		boolean first = true;
		for (int i = 0; i < target.getComponentCount(); i++) {
			Component m = target.getComponent(i);
			if (m.isVisible()) {
				Dimension d = m.getPreferredSize();
				tarsiz.width = Math.max(tarsiz.width, d.width);
				// Rows are separated by the vertical gap
				if (!first) {
					tarsiz.height += vgap;
				}
				first = false;
				tarsiz.height += d.height;
			}
		}
		Insets insets = target.getInsets();
		tarsiz.width += insets.left + insets.right + hgap * 2;
		tarsiz.height += insets.top + insets.bottom + vgap * 2;
		return tarsiz;
	}

	@Override
	public Dimension minimumLayoutSize(Container target) {
		Dimension tarsiz = new Dimension(0, 0);
		boolean first = true;
		for (int i = 0; i < target.getComponentCount(); i++) {
			Component m = target.getComponent(i);
			if (m.isVisible()) {
				Dimension d = m.getMinimumSize();
				tarsiz.width = Math.max(tarsiz.width, d.width);
				// Rows are separated by the vertical gap
				if (!first) {
					tarsiz.height += vgap;
				}
				first = false;
				tarsiz.height += d.height;
			}
		}
		Insets insets = target.getInsets();
		tarsiz.width += insets.left + insets.right + hgap * 2;
		tarsiz.height += insets.top + insets.bottom + vgap * 2;
		return tarsiz;
	}

	public void setVerticalFill(boolean vfill) {
		this.vfill = vfill;
	}

	public boolean getVerticalFill() {
		return vfill;
	}

	public void setHorizontalFill(boolean hfill) {
		this.hfill = hfill;
	}

	public boolean getHorizontalFill() {
		return hfill;
	}

	private void placethem(Container target, int x, int y, int width, int height, int first, int last) {
		int align = getAlignment();
		if (align == MIDDLE)
			y += height / 2;
		if (align == BOTTOM)
			y += height;
		for (int i = first; i < last; i++) {
			Component m = target.getComponent(i);
			Dimension md = m.getSize();
			if (m.isVisible()) {
				int px = x + (width - md.width) / 2;
				m.setLocation(px, y);
				y += vgap + md.height;
			}
		}
	}

	@Override
	public void layoutContainer(Container target) {
		// When located in a scrollpane., we size it with the srollpane
		// Seems to be the only solution to resize it properly
		int targetSize = target.getSize().width;
		Container parent = target.getParent();
		if(parent!=null && parent.getParent() instanceof JScrollPane sp) {
			Rectangle r = sp.getViewportBorderBounds();
			targetSize = r.width;
		}
		
		Insets insets = target.getInsets();
		int maxheight = target.getSize().height - (insets.top + insets.bottom + vgap * 2);
		int maxwidth = targetSize - (insets.left + insets.right + hgap * 2);
//		int maxwidth =  target.getSize().width - (insets.left + insets.right + hgap * 2);
		int numcomp = target.getComponentCount();
		int x = insets.left + hgap, y = 0;
		int colw = 0, start = 0;
		for (int i = 0; i < numcomp; i++) {
			Component m = target.getComponent(i);
			if (m.isVisible()) {
				Dimension d = m.getPreferredSize();
				if ((this.vfill) && (i == (numcomp - 1))) {
					d.height = Math.max((maxheight - y), m.getPreferredSize().height);
				}
				if (this.hfill) {
					m.setSize(maxwidth, d.height);
					d.width = maxwidth;
				} else {
					m.setSize(d.width, d.height);
				}
				if (y + d.height > maxheight) {
					placethem(target, x, insets.top + vgap, colw, maxheight - y, start, i);
					y = d.height;
					x += hgap + colw;
					colw = d.width;
					start = i;
				} else {
					if (y > 0)
						y += vgap;
					y += d.height;
					colw = Math.max(colw, d.width);
				}
			}
		}
		placethem(target, x, insets.top + vgap, colw, maxheight - y, start, numcomp);
	}
}