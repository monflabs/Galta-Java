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
public class StringArrayLookup extends AbstractLookup<String> {
    
    private String[] values;
    private String[] labels;
    
    public StringArrayLookup(String...values) {
        this.values = values;
    }

    public StringArrayLookup(String[] values, String[] labels) {
        this.values = values;
        this.labels = labels;
    }

    @Override
	public int size() {
        return values!=null ? values.length : 0;
    }

    @Override
	public String getValue(int index) {
    	if(index<0) {
    		return null;
    	}
    	if(index>=size()) {
    		throw new IndexOutOfBoundsException(index);
    	}
        return values[index];
    }

	public void setValues(String[] values) {
		setValues(values, null);
    }

	public void setValues(String[] values, String[] labels) {
        this.values = values;
        this.labels = labels;
        notifyLookupChanged();
    }

    @Override
	public String getDisplayLabel(int index) {
    	if(index<0) {
    		return "";
    	}
    	if(index>=size()) {
    		throw new IndexOutOfBoundsException(index);
    	}
    	if(labels!=null && index<labels.length) {
    		return labels[index];
    	}
		return super.getDisplayLabel(index);
    }
}
