function createTestIterator() {
    return ['a', 'b', 'c', 'd', 'e'].values();
}

// some: Check if at least one element satisfies a condition
let iter = createTestIterator().some(x => x === 'c');
assertTrue(iter); // Found an element

iter = createTestIterator().some(x => x === 'z');
assertFalse(iter); // No match

iter = createTestIterator().some(x => x > 'a');
assertTrue(iter); // Some elements are greater than 'a'

iter = [].values().some(x => x !== 'z');
assertFalse(iter); // Empty input returns false
