function createTestIterator() {
    return ['a', 'b', 'c', 'd', 'e'].values();
}

// find: Return the first element that satisfies a condition
let iter = createTestIterator().find(x => x === 'c');
assertEquals(iter, 'c'); // Found element

iter = createTestIterator().find(x => x === 'z');
assertEquals(iter, undefined); // Not found

iter = createTestIterator().find(x => x < 'd');
assertEquals(iter, 'a'); // First matching element

iter = [].values().find(x => x > 'a');
assertEquals(iter, undefined); // Empty input returns undefined