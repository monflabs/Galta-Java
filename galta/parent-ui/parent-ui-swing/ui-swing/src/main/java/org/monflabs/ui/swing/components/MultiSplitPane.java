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
package org.monflabs.ui.swing.components;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Graphics;
import java.awt.LayoutManager;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;

import javax.swing.JPanel;
import javax.swing.UIManager;
import javax.swing.event.MouseInputAdapter;

import org.monflabs.ui.swing.components.MultiSplitLayout.Divider;
import org.monflabs.ui.swing.components.MultiSplitLayout.Region;

/**
 * Panel holding several named panes separated by dividers the user can drag.
 * <p>
 * The arrangement is a tree of rows and columns (see {@link MultiSplitLayout}),
 * so a pane can be split in more than two parts, and splits can be nested:
 * <pre>
 * MultiSplitPane p = new MultiSplitPane(
 *     MultiSplitLayout.row(
 *         MultiSplitLayout.pane("navigator", 1),
 *         MultiSplitLayout.column(
 *             MultiSplitLayout.pane("editor", 3),
 *             MultiSplitLayout.pane("console", 1)).weight(4)));
 * p.add(tree, "navigator");
 * p.add(editor, "editor");
 * p.add(console, "console");
 * </pre>
 * With continuous layout (the default) the panes are resized while a divider
 * is dragged; otherwise a bar shows the new position and the panes are
 * resized when the mouse is released.
 */
public class MultiSplitPane extends JPanel {

	private static final long serialVersionUID = 1L;

	private boolean continuousLayout = true;
	private transient Divider dragged;
	// Offset between the mouse and the divider start, and the pending position
	private int dragOffset;
	private int dragPosition;

	public MultiSplitPane(Region model) {
		super(new MultiSplitLayout(model));
		DividerMouse mouse = new DividerMouse();
		addMouseListener(mouse);
		addMouseMotionListener(mouse);
	}

	@Override
	public void setLayout(LayoutManager mgr) {
		// The constructor installs the one layout this panel works with
		if(getLayout()!=null && !(mgr instanceof MultiSplitLayout)) {
			throw new IllegalArgumentException("MultiSplitPane requires a MultiSplitLayout");
		}
		super.setLayout(mgr);
	}

	public MultiSplitLayout getMultiSplitLayout() {
		return (MultiSplitLayout)getLayout();
	}

	public Region getModel() {
		return getMultiSplitLayout().getModel();
	}

	public void setModel(Region model) {
		getMultiSplitLayout().setModel(model);
		revalidate();
		repaint();
	}

	public int getDividerSize() {
		return getMultiSplitLayout().getDividerSize();
	}

	public void setDividerSize(int size) {
		getMultiSplitLayout().setDividerSize(size);
		revalidate();
		repaint();
	}

	public boolean isContinuousLayout() {
		return continuousLayout;
	}

	public void setContinuousLayout(boolean continuousLayout) {
		this.continuousLayout = continuousLayout;
	}

	/**
	 * True while the user is dragging a divider.
	 */
	public boolean isDragging() {
		return dragged!=null;
	}

	@Override
	protected void paintChildren(Graphics g) {
		super.paintChildren(g);
		if(dragged!=null && !continuousLayout) {
			Rectangle r = previewBounds();
			Color c = UIManager.getColor("SplitPaneDivider.draggingColor");
			g.setColor(c!=null ? c : Color.DARK_GRAY);
			g.fillRect(r.x, r.y, r.width, r.height);
		}
	}

	private Rectangle previewBounds() {
		Rectangle r = dragged.getBounds();
		if(dragged.isHorizontal()) {
			r.x = dragPosition;
		} else {
			r.y = dragPosition;
		}
		return r;
	}

	private void moveTo(int position) {
		MultiSplitLayout layout = getMultiSplitLayout();
		layout.moveDivider(dragged, position);
		// Lay out now, so the dividers are rebuilt: follow the one being dragged
		doLayout();
		revalidate();
		for(Divider d: layout.getDividers()) {
			if(d.getSplit()==dragged.getSplit() && d.getIndex()==dragged.getIndex()) {
				dragged = d;
				break;
			}
		}
		repaint();
	}

	private class DividerMouse extends MouseInputAdapter {
		@Override
		public void mouseMoved(MouseEvent e) {
			Divider d = getMultiSplitLayout().dividerAt(e.getPoint());
			setCursor(d==null ? Cursor.getDefaultCursor()
					: Cursor.getPredefinedCursor(d.isHorizontal() ? Cursor.E_RESIZE_CURSOR : Cursor.N_RESIZE_CURSOR));
		}

		@Override
		public void mouseExited(MouseEvent e) {
			if(dragged==null) {
				setCursor(Cursor.getDefaultCursor());
			}
		}

		@Override
		public void mousePressed(MouseEvent e) {
			if(!isEnabled()) {
				return;
			}
			dragged = getMultiSplitLayout().dividerAt(e.getPoint());
			if(dragged!=null) {
				Rectangle b = dragged.getBounds();
				dragPosition = dragged.isHorizontal() ? b.x : b.y;
				dragOffset = (dragged.isHorizontal() ? e.getX() : e.getY()) - dragPosition;
			}
		}

		@Override
		public void mouseDragged(MouseEvent e) {
			if(dragged==null) {
				return;
			}
			int position = (dragged.isHorizontal() ? e.getX() : e.getY()) - dragOffset;
			if(continuousLayout) {
				moveTo(position);
			} else {
				dragPosition = position;
				repaint();
			}
		}

		@Override
		public void mouseReleased(MouseEvent e) {
			if(dragged==null) {
				return;
			}
			if(!continuousLayout) {
				moveTo(dragPosition);
			}
			dragged = null;
			repaint();
		}
	}
}
