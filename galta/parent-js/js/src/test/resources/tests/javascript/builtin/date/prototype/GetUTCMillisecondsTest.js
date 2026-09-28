const d = new Date("2020 Apr 13 08:04:20.681 GMT")
const di = new Date("")

assertEquals( 681, d.getUTCMilliseconds() )

// NaN is the date in invalid
assertEquals( NaN, di.getUTCMilliseconds() )
