const array = [2, 5, 9, 2];

assertEquals( 3, array.lastIndexOf(2) );
assertEquals( -1, array.lastIndexOf(7) );
assertEquals( 3, array.lastIndexOf(2, 3) );
assertEquals( 0, array.lastIndexOf(2, 2) );
assertEquals( 0, array.lastIndexOf(2, -2) );
assertEquals( 3, array.lastIndexOf(2, -1) );

// NaN is not found by lastIndexOf (uses strict equality)
assertEquals( -1, [1, NaN, NaN].lastIndexOf(NaN) );

// fromIndex beyond array length → starts from end
assertEquals( 3, array.lastIndexOf(2, 100) );

// Negative fromIndex beyond array → returns -1
assertEquals( -1, [1,2,3].lastIndexOf(1, -10) );

// String vs number
assertEquals( -1, [1, 2, 3].lastIndexOf('2') );
