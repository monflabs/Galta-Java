const array = [2, 9, 9];
assertEquals( 0, array.indexOf(2) );
assertEquals( -1, array.indexOf(7) );
assertEquals( 2, array.indexOf(9, 2) );
assertEquals( -1, array.indexOf(2, -1) );
assertEquals( 0, array.indexOf(2, -3) );

// NaN is not found by indexOf (uses strict equality)
assertEquals( -1, [1, NaN, 3].indexOf(NaN) );

// Negative start clamped to 0
assertEquals( 0, [1,2,3].indexOf(1, -100) );

// Start beyond array length → -1
assertEquals( -1, [1,2,3].indexOf(1, 5) );

// String vs number
assertEquals( -1, [1, 2, 3].indexOf('1') );

// undefined
assertEquals( 1, [0, undefined, 2].indexOf(undefined) );

// null
assertEquals( 1, [0, null, 2].indexOf(null) );
