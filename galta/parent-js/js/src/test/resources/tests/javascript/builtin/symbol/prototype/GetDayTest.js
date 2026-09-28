const d = new Date(2020,3,13,8,4,20,681)
const di = new Date("")

// 0 for Sunday, 1 for Monday, 2 for Tuesday, and so on
assertEquals( 1, d.getDay() ) // 2020-04-13 is a Monday

// NaN is the date in invalid
assertEquals( NaN, di.getDay() )
