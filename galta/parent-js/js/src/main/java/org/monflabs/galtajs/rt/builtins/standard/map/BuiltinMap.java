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
package org.monflabs.galtajs.rt.builtins.standard.map;

import org.monflabs.galtajs.rt.RuntimeUtil;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.jsonfactory.internal.JSObjectInternal;
import org.monflabs.galtajs.rt.builtins.JSAccessor;
import org.monflabs.galtajs.rt.builtins.PropertiesHolder;
import org.monflabs.galtajs.rt.builtins.standard.set.SetLike;


/**
 * JavaScript native Map
 */
public class BuiltinMap implements Map<Object,Object>, SetLike, PropertiesHolder {

	private JavaScriptMap map;
	private JSObjectInternal properties;

	public BuiltinMap(JSEnvironment env){
		this.map = new JavaScriptMap(env, env.supportMixedBigNumber());
	}

	private BuiltinMap(JavaScriptMap map) {
		this.map = map;
	}
	
	@Override
	public JSAccessor createAccessor(JSEnvironment env) {
		return new MapAccessor(env);
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
	public BuiltinMap clone() {
		return new BuiltinMap((JavaScriptMap) map.clone());
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
	public boolean containsKey(Object key) {
		return map.containsKey(key);
	}

	@Override
	public boolean containsValue(Object value) {
		return map.containsValue(value);
	}

	@Override
	public Object get(Object key) {
		return map.get(key);
	}

	@Override
	public Object getOrDefault(Object key, Object defaultValue) {
		return map.getOrDefault(key, defaultValue);
	}

	@Override
	public Object put(Object key, Object value) {
		// Spec: a -0 key is stored as +0 (in every method that can insert a key)
		return map.put(map.canonicalKey(key), value);
	}

	@Override
	public void putAll(Map<? extends Object, ? extends Object> m) {
		for(Map.Entry<? extends Object, ? extends Object> e: m.entrySet()) {
			put(e.getKey(), e.getValue());
		}
	}

	@Override
	public Object remove(Object key) {
		return map.remove(key);
	}

	@Override
	public void clear() {
		map.clear();
	}

	@Override
	public Set<Object> keySet() {
		return map.keySet();
	}

	@Override
	public int hashCode() {
		return map.hashCode();
	}

	@Override
	public Collection<Object> values() {
		return map.values();
	}

	@Override
	public String toString() {
		return map.toString();
	}

	@Override
	public Set<Entry<Object, Object>> entrySet() {
		return map.entrySet();
	}

	@Override
	public void forEach(BiConsumer<? super Object, ? super Object> action) {
		map.forEach(action);
	}

	@Override
	public void replaceAll(BiFunction<? super Object, ? super Object, ? extends Object> function) {
		map.replaceAll(function);
	}

	@Override
	public Object putIfAbsent(Object key, Object value) {
		return map.putIfAbsent(map.canonicalKey(key), value);
	}

	@Override
	public boolean remove(Object key, Object value) {
		return map.remove(key, value);
	}

	@Override
	public boolean replace(Object key, Object oldValue, Object newValue) {
		return map.replace(key, oldValue, newValue);
	}

	@Override
	public Object replace(Object key, Object value) {
		return map.replace(key, value);
	}

	@Override
	public Object computeIfAbsent(Object key, Function<? super Object, ? extends Object> mappingFunction) {
		return map.computeIfAbsent(map.canonicalKey(key), mappingFunction);
	}

	@Override
	public Object computeIfPresent(Object key,
			BiFunction<? super Object, ? super Object, ? extends Object> remappingFunction) {
		return map.computeIfPresent(map.canonicalKey(key), remappingFunction);
	}

	@Override
	public Object compute(Object key, BiFunction<? super Object, ? super Object, ? extends Object> remappingFunction) {
		return map.compute(map.canonicalKey(key), remappingFunction);
	}

	@Override
	public Object merge(Object key, Object value,
			BiFunction<? super Object, ? super Object, ? extends Object> remappingFunction) {
		return map.merge(map.canonicalKey(key), value, remappingFunction);
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
