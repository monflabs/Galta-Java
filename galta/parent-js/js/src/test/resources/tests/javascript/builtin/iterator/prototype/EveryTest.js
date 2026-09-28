const a = [1,2,3,4,5]
const r = []
a.values().every( (v) => { r.push(v); return v<=2; } );
assertEquals( [1,2,3], r)


function createTestIterator() {
    return ['a', 'b', 'c', 'd', 'e'].values();
}

// every: Check if all elements satisfy a condition
let iter = createTestIterator().every(x => typeof x === 'string');
assertTrue(iter); // All are strings

iter = createTestIterator().every(x => x >= 'a');
assertTrue(iter); // All are at least 'a'

iter = createTestIterator().every(x => x === 'a');
assertFalse(iter); // Not all are 'a'

iter = [].values().every(x => x !== 'z');
assertTrue(iter); // Empty iterator always returns true