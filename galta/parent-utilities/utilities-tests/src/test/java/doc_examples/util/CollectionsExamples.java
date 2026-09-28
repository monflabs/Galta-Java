package doc_examples.util;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertThrows;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.monflabs.util.cache.CacheProvider;
import org.monflabs.util.cache.LRUCache;
import org.monflabs.util.dependencies.DependencyEngine;
import org.monflabs.util.dependencies.DependencyEngine.DependencyCollector;
import org.monflabs.util.dependencies.DependencyEngine.DependencyFinder;
import org.monflabs.util.dependencies.DependencyException;
import org.monflabs.util.iterators.Iterables;
import org.monflabs.util.iterators.Iterators;
import org.monflabs.util.sort.QuickSort;

import tests.ProjectTestCase;

/**
 * Samples for docs/Utilities/Collections.md
 */
public class CollectionsExamples extends ProjectTestCase {

	public void testIteratorPipeline() throws Exception {
		Iterator<Integer> numbers = Iterators.intSequence(0, 20).objectIterator();   // 0..19, end excluded
		Iterator<String> it = Iterators.map(
				Iterators.limit(Iterators.skip(Iterators.filter(numbers, n -> n % 2 == 0), 1), 3),
				n -> "#" + n);
		assertEquals(List.of("#2", "#4", "#6"), Iterators.collect(it));
	}

	public void testIndexedCallbacks() throws Exception {
		List<String> names = List.of("ann", "bob", "cid", "dan");
		// The IteratorPredicate/IteratorMap variants also receive the index
		Iterator<String> odd = Iterators.filter(names.iterator(), (s, index) -> index % 2 == 1);
		assertEquals(List.of("bob", "dan"), Iterators.collect(odd));

		assertEquals("cid", Iterators.find(names.iterator(), (s, i) -> s.startsWith("c"), null));
		assertTrue(Iterators.some(names.iterator(), (s, i) -> s.equals("bob")));
		assertEquals(Integer.valueOf(12), Iterators.reduce(names.iterator(), (acc, s, i) -> acc + s.length(), 0));
		assertEquals("dan", Iterators.last(names.iterator()));
		assertNull(Iterators.get(names.iterator(), 10));   // out of range: null
	}

	public void testConcat() throws Exception {
		Iterator<String> it = Iterators.concat(List.of("a").iterator(), null, List.of("b", "c").iterator());
		assertEquals(List.of("a", "b", "c"), Iterators.collect(it));   // null iterators are skipped

		Iterable<Integer> both = Iterables.concat(List.of(1, 2), List.of(3));
		assertEquals(List.of(1, 2, 3), Iterators.collect(both.iterator()));
		assertEquals(List.of(1, 2, 3), Iterators.collect(both.iterator()));   // an Iterable can be iterated again
	}

	public void testRemoveThroughFilter() throws Exception {
		List<Integer> list = new ArrayList<>(List.of(1, 2, 3, 4));
		Iterator<Integer> even = Iterators.filter(list.iterator(), n -> n % 2 == 0);
		even.next();       // 2
		even.remove();     // removes 2 from the list
		assertEquals(List.of(1, 3, 4), list);

		even.hasNext();    // looks ahead: the source is now past 4
		assertThrows(IllegalStateException.class, even::remove);

		assertThrows(UnsupportedOperationException.class, () -> {
			Iterator<Integer> ro = Iterators.readOnly(list.iterator());
			ro.next();
			ro.remove();
		});
	}

	public void testNestedAndFlatten() throws Exception {
		Map<String, List<String>> teams = Map.of("red", List.of("ann", "bob"));
		Iterator<String> members = Iterators.nested(List.of("red").iterator(), team -> teams.get(team).iterator());
		assertEquals(List.of("ann", "bob"), Iterators.collect(members));

		// flatten() descends into any value the factory turns into an Iterator
		List<Object> tree = List.of(1, List.of(2, List.of(3)), 4);
		Iterator<Object> flat = Iterators.flatten(tree.iterator(), v -> v instanceof List<?> l ? l.iterator() : v);
		assertEquals(List.of(1, 2, 3, 4), Iterators.collect(flat));
	}

	public void testLRUCache() throws Exception {
		LRUCache<String, Integer> cache = new LRUCache<>(2);
		cache.put("a", 1);
		cache.put("b", 2);
		cache.get("a");          // "a" is now the most recently used
		cache.put("c", 3);       // evicts "b"
		assertTrue(cache.contains("a"));
		assertFalse(cache.contains("b"));
		assertEquals(List.of("a", "c"), new ArrayList<>(cache.getMap().keySet()));
	}

	public void testCacheFactory() throws Exception {
		CacheProvider<String, Integer> cache = new LRUCache<>(100);
		int[] calls = {0};
		assertEquals(Integer.valueOf(5), cache.get("hello", k -> { calls[0]++; return k.length(); }));
		assertEquals(Integer.valueOf(5), cache.get("hello", k -> { calls[0]++; return k.length(); }));
		assertEquals(1, calls[0]);   // computed once, then cached

		cache.get("missing", k -> null);
		assertTrue(cache.contains("missing"));   // a null result is cached too
	}

	public void testQuickSort() throws Exception {
		String[] names = {"dan", "ann", "cid", "bob"};
		int[] ages = {40, 25, 31, 25};
		// Sort two parallel arrays by age, then name: QuickSort only sees indexes
		QuickSort.sort(new QuickSort.Accessor() {
			@Override
			public int size() {
				return ages.length;
			}
			@Override
			public int compare(int i, int j) {
				int c = Integer.compare(ages[i], ages[j]);
				return c != 0 ? c : names[i].compareTo(names[j]);
			}
			@Override
			public void exchange(int i, int j) {
				String n = names[i]; names[i] = names[j]; names[j] = n;
				int a = ages[i]; ages[i] = ages[j]; ages[j] = a;
			}
		});
		assertArrayEquals(new String[] {"ann", "bob", "cid", "dan"}, names);
		assertArrayEquals(new int[] {25, 25, 31, 40}, ages);
	}

	/** Items are module names; the finder reports what each one requires. */
	static class Requires extends DependencyFinder<String> {
		private final Map<String, List<String>> requires;
		Requires(Map<String, List<String>> requires) {
			this.requires = requires;
		}
		@Override
		public void getDependencies(String module, DependencyCollector<String> collector) {
			for (String r : requires.getOrDefault(module, List.of())) {
				collector.addDependency(r);
			}
		}
	}

	public void testDependencyEngine() throws Exception {
		DependencyEngine<String> engine = new DependencyEngine<>(new Requires(Map.of(
				"app", List.of("ui", "json"),
				"ui", List.of("json", "utilities"),
				"json", List.of("utilities"))));
		List<String> sorted = engine.sortCollectionAsList(List.of("app", "ui", "json", "utilities"));
		assertEquals(List.of("utilities", "json", "ui", "app"), sorted);   // dependencies first

		List<String> inPlace = new ArrayList<>(List.of("json", "utilities"));
		engine.sortList(inPlace);
		assertEquals(List.of("utilities", "json"), inPlace);

		// A dependency outside the collection is ignored
		assertEquals(List.of("json"), engine.sortCollectionAsList(List.of("json")));
	}

	public void testCircularDependencies() throws Exception {
		DependencyEngine<String> engine = new DependencyEngine<>(new Requires(Map.of(
				"a", List.of("b"),
				"b", List.of("c"),
				"c", List.of("a"))));
		DependencyException e = assertThrows(DependencyException.class,
				() -> engine.sortCollectionAsList(Arrays.asList("start", "a", "b", "c")));
		assertEquals("Circular references detected between the following items: a, b, c", e.getMessage());
	}
}
