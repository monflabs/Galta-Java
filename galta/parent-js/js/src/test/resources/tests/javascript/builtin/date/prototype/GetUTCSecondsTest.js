const d = new Date("2020 Apr 13 08:04:20.681 GMT")
const di = new Date("")

assertEquals( 20, d.getUTCSeconds() )

// NaN is the date in invalid
assertEquals( NaN, di.getUTCSeconds() )
