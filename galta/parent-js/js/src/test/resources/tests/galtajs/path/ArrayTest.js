// Operator []
// Test the conversion of sequences to array

const a = [1,2,3]

// Reg access
assertEquals( 1, a[0] )
assertUndefined( a[-1] )

// Seq access
const aa = [a]
assertEquals( 1, aa.*[0] )
assertEquals( 3, aa.*[-1] )
assertEquals( 2, aa.*[-2] )
assertUndefined( aa.*[-4] )

const v = [ [1,2,3,4,5] ]
assertEquals( 1, aa.*[0] )

assertEquals( 1,v.*[0] );
assertEquals( 5,v.*[-1] );
assertEquals( [2,4],v.*[1,3] );
assertEquals( [5,3],v.*[-1,2] );
assertEquals( [2,3],v.*[1:3] );
assertEquals( [1,3,5],v.*[0:6:2] );

assertEquals( [], [].*[] )
assertEquals( [1], [1].*[] )
assertEquals( [1,2], [1,2].*[] )

assertEquals( [null], null[] )
assertEquals( [1], 1[] )
assertEquals( ["xyz"], "xyz"[] )
