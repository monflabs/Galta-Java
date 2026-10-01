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
package org.monflabs.json.jsonpath;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

import org.monflabs.json.JsonArray;
import org.monflabs.json.JsonObject;
import org.monflabs.json.jsonpointer.JsonPointer;

/**
 * Deep scan the item.
 * 
 * @author priand
 */
public class PathDeepScan extends PathNode {
	
	public PathDeepScan(PathNode next) {
		super(next);
	}
	
	@Override
	public void addCanonicalPath(StringBuilder b) {
		b.append("..");
	}
	
	@Override
	public boolean isDefinite() {
		return false;
	}
	
	@Override
	public void execute(JsonValues r, Object root, Object source, JsonPointer sourcePointer) {
		scan(r, source, sourcePointer, null);
	}
	
	/**
	 * Add the value and its descendants. A container that is one of its own ancestors (a
	 * cyclic graph, e.g. resolved recursive references) is skipped: the back edge is not
	 * followed. Shared, non cyclic containers are visited at each location, as they are
	 * distinct nodes for JSON Path.
	 */
	private void scan(JsonValues r, Object source, JsonPointer sourcePointer, Set<Object> ancestors) {
		boolean container = source instanceof JsonObject || source instanceof JsonArray;
		if(container) {
			if(ancestors==null) {
				ancestors = Collections.newSetFromMap(new IdentityHashMap<>());
			}
			if(!ancestors.add(source)) {
				return;
			}
		}
		if(sourcePointer!=null) {
			r._add(source, sourcePointer);
		} else {
			r._add(source);
		}
		if(source instanceof JsonObject o) {
			for(Map.Entry<String,Object> e: o.entrySet()) {
				scan(r, e.getValue(), sourcePointer!=null ? sourcePointer.getChild(e.getKey()) : null, ancestors);
			}
		} else if(source instanceof JsonArray a) {
			int sz = a.size();
			for(int i=0; i<sz; i++) {
				scan(r, a.get(i), sourcePointer!=null ? sourcePointer.getChild(i) : null, ancestors);
			}
		}
		if(container) {
			ancestors.remove(source);
		}
	}
}
