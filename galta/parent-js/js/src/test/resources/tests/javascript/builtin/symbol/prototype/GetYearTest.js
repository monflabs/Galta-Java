const d = new Date(2020,3,13,8,4,20,681)
const d2 = new Date(1914,3,13,8,4,20,681)
const di = new Date("")

assertEquals( 120, d.getYear() )
assertEquals( 14, d2.getYear() )

// NaN is the date in invalid
assertEquals( NaN, di.getYear() )
