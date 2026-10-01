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


/**
 * A list of values, with their display labels (a combo box, a list).
 * <p>
 * The index contract: {@code -1} means "no selection" - {@link #getValue(int)}
 * returns null and {@link #getDisplayLabel(int)} an empty string; any other
 * index outside {@code [0, size())} throws an {@link IndexOutOfBoundsException}.
 */
public interface ILookup<T> {

    public int size();

    /**
     * The value at an index, null for -1.
     *
     * @throws IndexOutOfBoundsException when the index is not -1 nor in {@code [0, size())}
     */
    public T getValue(int index);

    /**
     * The label displayed for the value at an index, an empty string for -1.
     *
     * @throws IndexOutOfBoundsException when the index is not -1 nor in {@code [0, size())}
     */
    public String getDisplayLabel(int index);
    
    public void addLookupChangeListener(ILookupChangeListener<T> listener);

    public void removeLookupChangeListener(ILookupChangeListener<T> listener);
    
    /**
     * The index of a value, or -1 when it is not in the lookup (or null).
     */
    public default int find(T value) {
    	if(value!=null) {
	    	int sz = size();
	    	for(int i=0; i<sz; i++) {
	    		T v = getValue(i);
	    		if(value.equals(v)) {
	    			return i;
	    		}
	    	}
    	}
    	return -1;
    }
}
