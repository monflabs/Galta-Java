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
package org.monflabs.galtajs.rt.builtins.standard.set;

import org.monflabs.galtajs.rt.RuntimeUtil;
import java.util.Collection;
import java.util.Iterator;
import java.util.Set;
import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.jsonfactory.internal.JSObjectInternal;
import org.monflabs.galtajs.rt.builtins.JSAccessor;
import org.monflabs.galtajs.rt.builtins.PropertiesHolder;
import org.monflabs.galtajs.rt.builtins.standard.map.JavaScriptMap;


/**
 * JavaScript Set
 */
public class BuiltinSet implements Set<Object>, SetLike, PropertiesHolder {
	
	private JavaScriptMap map;
	private JSObjectInternal properties;

	public BuiltinSet(JSEnvironment env) {
		this.map = new JavaScriptMap(env.supportMixedBigNumber());
	}

	@Override
	public JSObjectInternal getPropertiesObject() {
		return properties;
	}
	@Override
	public void setPropertiesObject(JSObjectInternal properties) {
		this.properties = properties;
	}

	@Override
	public JSAccessor createAccessor(JSEnvironment env) {
		return new SetAccessor(env);
	}

	@Override
	public void forEach(Consumer<? super Object> action) {
		map.keySet().forEach(action);
	}

	@Override
	public int size() {
		return map.size();
	}

	@Override
	public boolean isEmpty() {
		return map.isEmpty();
	}

	@Override
	public boolean contains(Object o) {
		return map.containsKey(o);
	}

	@Override
	public Iterator<Object> iterator() {
		return map.keySet().iterator();
	}

	@Override
	public Object[] toArray() {
		return map.keySet().toArray();
	}

	@Override
	public <T> T[] toArray(T[] a) {
		return map.keySet().toArray(a);
	}

	@Override
	public boolean add(Object e) {
		// Spec: a -0 value is stored as +0
		e = RuntimeUtil.canonicalizeKeyedCollectionKey(e);
		if(!map.containsKey(e)) {
			map.put(e,null);
			return true;
		}
		return false;
	}

	@Override
	public boolean remove(Object o) {
		if(map.containsKey(o)) {
			map.remove(o);
			return true;
		}
		return false;
	}

	@Override
	public boolean containsAll(Collection<?> c) {
		return map.keySet().containsAll(c);
	}

	@Override
	public boolean addAll(Collection<? extends Object> c) {
		boolean res = false;
		for(Object v: c) {
			res |= add(v);
		}
		return res;
	}

	@Override
	public boolean retainAll(Collection<?> c) {
		if(size()==0) {
			return false;
		}
		if(c.size()==0) {
			clear();
			return true;
		}
		// We have to use a copy so the right equals() operation is used
		JavaScriptMap cmap = new JavaScriptMap(map.isMixedBigNumbers());
		for(Object k: c) {
			cmap.put(k,null);
		}
		boolean res = false;
		for(Iterator<Object> it=map.keySet().iterator(); it.hasNext(); )  {
			Object k = it.next();
			if(!cmap.containsKey(k)) {
				it.remove();
				res = true;
			}
		}
		return res;
	}

	@Override
	public boolean removeAll(Collection<?> c) {
		boolean res = false;
		for(Object v: c) {
			res |= remove(v);
		}
		return res;
	}

	@Override
	public void clear() {
		map.clear();
	}

	@Override
	public boolean equals(Object o) {
		if(o==this) {
			return true;
		}
		if(o instanceof Set s) {
			if(map.size()!=s.size()) {
				return false;
			}
			for(Object k: s) {
				if(!map.containsKey(k)) {
					return false;
				}
			}
			return true;
		}
		return false;
	}

	@Override
	public int hashCode() {
		return map.hashCode();
	}

	@Override
	public Spliterator<Object> spliterator() {
		return map.keySet().spliterator();
	}

	@Override
	public <T> T[] toArray(IntFunction<T[]> generator) {
		return map.keySet().toArray(generator);
	}

	@Override
	public boolean removeIf(Predicate<? super Object> filter) {
		boolean res = false;
		for(Iterator<Object> it=map.keySet().iterator(); it.hasNext(); )  {
			Object k = it.next();
			if(filter.test(k)) {
				it.remove();
				res = true;
			}
		}
		return res;
	}

	@Override
	public Stream<Object> stream() {
		return map.keySet().stream();
	}

	@Override
	public Stream<Object> parallelStream() {
		return map.keySet().parallelStream();
	}

	
	
	//
	// JSSet
	//
	@Override
	public long jsSize() {
		return map.size();
	}
	@Override
	public boolean jsHas(Object v) {
		return map.containsKey(v);
	}
	@Override
	public Iterator<Object> jsKeys() {
		return map.keySet().iterator();
	}
}
