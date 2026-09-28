const evens = new Set([2, 4, 6, 8]);
const squares = new Set([1, 4, 9]);
const diff = evens.symmetricDifference(squares);
assertEquals( 5, diff.size );
assertTrue( diff.has(2));
assertTrue( diff.has(6));
assertTrue( diff.has(8));
assertTrue( diff.has(1));
assertTrue( diff.has(9));
