const d = new Date(2020,3,13,8,4,20,681)
const di = new Date("")

assertEquals( 3, d.getMonth() )

// NaN is the date in invalid
assertEquals( NaN, di.getMonth() )
