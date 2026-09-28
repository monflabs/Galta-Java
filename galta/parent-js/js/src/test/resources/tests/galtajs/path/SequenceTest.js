const a1$ = [ 1, 2, 3 ]
assertEquals( 1, a1$[0] )

const a2$ = [ {a:1} ]
assertEquals( 1, a2$.*.a )

const a3$ = [ {a:1}, {a:2} ]
assertEquals( [1,2], a3$.*.a )

const a4$ = [ {a:{b:1}}, {a:2}, {a:{b:3}} ]
assertEquals( [{b:1},2,{b:3}], a4$.*.a )
assertEquals( [1,3], a4$.*.a.b )
assertEquals( 1, a4$.*.a.b[][0] )
assertEquals( 3, a4$.*.a.b[][1] )


// Make sure that a value is an array
assertEquals( [1], 1[])
assertEquals( 1, 1[][0])
