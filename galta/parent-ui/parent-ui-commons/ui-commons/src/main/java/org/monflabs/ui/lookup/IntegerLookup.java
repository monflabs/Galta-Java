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
 * 
 */
public class IntegerLookup extends AbstractLookup<Integer> {
    
    private int[] codes;
    private String[] labels;
    
    public IntegerLookup(int[] codes) {
        this.codes = codes;
    }

    public IntegerLookup(int[] codes, String[] labels) {
        this.codes = codes;
        this.labels = labels;
    }

    @Override
	public int size() {
        return codes!=null ? codes.length : 0;
    }

    @Override
	public Integer getValue(int index) {
    	if(index<0 || index>=size()) {
    		throw new IndexOutOfBoundsException(index);
    	}
        return codes[index];
    }

    @Override
	public String getDisplayLabel(int index) {
    	if(index<0 || index>=size()) {
    		throw new IndexOutOfBoundsException(index);
    	}
    	if(labels!=null && index<labels.length) {
    		return labels[index];
    	}
    	return Integer.toString(codes[index]);
    }
}
