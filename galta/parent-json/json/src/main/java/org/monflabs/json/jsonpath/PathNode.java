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

import org.monflabs.json.jsonpointer.JsonPointer;

/**
 * Base class for a JsonPath node.
 * 
 * @author priand
 */
public abstract class PathNode {
	
	private PathNode next;
	
	public PathNode(PathNode next) {
		this.next = next;
	}
	
	public PathNode next() {
		return next;
	}
	
	@Override
	public String toString() {
		StringBuilder b = new StringBuilder();
		addCanonicalPath(b);
		return b.toString();
	}
	
	public abstract boolean isDefinite();
	
	public abstract void execute(JsonValues r, Object root, Object source, JsonPointer sourcePointer);
	
	public abstract void addCanonicalPath(StringBuilder b);
}
