const a = [1,2,3,4,5]
const ita = a.values().drop(2)
assertEquals({value:3,done:false},ita.next())

const b = []
const itb = b.values().drop(2)
assertEquals({value:undefined,done:true},itb.next())


function createTestIterator() {
    return ['a', 'b', 'c', 'd', 'e'].values();
}

// drop: Skip the first N elements and return the remaining elements
let iter = createTestIterator().drop(0);
assertEquals(iter.toArray(), ['a', 'b', 'c', 'd', 'e']); // No drop

iter = createTestIterator().drop(2);
assertEquals(iter.toArray(), ['c', 'd', 'e']); // Drop first two

iter = createTestIterator().drop(5);
assertEquals(iter.toArray(), []); // Drop all elements

iter = createTestIterator().drop(10);
assertEquals(iter.toArray(), []); // Drop more than available