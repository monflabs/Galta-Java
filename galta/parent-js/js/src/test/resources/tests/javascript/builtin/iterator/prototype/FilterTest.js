function createTestIterator() {
    return ['a', 'b', 'c', 'd', 'e'].values();
}

// filter: Keep only elements that satisfy a condition
let iter = createTestIterator().filter(x => x > 'b');
assertEquals(iter.toArray(), ['c', 'd', 'e']); // Filtered output

iter = createTestIterator().filter(x => x < 'z');
assertEquals(iter.toArray(), ['a', 'b', 'c', 'd', 'e']); // No filtering

iter = createTestIterator().filter(x => x === 'z');
assertEquals(iter.toArray(), []); // No elements match

iter = [].values().filter(x => x > 'a');
assertEquals(iter.toArray(), []); // Empty input remains empty