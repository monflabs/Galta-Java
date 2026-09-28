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

import java.util.function.Predicate;

import javax.swing.JTree;
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
	
	public static void expandAllNodes(JTree tree) {
		int j = tree.getRowCount();
		int i = 0;
		while (i < j) {
			tree.expandRow(i);
			i += 1;
			j = tree.getRowCount();
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
