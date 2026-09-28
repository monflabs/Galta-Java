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
package tests.dependencies;

import static org.junit.Assert.assertThrows;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.monflabs.util.dependencies.DependencyEngine;
import org.monflabs.util.dependencies.DependencyEngine.DependencyCollector;
import org.monflabs.util.dependencies.DependencyEngine.DependencyFinder;
import org.monflabs.util.dependencies.DependencyException;

import tests.ProjectTestCase;

public class DependencyEngineTest extends ProjectTestCase {

	static class Requires extends DependencyFinder<String> {
		private final Map<String, List<String>> requires;
		private final Map<String, List<String>> requiredBy;
		Requires(Map<String, List<String>> requires) {
			this(requires, Map.of());
		}
		Requires(Map<String, List<String>> requires, Map<String, List<String>> requiredBy) {
			this.requires = requires;
			this.requiredBy = requiredBy;
		}
		@Override
		public void getDependencies(String item, DependencyCollector<String> collector) {
			for (String r : requires.getOrDefault(item, List.of())) {
				collector.addDependency(r);
			}
		}
		@Override
		public void getDependingItems(String item, DependencyCollector<String> collector) {
			for (String r : requiredBy.getOrDefault(item, List.of())) {
				collector.addDependency(r);
			}
		}
	}

	public void testFailureLeavesTheListUntouched() throws Exception {
		DependencyEngine<String> engine = new DependencyEngine<>(new Requires(Map.of(
				"a", List.of("b"),
				"b", List.of("a"))));
		List<String> list = new ArrayList<>(List.of("a", "b", "c"));
		assertThrows(DependencyException.class, () -> engine.sortList(list));
		// The list used to be cleared first, and left as [c]
		assertEquals(List.of("a", "b", "c"), list);
	}

	public void testMissingDependingItemLeavesTheListUntouched() throws Exception {
		DependencyEngine<String> engine = new DependencyEngine<>(new Requires(Map.of(), Map.of("a", List.of("zzz"))));
		List<String> list = new ArrayList<>(List.of("b", "a"));
		DependencyException e = assertThrows(DependencyException.class, () -> engine.sortList(list));
		assertTrue(e.getMessage(), e.getMessage().contains("zzz"));
		assertEquals(List.of("b", "a"), list);
	}

	public void testSortFixedSizeList() throws Exception {
		DependencyEngine<String> engine = new DependencyEngine<>(new Requires(Map.of("a", List.of("b"))));
		// Arrays.asList() can't be cleared: sortList() used to throw UnsupportedOperationException
		List<String> list = Arrays.asList("a", "b");
		engine.sortList(list);
		assertEquals(List.of("b", "a"), list);
	}

	public void testEqualItemsAreMerged() throws Exception {
		DependencyEngine<String> engine = new DependencyEngine<>(new Requires(Map.of("d", List.of("c"))));
		// Documented: items are looked up by equals(), equal items are the same item
		List<String> list = new ArrayList<>(List.of("d", "c", new String("c")));
		engine.sortList(list);
		assertEquals(List.of("c", "d"), list);
	}

	public void testEdgeCases() throws Exception {
		DependencyEngine<String> engine = new DependencyEngine<>(new Requires(Map.of(
				"self", List.of("self"),
				"x", List.of("outside"))));
		List<String> empty = new ArrayList<>();
		engine.sortList(empty);
		assertTrue(empty.isEmpty());
		assertTrue(engine.sortCollectionAsList(List.of()).isEmpty());
		// A dependency outside the collection is ignored
		assertEquals(List.of("x"), engine.sortCollectionAsList(List.of("x")));
		// A self dependency is a cycle
		DependencyException e = assertThrows(DependencyException.class, () -> engine.sortCollectionAsList(List.of("self")));
		assertTrue(e.getMessage(), e.getMessage().contains("self"));
	}

	public void testDependingItems() throws Exception {
		// "a" declares that "c" and "b" depend on it
		DependencyEngine<String> engine = new DependencyEngine<>(new Requires(Map.of("c", List.of("b")), Map.of("a", List.of("c", "b"))));
		assertEquals(List.of("a", "b", "c"), engine.sortCollectionAsList(List.of("c", "b", "a")));
	}
}
