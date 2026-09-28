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

import java.util.LinkedHashSet;
import java.util.Set;

import org.monflabs.json.JsonArray;
import org.monflabs.json.JsonException;
import org.monflabs.json.serialization.ClassAdapter;
import org.monflabs.json.serialization.CycleGuard;
import org.monflabs.json.serialization.JsonRegistry;
import org.monflabs.json.serialization.classes.BaseClassAdapter;

/**
 * Adapter for <code>java.util.Set</code>, serialized as a JSON array.
 * <p>
 * Read back as a <code>LinkedHashSet</code> by default, so the order of the array is kept.
 */
public class SetClassAdapter extends BaseClassAdapter {
	
	private Class<? extends Set<?>> setClass;
	private ClassAdapter objectAdapter;
	
	public SetClassAdapter() {
		this(null);
	}
	public SetClassAdapter(Class<? extends Set<?>> setClass) {
		super(Set.class);
		this.setClass = setClass;
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
			Set<?> l = (Set<?>)value;
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
				Set<Object> v = createCollection();
				for(int i=0; i<len; i++) {
					v.add(item.deserialize(a.get(i)));
				}
				return v;
			} catch(InstantiationException|IllegalAccessException e) {
				throw new JsonException(e,"Cannot instanciate class {0}",setClass);
			}
		} else if(jsonValue==null) {
			return null;
		} else {
			throw new JsonException(null,"JsonValue is not an array");
		}
	}
	
	protected Set<Object> createCollection() throws InstantiationException, IllegalAccessException {
		return setClass!=null ? CollectionUtil.newInstance(setClass) 
							  : new LinkedHashSet<Object>();
	}
}
