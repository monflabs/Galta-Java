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
package org.monflabs.ui.swing.components.models;

import javax.swing.AbstractListModel;
import javax.swing.SwingUtilities;
import javax.swing.event.ListDataListener;

import org.monflabs.ui.lookup.ILookup;
import org.monflabs.ui.lookup.ILookupChangeListener;

/**
 * A list model over a lookup, which follows the lookup's changes. It only
 * listens to the lookup while the model itself has listeners, so a model
 * dropped by its list is not kept alive by a long-lived lookup.
 */
@SuppressWarnings("serial")
public class LookupListModel<T> extends AbstractListModel<T> {

	private final ILookup<T> lookup;
	private final ILookupChangeListener<T> lookupListener = l -> lookupChanged();

	public LookupListModel(ILookup<T> lookup) {
		this.lookup = lookup;
	}

	@Override
	public int getSize() {
		return lookup.size();
	}

	@Override
	public T getElementAt(int index) {
		return lookup.getValue(index);
	}

	@Override
	public void addListDataListener(ListDataListener l) {
		if(getListDataListeners().length==0) {
			lookup.addLookupChangeListener(lookupListener);
		}
		super.addListDataListener(l);
	}

	@Override
	public void removeListDataListener(ListDataListener l) {
		super.removeListDataListener(l);
		if(getListDataListeners().length==0) {
			lookup.removeLookupChangeListener(lookupListener);
		}
	}

	private void lookupChanged() {
		if(!SwingUtilities.isEventDispatchThread()) {
			SwingUtilities.invokeLater(this::lookupChanged);
			return;
		}
		// The whole content may have changed, size included
		fireContentsChanged(this, 0, Math.max(0, getSize()-1));
	}
}
