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
package org.monflabs.galtajs.transpiler.util;

/**
 * List of primitive int values.
 */ 
public final class IntList {
    
	private int[] elementData; 
    private int size;

    public static final int DEFAULT_CAPACITY = 64;

    public IntList() {
        this(DEFAULT_CAPACITY);
    }

    public IntList(int capacity) {
        this.elementData = new int[capacity];
    }

	public int size() {
        return size;
    }

	public int get(int index) {
        checkIndex(index);
        return elementData[index];
    }
    @Override
	public String toString() {
    	StringBuilder b = new StringBuilder(size*3+16);
    	b.append('[');
    	for(int i=0; i<size; i++) {
    		if(i>0) {
    			b.append(',');
    		}
			b.append(Integer.toString(elementData[i]));
    	}
    	b.append(']');
        return b.toString();
    }

    public int indexOf(int value) {
        for( int i=0; i<size; i++) {
            if (elementData[i] == value) {
                return i;
            }
        }
        return -1;
    }

	public boolean isEmpty() {
        return size==0;
    }

    public boolean contains(int value) {
        return indexOf(value) != -1;
    }

    protected void checkIndex(int index) {
        if (index < 0 || index >= size()) {
            throw new IndexOutOfBoundsException("Invalid index: " + index);
        }
    }

    public void add(int value) {
        ensureCapacity(size + 1);
        elementData[size] = value;
        size++;
    }

    public void add(int index, int value) {
        ensureCapacity(size + 1);
        if(index<size) {
        	System.arraycopy(elementData,index, elementData, index+1, size-index);
        }
        elementData[index] = value;
        size++;
    }

	public void remove(int index) {
        checkIndex(index);
    	System.arraycopy(elementData,index+1, elementData, index, size-index-1);
        size--;
    }

    public void set(int index, int value) {
        checkIndex(index);
        elementData[index] = value;
    }

	public void clear() {
        size = 0;
    }

    private void ensureCapacity(int capacity) {
        if (capacity > elementData.length) {
        	capacity = Math.max(capacity, elementData.length*2 +1 );
            int[] tempData = new int[capacity];
        	System.arraycopy(elementData,0, tempData, 0, size);
            elementData = tempData;
        }
    }
}