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
package org.monflabs.galtajs.jsonfactory;

/**
 * Specialized map to store JSON object.
 * Allows the use of property descriptors to manage the access.
 */
public class StringPropertyMap extends ObjectPropertiesMap<String> {

	public static final int STATE_HASNUMBERPROP		= 0x0001;
	public static final int STATE_HASNUMBERSETTER	= 0x0002;
	
	private int state;
	
    public StringPropertyMap() {
    }

	public boolean mayHaveNumberProp() {
		return state!=0 && (state&STATE_HASNUMBERPROP)!=0;
	}
	public boolean mayHaveNumberPropSetter() {
		return state!=0 && (state&STATE_HASNUMBERSETTER)!=0;
	}
	
	@Override
	protected boolean separateIntegerKeys() {
		return true;
	}

	@Override
    protected void entryAdded(EntryImpl<String> entry) {
		if(entry.getLongKey()>=0) {
			state |= STATE_HASNUMBERPROP;
    		if(entry.getPropertyDescriptor().getSetter()!=null) {
    			state |= STATE_HASNUMBERSETTER;
    		}
		}
    }
	
	@Override
	public void clear() {
		super.clear();
		state = 0;
	}
}
