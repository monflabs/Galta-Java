function createTestIterator() {
    return ['a', 'b', 'c', 'd', 'e'].values();
}

// take: Take the first N elements
let iter = createTestIterator().take(3);
assertEquals(iter.toArray(), ['a', 'b', 'c']); // Taking three elements

iter = createTestIterator().take(5);
assertEquals(iter.toArray(), ['a', 'b', 'c', 'd', 'e']); // Taking all

iter = createTestIterator().take(0);
assertEquals(iter.toArray(), []); // Taking zero elements

iter = createTestIterator().take(10);
assertEquals(iter.toArray(), ['a', 'b', 'c', 'd', 'e']); // Taking more than available
