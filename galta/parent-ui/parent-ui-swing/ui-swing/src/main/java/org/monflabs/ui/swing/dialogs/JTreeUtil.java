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
package org.monflabs.ui.swing.dialogs;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import javax.swing.JTree;
import javax.swing.plaf.TreeUI;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;

/**
 * JTree utilities.
 * 
 * @author priand
 */
public class JTreeUtil {
	
	public static void expandNode(JTree tree, TreePath path) {
		tree.setSelectionPath(path);
		tree.makeVisible(path);
		tree.setSelectionRow(tree.getRowForPath(path));
		tree.scrollPathToVisible(path);
	}
	
	/**
	 * Expands every node of the tree.
	 * <p>
	 * Expanding row by row made the tree's UI recompute its layout after each
	 * row: quadratic in the number of nodes (seconds on the event dispatch
	 * thread for a 20 000 node tree). Here the expansion state is set with the
	 * UI detached, and the UI rebuilds its layout once when it is attached
	 * back.
	 * <p>
	 * The rebuild still measures every row with the cell renderer, unless the
	 * tree has a fixed row height and a large model ({@code setRowHeight(n)},
	 * {@code setLargeModel(true)}): set them for a tree of thousands of nodes
	 * (20 000 nodes then expand in a fraction of a second).
	 */
	public static void expandAllNodes(JTree tree) {
		expandAllNodes(tree, Integer.MAX_VALUE);
	}

	/**
	 * Expands the nodes of the tree down to a depth: 1 expands the root only
	 * (its children are shown), 2 the root and its children, and so on.
	 */
	public static void expandAllNodes(JTree tree, int maxDepth) {
		TreeModel model = tree.getModel();
		Object root = model!=null ? model.getRoot() : null;
		if(root==null || maxDepth<1) {
			return;
		}
		// The expandable paths, parents first
		List<TreePath> paths = new ArrayList<>();
		ArrayDeque<TreePath> stack = new ArrayDeque<>();
		stack.push(new TreePath(root));
		while(!stack.isEmpty()) {
			TreePath path = stack.pop();
			Object node = path.getLastPathComponent();
			if(model.isLeaf(node) || path.getPathCount()>maxDepth) {
				continue;
			}
			paths.add(path);
			int count = model.getChildCount(node);
			for(int i=count-1; i>=0; i--) {
				stack.push(path.pathByAddingChild(model.getChild(node, i)));
			}
		}
		TreeUI ui = tree.getUI();
		tree.setUI(null);
		try {
			for(TreePath p: paths) {
				tree.expandPath(p);
			}
		} finally {
			if(ui!=null) {
				tree.setUI(ui);
			} else {
				tree.updateUI();
			}
		}
	}
	

    public static void expandNodes(JTree tree, Predicate<TreePath> predicate) {
    	TreeModel model = tree.getModel();
        TreePath rootPath = new TreePath(model.getRoot());
        expandNode(tree, model, rootPath, model.getRoot(), predicate);
    }
    private static void expandNode(JTree tree, TreeModel treeModel, TreePath path, Object value, Predicate<TreePath> predicate) {
        if (predicate.test(path)) {
            tree.expandPath(path);
	        if(!treeModel.isLeaf(value)) {
	        	int count = treeModel.getChildCount(value);
	            for (int i = 0; i < count; i++) {
	                Object child = treeModel.getChild(value,i);
	                TreePath childPath = path.pathByAddingChild(child);
	                expandNode(tree, treeModel, childPath, child, predicate);
	            }
	        }
        }
    }
}
