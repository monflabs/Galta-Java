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

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.LayoutManager2;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Layout manager that arranges named panes in a tree of rows and columns
 * separated by movable dividers.
 * <p>
 * The arrangement is described by a {@link Region} tree built with
 * {@link #pane(String)}, {@link #row(Region...)} and {@link #column(Region...)}:
 * <pre>
 * Region model = row(
 *     pane("navigator", 1),
 *     column(pane("editor", 3), pane("console", 1)).weight(4));
 * </pre>
 * Components are added with the name of their pane as the constraint. A pane
 * without a component is left empty, and a component whose name is not in
 * the model is hidden.
 * <p>
 * Every split keeps the share of its length that each child currently gets.
 * Shares start from the weights, and dragging a divider moves length between
 * the two children next to it. When the container is resized, every child
 * keeps its share, and no child gets less than its minimum size as long as
 * there is room for all of them.
 */
public class MultiSplitLayout implements LayoutManager2 {

	/**
	 * A node of the layout: a named {@link Pane}, or a {@link Split} of
	 * other regions.
	 */
	public static abstract class Region {
		private double weight = 1;

		Region() {
		}

		/**
		 * Relative size of this region in its parent split. Must be positive.
		 */
		public double getWeight() {
			return weight;
		}

		/**
		 * Set the relative size of this region in its parent split.
		 * @return this region, for chaining
		 */
		public Region weight(double weight) {
			if(!(weight>0) || Double.isInfinite(weight)) {
				throw new IllegalArgumentException("Invalid weight "+weight);
			}
			this.weight = weight;
			return this;
		}
	}

	/**
	 * A leaf of the layout, holding the component added with its name.
	 */
	public static final class Pane extends Region {
		private final String name;

		Pane(String name) {
			if(name==null || name.isEmpty()) {
				throw new IllegalArgumentException("A pane needs a name");
			}
			this.name = name;
		}

		public String getName() {
			return name;
		}

		@Override
		public Pane weight(double weight) {
			super.weight(weight);
			return this;
		}
	}

	/**
	 * Regions laid out side by side (a row) or on top of each other (a column),
	 * separated by dividers.
	 */
	public static final class Split extends Region {
		private final boolean horizontal;
		private final List<Region> children;
		// Current share of the available length for each child, summing to 1
		private final double[] shares;

		Split(boolean horizontal, Region[] children) {
			if(children.length<1) {
				throw new IllegalArgumentException("A split needs at least one region");
			}
			this.horizontal = horizontal;
			this.children = Collections.unmodifiableList(new ArrayList<>(Arrays.asList(children)));
			this.shares = new double[children.length];
			resetShares();
		}

		/**
		 * True for a row (children side by side), false for a column.
		 */
		public boolean isHorizontal() {
			return horizontal;
		}

		public List<Region> getChildren() {
			return children;
		}

		/**
		 * Current fraction of the split's length given to each child.
		 */
		public double[] getShares() {
			return shares.clone();
		}

		/**
		 * Restore the shares computed from the children weights.
		 */
		public void resetShares() {
			double total = 0;
			for(Region r: children) {
				total += r.getWeight();
			}
			for(int i=0; i<shares.length; i++) {
				shares[i] = children.get(i).getWeight()/total;
			}
		}

		@Override
		public Split weight(double weight) {
			super.weight(weight);
			return this;
		}
	}

	/**
	 * A divider between two adjacent children of a split, as placed by the
	 * last layout.
	 */
	public static final class Divider {
		private final Split split;
		private final int index;
		private final Rectangle bounds;
		// Offset of the split's first child and length available to children
		private final int origin;
		private final int available;

		Divider(Split split, int index, Rectangle bounds, int origin, int available) {
			this.split = split;
			this.index = index;
			this.bounds = bounds;
			this.origin = origin;
			this.available = available;
		}

		public Split getSplit() {
			return split;
		}

		/**
		 * The divider sits between child {@code index} and {@code index+1}.
		 */
		public int getIndex() {
			return index;
		}

		public Rectangle getBounds() {
			return new Rectangle(bounds);
		}

		public boolean isHorizontal() {
			return split.isHorizontal();
		}
	}

	public static Pane pane(String name) {
		return new Pane(name);
	}

	public static Pane pane(String name, double weight) {
		return new Pane(name).weight(weight);
	}

	/**
	 * Regions laid out from left to right.
	 */
	public static Split row(Region... children) {
		return new Split(true, children);
	}

	/**
	 * Regions laid out from top to bottom.
	 */
	public static Split column(Region... children) {
		return new Split(false, children);
	}

	private Region model;
	private int dividerSize = 5;
	private final Map<String,Component> components = new LinkedHashMap<>();
	private final List<Divider> dividers = new ArrayList<>();

	public MultiSplitLayout(Region model) {
		setModel(model);
	}

	public Region getModel() {
		return model;
	}

	/**
	 * Replace the layout tree. A pane name can only be used once.
	 */
	public void setModel(Region model) {
		if(model==null) {
			throw new IllegalArgumentException("The model cannot be null");
		}
		checkNames(model, new HashSet<>());
		this.model = model;
		dividers.clear();
	}

	private static void checkNames(Region r, Set<String> names) {
		if(r instanceof Pane p) {
			if(!names.add(p.getName())) {
				throw new IllegalArgumentException("Duplicate pane name '"+p.getName()+"'");
			}
		} else {
			for(Region c: ((Split)r).getChildren()) {
				checkNames(c, names);
			}
		}
	}

	public int getDividerSize() {
		return dividerSize;
	}

	public void setDividerSize(int dividerSize) {
		if(dividerSize<0) {
			throw new IllegalArgumentException("Negative divider size");
		}
		this.dividerSize = dividerSize;
	}

	/**
	 * The component added for a pane, or null.
	 */
	public Component getComponent(String paneName) {
		return components.get(paneName);
	}

	/**
	 * The dividers placed by the last layout.
	 */
	public List<Divider> getDividers() {
		return Collections.unmodifiableList(dividers);
	}

	/**
	 * The divider under a point, or null.
	 */
	public Divider dividerAt(Point p) {
		for(Divider d: dividers) {
			if(d.bounds.contains(p)) {
				return d;
			}
		}
		return null;
	}

	/**
	 * Move a divider so that it starts at {@code position} (an x coordinate for
	 * a row, a y coordinate for a column). The length is taken from one
	 * neighbour and given to the other, within their minimum sizes.
	 * The container must be laid out again for the change to show.
	 */
	public void moveDivider(Divider d, int position) {
		Split s = d.split;
		int i = d.index;
		if(d.available<=0) {
			return;
		}
		int[] lengths = lengths(s, d.available);
		// Start of child i, and combined length of children i and i+1
		int start = d.origin;
		for(int k=0; k<i; k++) {
			start += lengths[k] + dividerSize;
		}
		int pair = lengths[i] + lengths[i+1];
		int minA = minimumLength(s.getChildren().get(i), s.isHorizontal());
		int minB = minimumLength(s.getChildren().get(i+1), s.isHorizontal());
		int a = position - start;
		if(minA+minB<=pair) {
			a = Math.max(minA, Math.min(a, pair-minB));
		} else {
			a = Math.max(0, Math.min(a, pair));
		}
		double pairShare = s.shares[i] + s.shares[i+1];
		s.shares[i] = pairShare * a / pair;
		s.shares[i+1] = pairShare - s.shares[i];
	}

	@Override
	public void addLayoutComponent(Component comp, Object constraints) {
		if(!(constraints instanceof String name)) {
			throw new IllegalArgumentException("MultiSplitLayout constraint must be a pane name");
		}
		addLayoutComponent(name, comp);
	}

	@Override
	public void addLayoutComponent(String name, Component comp) {
		if(name==null) {
			throw new IllegalArgumentException("MultiSplitLayout constraint must be a pane name");
		}
		// A new component for a pane replaces the old one in the layout
		components.values().remove(comp);
		components.put(name, comp);
	}

	@Override
	public void removeLayoutComponent(Component comp) {
		components.values().remove(comp);
	}

	@Override
	public Dimension preferredLayoutSize(Container parent) {
		return withInsets(parent, size(model, false));
	}

	@Override
	public Dimension minimumLayoutSize(Container parent) {
		return withInsets(parent, size(model, true));
	}

	@Override
	public Dimension maximumLayoutSize(Container target) {
		return new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE);
	}

	@Override
	public float getLayoutAlignmentX(Container target) {
		return 0.5f;
	}

	@Override
	public float getLayoutAlignmentY(Container target) {
		return 0.5f;
	}

	@Override
	public void invalidateLayout(Container target) {
	}

	@Override
	public void layoutContainer(Container parent) {
		synchronized(parent.getTreeLock()) {
			Insets in = parent.getInsets();
			Rectangle area = new Rectangle(in.left, in.top,
					Math.max(0, parent.getWidth()-in.left-in.right),
					Math.max(0, parent.getHeight()-in.top-in.bottom));
			dividers.clear();
			Set<Component> placed = new HashSet<>();
			place(model, area, placed);
			// Components that are not in the model are not shown
			for(Component c: parent.getComponents()) {
				if(!placed.contains(c)) {
					c.setBounds(0, 0, 0, 0);
				}
			}
		}
	}

	private void place(Region r, Rectangle area, Set<Component> placed) {
		if(r instanceof Pane p) {
			Component c = components.get(p.getName());
			if(c!=null) {
				c.setBounds(area);
				placed.add(c);
			}
			return;
		}
		Split s = (Split)r;
		boolean h = s.isHorizontal();
		int n = s.getChildren().size();
		int total = h ? area.width : area.height;
		int available = Math.max(0, total - dividerSize*(n-1));
		int origin = h ? area.x : area.y;
		int[] lengths = lengths(s, available);
		int pos = origin;
		for(int i=0; i<n; i++) {
			Rectangle child = h ? new Rectangle(pos, area.y, lengths[i], area.height)
								: new Rectangle(area.x, pos, area.width, lengths[i]);
			place(s.getChildren().get(i), child, placed);
			pos += lengths[i];
			if(i<n-1) {
				Rectangle db = h ? new Rectangle(pos, area.y, dividerSize, area.height)
								 : new Rectangle(area.x, pos, area.width, dividerSize);
				dividers.add(new Divider(s, i, db, origin, available));
				pos += dividerSize;
			}
		}
	}

	/**
	 * Split a length between the children of a split: proportional to their
	 * shares, but never under their minimum size when all the minimums fit.
	 * The lengths always add up to {@code available}.
	 */
	private int[] lengths(Split s, int available) {
		int n = s.getChildren().size();
		int[] min = new int[n];
		int minTotal = 0;
		for(int i=0; i<n; i++) {
			min[i] = minimumLength(s.getChildren().get(i), s.isHorizontal());
			minTotal += min[i];
		}
		double[] want = new double[n];
		if(minTotal>available) {
			// Not enough room: shrink proportionally to the shares
			for(int i=0; i<n; i++) {
				want[i] = s.shares[i]*available;
			}
		} else {
			// Children held at their minimum are fixed, the others share the rest
			boolean[] fixed = new boolean[n];
			boolean changed = true;
			while(changed) {
				changed = false;
				double freeShare = 0;
				int freeLength = available;
				for(int i=0; i<n; i++) {
					if(fixed[i]) {
						freeLength -= min[i];
					} else {
						freeShare += s.shares[i];
					}
				}
				for(int i=0; i<n; i++) {
					if(fixed[i]) {
						want[i] = min[i];
					} else {
						want[i] = freeShare>0 ? freeLength*s.shares[i]/freeShare : 0;
						if(want[i]<min[i]) {
							fixed[i] = true;
							changed = true;
						}
					}
				}
			}
		}
		// Round so that the total is exact: the fractional parts go to the
		// children with the largest remainders
		int[] lengths = new int[n];
		int used = 0;
		for(int i=0; i<n; i++) {
			lengths[i] = (int)Math.floor(want[i]);
			used += lengths[i];
		}
		while(used<available) {
			int best = 0;
			double bestRest = -1;
			for(int i=0; i<n; i++) {
				double rest = want[i]-lengths[i];
				if(rest>bestRest) {
					bestRest = rest;
					best = i;
				}
			}
			lengths[best]++;
			want[best] = lengths[best];
			used++;
		}
		return lengths;
	}

	private int minimumLength(Region r, boolean horizontal) {
		Dimension d = size(r, true);
		return horizontal ? d.width : d.height;
	}

	private Dimension size(Region r, boolean minimum) {
		if(r instanceof Pane p) {
			Component c = components.get(p.getName());
			if(c==null || !c.isVisible()) {
				return new Dimension(0, 0);
			}
			return minimum ? c.getMinimumSize() : c.getPreferredSize();
		}
		Split s = (Split)r;
		int along = dividerSize*(s.getChildren().size()-1);
		int across = 0;
		for(Region c: s.getChildren()) {
			Dimension d = size(c, minimum);
			if(s.isHorizontal()) {
				along += d.width;
				across = Math.max(across, d.height);
			} else {
				along += d.height;
				across = Math.max(across, d.width);
			}
		}
		return s.isHorizontal() ? new Dimension(along, across) : new Dimension(across, along);
	}

	private static Dimension withInsets(Container parent, Dimension d) {
		Insets in = parent.getInsets();
		return new Dimension(d.width+in.left+in.right, d.height+in.top+in.bottom);
	}
}
