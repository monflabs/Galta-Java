function createTestIterator() {
    return ['a', 'b', 'c', 'd', 'e'].values();
}

// flatMap: Apply a function and flatten the result
let iter = createTestIterator().flatMap(x => [x, x.toUpperCase()]);
assertEquals(iter.toArray(), ['a', 'A', 'b', 'B', 'c', 'C', 'd', 'D', 'e', 'E']); // Duplicated in uppercase

iter = createTestIterator().flatMap(x => []);
assertEquals(iter.toArray(), []); // Empty transformation removes all

iter = createTestIterator().flatMap(x => [x]);
assertEquals(iter.toArray(), ['a', 'b', 'c', 'd', 'e']); // Identity function
