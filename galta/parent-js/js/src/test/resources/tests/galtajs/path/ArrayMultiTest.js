//
// Multi member access
//

// Array
const a1$ = [1,2,3,4,5]
assertEquals( 2, a1$[1] )
assertEquals( [2,4], a1$[1,3] )
assertUndefined( a1$[8,9] )

assertEquals( [2,4,5], a1$[1,3,4] )
assertEquals( 2, a1$[1,3,4][][0] )

assertEquals( 3, a1$[2,'a'] )

assertEquals( 4, a1$['a',3] )

assertEquals( null, a1$[true,false] )


// Array of objects
const a2$ = [{a:1, b:11}, {a:2, b:22, c:222}, {a:3, b:33} ]
assertEquals( [1,2,3], a2$[*]['a'] )
assertEquals( [{a:1, b:11}, {a:3, b:33} ], a2$[0,2] )
assertEquals( [ 1, 11, 3, 33 ], a2$[0,2]['a','b'] )

// The spec is not clear for the below - we do what work better!
assertEquals( [ 1, 11 , 2, 22, 3, 33 ], a2$.*['a','b'] )
assertEquals( [ 1, 11 , 2, 22, 222, 3, 33 ], a2$.*['a','b','c'] )


// Object
const o$ = {a:1, b:2, c: 3}
assertEquals( [1,3], o$['a','c'] )


//
// Assign
const as0$ = [1,2,3,4,5,6];
as0$[2,3,4] = 8
assertEquals( [1,2,8,8,8,6], as0$ )

const as1$ = [1,2];
as1$[2,3,4] = 8
assertEquals( [1,2,8,8,8], as1$ )

const as2$ = [];
as2$[2,3,4] = 8
assertEquals( SPARSE(EMPTY(2),8,8,8), as2$ )
