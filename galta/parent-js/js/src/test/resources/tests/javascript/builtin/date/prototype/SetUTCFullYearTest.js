const d = new Date("2020 Apr 13 08:04:20.681 GMT")

assertEquals( 2020, d.getUTCFullYear() )
assertEquals( 3,    d.getUTCMonth() )
assertEquals( 13,   d.getUTCDate() )
assertEquals( 8,    d.getUTCHours() )
assertEquals( 4,    d.getUTCMinutes() )
assertEquals( 20,   d.getUTCSeconds() )
assertEquals( 681,  d.getUTCMilliseconds() )

d.setUTCFullYear(2019)

assertEquals( 2019, d.getUTCFullYear() )
assertEquals( 3,    d.getUTCMonth() )
assertEquals( 13,   d.getUTCDate() )
assertEquals( 8,    d.getUTCHours() )
assertEquals( 4,    d.getUTCMinutes() )
assertEquals( 20,   d.getUTCSeconds() )
assertEquals( 681,  d.getUTCMilliseconds() )
