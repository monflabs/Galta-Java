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
package org.monflabs.json.model;

import org.monflabs.json.JsonArray;
import org.monflabs.json.JsonObject;
import org.monflabs.util.model.ModelAccessor;
import org.monflabs.util.model.ModelException;



/**
 * Model data accessor for JSON object. 
 */
public class JsonModelAccessor implements ModelAccessor {

	private boolean useUnhandled;
	
	public JsonModelAccessor() {
	}
	
	public boolean isUseUnhandled() {
		return useUnhandled;
	}

	public void setUseUnhandled(boolean useUnhandled) {
		this.useUnhandled = useUnhandled;
	}

	//
	// Member value access
	//
	@Override
	public Object getMember(Object instance, String member) throws ModelException {
		if(instance instanceof JsonObject jo) {
			return jo.get(member);
		}
		return useUnhandled ? UNHANDLED : null;
	}
	@Override
	public boolean putMember(Object instance, String member, Object value) throws ModelException {
		if(instance instanceof JsonObject jo) {
			jo.put(member,value);
			return true;
		}
		return false;
	}
	
	//
	// Member indexed value access
	//
	@Override
	public Object getMember(Object instance, int index) throws ModelException {
		if(instance instanceof JsonArray ja) {
			// Like a missing member: null for an index outside the array (and no
			// negative index, as putMember() rejects them)
			return index>=0 && index<ja.size() ? ja.get(index) : null;
		}
		return useUnhandled ? UNHANDLED : null;
	}
	@Override
	public boolean putMember(Object instance, int index, Object value) throws ModelException {
		if(instance instanceof JsonArray ja) {
			if(index<0) {
				return false;
			}
			// Set at the index (growing the array if needed), like PojoAccessor - not append
			ja.growTo(index+1);
			ja.set(index, value);
			return true;
		}
		return false;
	}
	
	//
	// Object construction
	//
	@Override
	public Object constructObject(String type, Object[] parameters) throws ModelException {
		return JsonObject.create();
	}
	@Override
	public Object constructArray(String type, int size) throws ModelException {
		return JsonArray.create();
	}

	// Method call
	@Override
	public Object call(Object instance, String methodName, Object[] parameters) throws ModelException {
		return useUnhandled ? UNHANDLED : null;
	}
}