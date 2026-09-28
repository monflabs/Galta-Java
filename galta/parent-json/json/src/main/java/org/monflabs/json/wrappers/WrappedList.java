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

import java.util.AbstractList;
import java.util.Collection;
import java.util.function.Function;

import org.monflabs.json.JsonArray;

/**
 * 
 * @author priand
 *
 */
public class WrappedList<T extends JsonWrapper> extends AbstractList<T> implements JsonWrapper {
	
	private JsonArray jsonArray;
	private Function<Object,T> factory;
	
	public WrappedList(JsonArray jsonObject, Function<Object,T> factory) {
		this.jsonArray = jsonObject;
		this.factory = factory;
	}

	@Override
	public JsonArray wrapped() {
		return jsonArray;
	}

	// equals() and hashCode() are the List ones (AbstractList): the wrappers compare
	// by their wrapped values
	
	@Override
	public String toString() {
		return jsonArray.toString();
	}

	//
	// AbstractList methods that must be implemented
	//

	@Override
	public int size() {
		return jsonArray.size();
	}

	@Override
	public T get(int index) {
		Object v = jsonArray.get(index);
		return v!=null ? factory.apply(v) : null;
	}

    @Override
	public boolean add(T value) {
		return jsonArray.add(value!=null?value.wrapped():null); 
    }

    @Override
	public T set(int index, T value) {
		Object old = jsonArray.set(index,value!=null?value.wrapped():null); 
		return old!=null ? factory.apply(old) : null;
    }

    @Override
	public void add(int index, T value) {
		jsonArray.add(index,value!=null?value.wrapped():null); 
    }

    @Override
	public T remove(int index) {
		Object old = jsonArray.remove(index); 
		return old!=null ? factory.apply(old) : null;
    }
       
	@Override
	public boolean addAll(Collection<? extends T> c) {
		if(c instanceof WrappedList<?> l) {
			return jsonArray.addAll(l.wrapped());
		}
		boolean modified = false;
        for (T t : c) {
            add(t);
            modified = true;
        }
        return modified;
    }
}
