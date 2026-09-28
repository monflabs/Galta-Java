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

import java.util.Map;

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
		if(sourcePointer!=null) {
			r._add(source, sourcePointer);
		} else {
			r._add(source);
		}
		if(source instanceof JsonObject o) {
			for(Map.Entry<String,Object> e: o.entrySet()) {
				if(sourcePointer!=null) {
					execute(r, root, e.getValue(), sourcePointer.getChild(e.getKey()));
				} else {
					execute(r, root, e.getValue(), null);
				}
			}
		} else if(source instanceof JsonArray a) {
			int sz = a.size();
			for(int i=0; i<sz; i++) {
				if(sourcePointer!=null) {
					execute(r, root, a.get(i), sourcePointer.getChild(i));
				} else {
					execute(r, root, a.get(i), null);
				}
			}
		}
	}
}
