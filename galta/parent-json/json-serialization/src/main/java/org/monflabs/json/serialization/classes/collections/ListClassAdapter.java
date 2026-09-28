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
package org.monflabs.json.serialization.classes.collections;

import java.util.ArrayList;
import java.util.List;

import org.monflabs.json.JsonArray;
import org.monflabs.json.JsonException;
import org.monflabs.json.serialization.ClassAdapter;
import org.monflabs.json.serialization.CycleGuard;
import org.monflabs.json.serialization.JsonRegistry;
import org.monflabs.json.serialization.classes.BaseClassAdapter;

/**
 * Adapter for <code>java.util.List</code>, serialized as a JSON array.
 * <p>
 * The element adapter is the first generic parameter; without one (a raw list, or a list
 * serialized at the top level), the elements are kept as is.
 */
public class ListClassAdapter extends BaseClassAdapter {
	
	private Class<? extends List<?>> listClass;
	private ClassAdapter objectAdapter;
	
	public ListClassAdapter() {
		this(null);
	}
	public ListClassAdapter(Class<? extends List<?>> listClass) {
		super(List.class);
		this.listClass = listClass;
	}
	
	@Override
	public void init(JsonRegistry registry) {
		super.init(registry);
		this.objectAdapter = registry.findAdapter(Object.class);
	}
	
	@Override
	public Object serialize(Object value, ClassAdapter[] genericParams) {
		if(value!=null) {
			ClassAdapter item = CollectionUtil.params(genericParams, 1, objectAdapter)[0];
			List<?> l = (List<?>)value;
			CycleGuard.enter(value);
			try {
				JsonArray a = JsonArray.create(l.size());
				for(Object v: l) {
					a.add(item.serialize(v));
				}
				return a;
			} finally {
				CycleGuard.exit(value);
			}
		}
		return null;
	}

	@Override
	public Object deserialize(Object jsonValue, ClassAdapter[] genericParams) {
		if(jsonValue instanceof JsonArray a) {
			ClassAdapter item = CollectionUtil.params(genericParams, 1, objectAdapter)[0];
			try {
				int len = a.size();
				List<Object> v = createCollection();
				for(int i=0; i<len; i++) {
					v.add(item.deserialize(a.get(i)));
				}
				return v;
			} catch(InstantiationException|IllegalAccessException e) {
				throw new JsonException(e,"Cannot instanciate class {0}",listClass);
			}
		} else if(jsonValue==null) {
			return null;
		} else {
			throw new JsonException(null,"JsonValue is not an array");
		}
	}
	
	protected List<Object> createCollection() throws InstantiationException, IllegalAccessException {
		return listClass!=null ? CollectionUtil.newInstance(listClass) 
				               : new ArrayList<Object>();
	}
}
