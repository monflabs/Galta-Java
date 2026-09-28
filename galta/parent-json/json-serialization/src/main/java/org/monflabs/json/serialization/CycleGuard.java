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
package org.monflabs.json.serialization;

import java.util.IdentityHashMap;
import java.util.Map;

import org.monflabs.json.JsonException;

/**
 * Detects the cycles in an object graph being serialized.
 * <p>
 * JSON is a tree: an object that (directly or not) contains itself cannot be serialized.
 * The adapters of the containers (objects, collections, arrays) call {@link #enter(Object)}
 * before serializing the content of a value and {@link #exit(Object)} after, so a cycle is
 * reported as a {@link JsonException} instead of a <code>StackOverflowError</code>. A value
 * shared by several parents, without a cycle, is fine: it is serialized several times.
 */
public final class CycleGuard {
	
	private static final ThreadLocal<Map<Object,Boolean>> current = new ThreadLocal<>();
	
	private CycleGuard() {
	}

	public static void enter(Object value) {
		Map<Object,Boolean> m = current.get();
		if(m==null) {
			m = new IdentityHashMap<>();
			current.set(m);
		}
		if(m.put(value, Boolean.TRUE)!=null) {
			throw new JsonException(null, "Cannot serialize a cyclic object graph: an instance of {0} contains itself", value.getClass().getName());
		}
	}
	
	public static void exit(Object value) {
		Map<Object,Boolean> m = current.get();
		if(m!=null) {
			m.remove(value);
			if(m.isEmpty()) {
				current.remove();
			}
		}
	}
}
