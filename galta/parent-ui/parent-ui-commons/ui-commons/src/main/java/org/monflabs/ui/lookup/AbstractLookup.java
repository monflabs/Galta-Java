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
package org.monflabs.ui.lookup;

import java.util.ArrayList;
import java.util.List;


/**
 * 
 */
public abstract class AbstractLookup<T> implements ILookup<T> {

    private ArrayList<ILookupChangeListener<T>> listeners;
    
    protected AbstractLookup() {
    }
    
    protected List<ILookupChangeListener<T>> getListeners() {
    	return listeners;
    }

	@Override
	public String getDisplayLabel(int index) {
		T v = getValue(index);
		return _valueToLabel(index, v);
	}

	protected String _valueToLabel(int index, Object value) {
		return value!=null ? value.toString() : null;
	}

    @Override
	public void addLookupChangeListener(ILookupChangeListener<T> listener) {
        if(listeners==null) {
            listeners = new ArrayList<ILookupChangeListener<T>>();
        }
        listeners.add(listener);
    }

    @Override
	public void removeLookupChangeListener(ILookupChangeListener<T> listener) {
        if(listeners!=null) {
            listeners.remove(listener);
        }
    }
    
    public void notifyLookupChanged() {
        if(listeners!=null) {
        	// Iterate over a copy: a listener can remove itself while being notified
        	for(ILookupChangeListener<T> l: new ArrayList<>(listeners)) {
        		l.lookupChanged(this);
        	}
        }
    }
}

