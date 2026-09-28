# Iterators, Caches & Sorting

This page covers the collection helpers of `org.monflabs.util`: lazy iterator pipelines (`iterators`), two caches (`cache`), an index-based quicksort (`sort`) and a topological sorter for items with dependencies (`dependencies`).

## Iterators and Iterables

`Iterators` builds lazy `java.util.Iterator` pipelines; `Iterables` offers the same operations on `Iterable`, returning an `Iterable` that creates a fresh pipeline on each `iterator()` call. Nothing is evaluated before `hasNext()`/`next()` is called.

| Group | `Iterators` | `Iterables` |
|---|---|---|
| Sources | `empty()`, `single(v)`, `staticValues(v...)`, `array(v...)`, `arrayReflection(array)`, `enumeration(e)`, `readOnlyList(list)`, `intSequence(start, end)`, `longSequence(start, end)`, `charSequence(cs[, start, end])` | `empty()`, `single(v)`, `staticValues(v...)`, `intSequence`, `longSequence` |
| Transform | `filter(it, predicate)`, `map(it, function)`, `skip(it, n)`, `limit(it, n)`, `concat(...)`, `nested(it, f1[, f2])`, `flatten(it, factory)`, `readOnly(it)` | `filter`, `map`, `concat` |
| Consume | `size`, `first`, `last`, `get(it, index)`, `collect(it[, list])`, `collectArray(it[, class])`, `forEach`, `find`, `every`, `some`, `reduce` | |

`intSequence(start, end)` and `longSequence` exclude `end` and return primitive iterators (`IntIterator`, `LongIterator`); `objectIterator()` boxes them, and `map(IntIterator, IntFunction)` maps them.

Sample: `doc_examples/util/CollectionsExamples.java` (`testIteratorPipeline`)

```java
Iterator<Integer> numbers = Iterators.intSequence(0, 20).objectIterator();   // 0..19, end excluded
Iterator<String> it = Iterators.map(
        Iterators.limit(Iterators.skip(Iterators.filter(numbers, n -> n % 2 == 0), 1), 3),
        n -> "#" + n);
assertEquals(List.of("#2", "#4", "#6"), Iterators.collect(it));
```

`filter` and `map` come in two flavours: with a `java.util.function` interface, or with `IteratorPredicate`/`IteratorMap`, which also receive the zero-based index of the element in the source. `forEach`, `find`, `every`, `some` and `reduce` pass the index too. `first`, `get` and `last` return `null` when there is no such element, and `find` returns the default you give it.

Sample: `doc_examples/util/CollectionsExamples.java` (`testIndexedCallbacks`)

```java
List<String> names = List.of("ann", "bob", "cid", "dan");
// The IteratorPredicate/IteratorMap variants also receive the index
Iterator<String> odd = Iterators.filter(names.iterator(), (s, index) -> index % 2 == 1);
assertEquals(List.of("bob", "dan"), Iterators.collect(odd));

assertEquals("cid", Iterators.find(names.iterator(), (s, i) -> s.startsWith("c"), null));
assertTrue(Iterators.some(names.iterator(), (s, i) -> s.equals("bob")));
assertEquals(Integer.valueOf(12), Iterators.reduce(names.iterator(), (acc, s, i) -> acc + s.length(), 0));
assertEquals("dan", Iterators.last(names.iterator()));
assertNull(Iterators.get(names.iterator(), 10));   // out of range: null
```

`concat` skips `null` iterators. The `Iterables.concat` variant builds its chain per `iterator()` call, so it can be traversed any number of times:

Sample: `doc_examples/util/CollectionsExamples.java` (`testConcat`)

```java
Iterator<String> it = Iterators.concat(List.of("a").iterator(), null, List.of("b", "c").iterator());
assertEquals(List.of("a", "b", "c"), Iterators.collect(it));   // null iterators are skipped

Iterable<Integer> both = Iterables.concat(List.of(1, 2), List.of(3));
assertEquals(List.of(1, 2, 3), Iterators.collect(both.iterator()));
assertEquals(List.of(1, 2, 3), Iterators.collect(both.iterator()));   // an Iterable can be iterated again
```

### remove()

`map`, `skip`, `limit` and `concat` forward `remove()` to the iterator that produced the last element. `filter` does too, with one restriction: `hasNext()` has to read ahead in the source to find the next match, and after that the source is no longer positioned on the element `next()` returned. `remove()` then throws `IllegalStateException` rather than remove the wrong element. The sources (`single`, `staticValues`, `readOnlyList`, sequences...) and `readOnly(it)` throw `UnsupportedOperationException`.

Sample: `doc_examples/util/CollectionsExamples.java` (`testRemoveThroughFilter`)

```java
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
```

### Nested and flattened iteration

`nested(it, f1[, f2])` iterates the source and, for every element, the iterator the function returns for it; the result yields the elements of the innermost level. `flatten(it, factory)` is the recursive form: each element goes through the factory, and whenever the factory returns an `Iterator` (or the element already is one) its elements are flattened in turn. A factory that returns anything other than an iterator yields that value.

Sample: `doc_examples/util/CollectionsExamples.java` (`testNestedAndFlatten`)

```java
Map<String, List<String>> teams = Map.of("red", List.of("ann", "bob"));
Iterator<String> members = Iterators.nested(List.of("red").iterator(), team -> teams.get(team).iterator());
assertEquals(List.of("ann", "bob"), Iterators.collect(members));

// flatten() descends into any value the factory turns into an Iterator
List<Object> tree = List.of(1, List.of(2, List.of(3)), 4);
Iterator<Object> flat = Iterators.flatten(tree.iterator(), v -> v instanceof List<?> l ? l.iterator() : v);
assertEquals(List.of(1, 2, 3, 4), Iterators.collect(flat));
```

## Caches

`CacheProvider<K,V>` is a minimal cache interface: `contains`, `get`, `put`, `remove`, `clear`, and a default `get(key, factory)` that computes and stores a missing value. `MapCacheProvider` implements it over any `Map`, with every method `synchronized`. `LRUCache(capacity)` is a `MapCacheProvider` over an access-ordered `LinkedHashMap` that drops the least recently used entry once `capacity` is exceeded.

Sample: `doc_examples/util/CollectionsExamples.java` (`testLRUCache`)

```java
LRUCache<String, Integer> cache = new LRUCache<>(2);
cache.put("a", 1);
cache.put("b", 2);
cache.get("a");          // "a" is now the most recently used
cache.put("c", 3);       // evicts "b"
assertTrue(cache.contains("a"));
assertFalse(cache.contains("b"));
assertEquals(List.of("a", "c"), new ArrayList<>(cache.getMap().keySet()));
```

A `get` counts as a use; `contains` does not. `getMap()` exposes the underlying map, in least-recently-used-first order for an `LRUCache`; it is not synchronized.

`get(key, factory)` runs the factory outside the cache's lock, so a slow factory doesn't block the other accesses. When two threads compute the same key at the same time, the first value stored wins and both get it. The result is stored even when it is `null`, and a factory asking for the key it is computing gets an `IllegalStateException`:

Sample: `doc_examples/util/CollectionsExamples.java` (`testCacheFactory`)

```java
CacheProvider<String, Integer> cache = new LRUCache<>(100);
int[] calls = {0};
assertEquals(Integer.valueOf(5), cache.get("hello", k -> { calls[0]++; return k.length(); }));
assertEquals(Integer.valueOf(5), cache.get("hello", k -> { calls[0]++; return k.length(); }));
assertEquals(1, calls[0]);   // computed once, then cached

cache.get("missing", k -> null);
assertTrue(cache.contains("missing"));   // a null result is cached too
```

## QuickSort

`QuickSort` sorts anything that can compare and swap by index, which lets it sort parallel arrays or a custom structure in place without building a list of objects. You supply a `QuickSort.Accessor` (`size()`, `compare(i, j)`, `exchange(i, j)`) and call `QuickSort.sort(accessor)`, or `QuickSort.sort(accessor, offset, length)` for a range. The algorithm is a median-of-three quicksort with a simple exchange sort for ranges of up to 7 elements. It is not stable.

Sample: `doc_examples/util/CollectionsExamples.java` (`testQuickSort`)

```java
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
```

## DependencyEngine

`DependencyEngine<T>` orders items so that every item comes after the items it depends on. The dependencies are reported by a `DependencyFinder<T>` subclass:

| Method | Reports |
|---|---|
| `getDependencies(item, collector)` | the items `item` depends on (`collector.addDependency(d)`) |
| `getDependingItems(item, collector)` | the reverse: the items that depend on `item` |
| `objectString(item)` | the text used for an item in error messages (default `toString()`) |

Both relations can be used at the same time. `sortCollectionAsList(collection)` returns a new sorted list; `sortList(list)` sorts a mutable list in place.

Sample: `doc_examples/util/CollectionsExamples.java` (`Requires`, `testDependencyEngine`)

```java
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
```

The sort is deterministic: it makes passes over the remaining items in their original order and emits every item whose dependencies are already out, so independent items keep their relative order. Two details of the lookup:

- A dependency reported by `getDependencies` that is not part of the collection is ignored.
- An item reported by `getDependingItems` that is not in the collection is an error: `DependencyException` "Dependency X not found for item Y".

Items are looked up by `equals()`/`hashCode()`; a pass in which no item can be emitted means a cycle, reported as a `DependencyException` listing every item left, not only the cycle itself:

Sample: `doc_examples/util/CollectionsExamples.java` (`testCircularDependencies`)

```java
DependencyEngine<String> engine = new DependencyEngine<>(new Requires(Map.of(
        "a", List.of("b"),
        "b", List.of("c"),
        "c", List.of("a"))));
DependencyException e = assertThrows(DependencyException.class,
        () -> engine.sortCollectionAsList(Arrays.asList("start", "a", "b", "c")));
assertEquals("Circular references detected between the following items: a, b, c", e.getMessage());
```
