function createTestIterator() {
    return ['a', 'b', 'c', 'd', 'e'].values();
}

// toArray: Convert iterator to array
let iter = createTestIterator().toArray();
assertEquals(iter, ['a', 'b', 'c', 'd', 'e']); // Conversion check

iter = [].values().toArray();
assertEquals(iter, []); // Empty input remains empty
