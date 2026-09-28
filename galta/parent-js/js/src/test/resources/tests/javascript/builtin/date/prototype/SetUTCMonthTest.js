const d = new Date("2020 Apr 13 08:04:20.681 GMT")

assertEquals( 2020, d.getUTCFullYear() )
assertEquals( 3,    d.getUTCMonth() )
assertEquals( 13,   d.getUTCDate() )
assertEquals( 8,    d.getUTCHours() )
assertEquals( 4,    d.getUTCMinutes() )
assertEquals( 20,   d.getUTCSeconds() )
assertEquals( 681,  d.getUTCMilliseconds() )

d.setUTCMonth(11)

assertEquals( 2020, d.getUTCFullYear() )
assertEquals( 11,   d.getUTCMonth() )
assertEquals( 13,   d.getUTCDate() )
assertEquals( 8,    d.getUTCHours() )
assertEquals( 4,    d.getUTCMinutes() )
assertEquals( 20,   d.getUTCSeconds() )
assertEquals( 681,  d.getUTCMilliseconds() )
