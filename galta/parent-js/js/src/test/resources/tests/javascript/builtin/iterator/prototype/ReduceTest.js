function createTestIterator() {
    return ['a', 'b', 'c', 'd', 'e'].values();
}

// reduce: Accumulate values using a function
let iter = createTestIterator().reduce((acc, x) => acc + x, '');
assertEquals(iter, 'abcde'); // Concatenation

iter = createTestIterator().reduce((acc, x) => acc + x.charCodeAt(0), 0);
assertEquals(iter, 'a'.charCodeAt(0) + 'b'.charCodeAt(0) + 'c'.charCodeAt(0) + 'd'.charCodeAt(0) + 'e'.charCodeAt(0)); // ASCII sum

iter = createTestIterator().reduce((acc, x) => acc, 'initial');
assertEquals(iter, 'initial'); // Identity function

iter = [].values().reduce((acc, x) => acc + x, 'empty');
assertEquals(iter, 'empty'); // Empty input keeps initial value
