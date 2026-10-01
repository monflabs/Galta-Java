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

import java.util.Arrays;

import org.monflabs.json.JsonException;

/**
 * Detects the cycles in an object graph being serialized, and limits the nesting depth.
 * <p>
 * JSON is a tree: an object that (directly or not) contains itself cannot be serialized.
 * The adapters of the containers (objects, collections, arrays) call {@link #enter(Object)}
 * before serializing the content of a value and {@link #exit(Object)} after, so a cycle is
 * reported as a {@link JsonException} instead of a <code>StackOverflowError</code>. A value
 * shared by several parents, without a cycle, is fine: it is serialized several times.
 * <p>
 * The containers being read call {@link #enterRead()}/{@link #exitRead()}. In both
 * directions, a graph nested deeper than the maximum depth (see {@link #maxDepth(int)},
 * {@link #DEFAULT_MAX_DEPTH} by default) throws a {@link JsonException} instead of
 * overflowing the stack.
 * <p>
 * The state is per thread. Only JDK types are stored in the thread local, so it does not pin
 * the class loader of this library, and the values are released as soon as they are exited.
 */
public final class CycleGuard {

	/**
	 * The default maximum nesting depth, the same as the JSON parser's.
	 */
	public static final int DEFAULT_MAX_DEPTH = 1000;

	// [0]: int[] {stack size, read depth, max depth}, then the stack of the values being serialized
	private static final ThreadLocal<Object[]> current = ThreadLocal.withInitial(() -> {
		Object[] s = new Object[17];
		s[0] = new int[] { 0, 0, DEFAULT_MAX_DEPTH };
		return s;
	});

	private CycleGuard() {
	}

	/**
	 * Set the maximum nesting depth for the current thread, and return the previous one.
	 */
	public static int maxDepth(int maxDepth) {
		int[] c = (int[])current.get()[0];
		int prev = c[2];
		c[2] = maxDepth>0 ? maxDepth : DEFAULT_MAX_DEPTH;
		return prev;
	}

	public static void enter(Object value) {
		Object[] s = current.get();
		int[] c = (int[])s[0];
		int size = c[0];
		// The stack holds the ancestors only: its size is bounded by the maximum depth
		for(int i=1; i<=size; i++) {
			if(s[i]==value) {
				throw new JsonException(null, "Cannot serialize a cyclic object graph: an instance of {0} contains itself", value.getClass().getName());
			}
		}
		if(size>=c[2]) {
			throw new JsonException(null, "Cannot serialize an object graph nested deeper than {0} levels", c[2]);
		}
		if(size+1>=s.length) {
			s = Arrays.copyOf(s, s.length*2);
			current.set(s);
		}
		s[++c[0]] = value;
	}

	public static void exit(Object value) {
		Object[] s = current.get();
		int[] c = (int[])s[0];
		int size = c[0];
		for(int i=size; i>=1; i--) {
			if(s[i]==value) {
				System.arraycopy(s, i+1, s, i, size-i);
				s[size] = null;
				c[0] = size-1;
				return;
			}
		}
	}

	/**
	 * Enter a JSON container being read.
	 */
	public static void enterRead() {
		int[] c = (int[])current.get()[0];
		if(c[1]>=c[2]) {
			throw new JsonException(null, "Cannot deserialize a JSON value nested deeper than {0} levels", c[2]);
		}
		c[1]++;
	}

	public static void exitRead() {
		int[] c = (int[])current.get()[0];
		if(c[1]>0) {
			c[1]--;
		}
	}
}
