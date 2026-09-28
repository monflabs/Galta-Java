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
package org.monflabs.json.wrappers;

import org.monflabs.json.JsonArray;

/**
 * 
 * @author priand
 *
 */
public class JsonArrayWrapperImpl implements JsonWrapper {
	
	private JsonArray wrapped;

	public JsonArrayWrapperImpl(JsonArray wrapped) {
		this.wrapped = wrapped;
	}
	
	@Override
	public JsonArray wrapped() {
		return wrapped;
	}
	
	// Two wrappers of the same kind are equal when they wrap equal values, so the
	// collections of wrappers (WrappedMap, WrappedList) follow the Map/List contracts
	@Override
	public boolean equals(Object o) {
		if(this==o) {
			return true;
		}
		if(o!=null && o.getClass()==getClass()) {
			return java.util.Objects.equals(wrapped, ((JsonArrayWrapperImpl)o).wrapped);
		}
		return false;
	}
	
	@Override
	public int hashCode() {
		return wrapped!=null ? wrapped.hashCode() : 0;
	}
}
