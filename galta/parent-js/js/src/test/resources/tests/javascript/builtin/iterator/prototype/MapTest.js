function createTestIterator() {
    return ['a', 'b', 'c', 'd', 'e'].values();
}

// map: Transform elements using a function
let iter = createTestIterator().map(x => x.toUpperCase());
assertEquals(iter.toArray(), ['A', 'B', 'C', 'D', 'E']); // Uppercase transformation

iter = createTestIterator().map(x => x + x);
assertEquals(iter.toArray(), ['aa', 'bb', 'cc', 'dd', 'ee']); // Duplicating elements

iter = createTestIterator().map(() => 'x');
assertEquals(iter.toArray(), ['x', 'x', 'x', 'x', 'x']); // Constant replacement

iter = [].values().map(x => x.toUpperCase());
assertEquals(iter.toArray(), []); // Empty input remains empty
