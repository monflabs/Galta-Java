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
package org.monflabs.galtajs.rt.builtins;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.monflabs.util.iterators.Iterators;


/**
 * GlobalVariables - not optimized yet
 */
public class GlobalVariables {

	private Map<String,Integer> map = new HashMap<String, Integer>();
	private Object[] variables;
	
	public GlobalVariables() {
	}

	public void set(Object[] variables, String[] varNames) {
		this.variables = variables;
		int l = varNames.length;
		for(int i=0; i<l; i++) {
			map.put(varNames[i], i);
		}
	}
	
	public boolean containsKey(String key) {
		if(map==null) {
			return false;
		}
		return map.containsKey(key);
	}
	
	public Object get(String key) {
		return getOrDefault(key, null);
	}
	
	public Object getOrDefault(String key, Object defaultValue) {
		if(map==null) {
			return defaultValue;
		}
		Integer i = map.get(key);
		if(i!=null) {
			return variables[i];
		}
		return defaultValue;
	}

	public boolean put(String key, Object value) {
		if(map==null) {
			return false;
		}
		Integer i = map.get(key);
		if(i!=null) {
			variables[i] = value;
			return true;
		}
		return false;
	}
	
	public Iterator<String> keys() {
		if(map==null) {
			return Iterators.empty();
		}
		return map.keySet().iterator();
	}
}
