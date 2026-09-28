const d = new Date(2020,3,13,8,4,20,681)

assertEquals( 2020, d.getFullYear() )
assertEquals( 3,    d.getMonth() )
assertEquals( 13,   d.getDate() )
assertEquals( 8,    d.getHours() )
assertEquals( 4,    d.getMinutes() )
assertEquals( 20,   d.getSeconds() )
assertEquals( 681,  d.getMilliseconds() )

d.setMinutes(44)

assertEquals( 2020, d.getFullYear() )
assertEquals( 3,    d.getMonth() )
assertEquals( 13,   d.getDate() )
assertEquals( 8,    d.getHours() )
assertEquals( 44,   d.getMinutes() )
assertEquals( 20,   d.getSeconds() )
assertEquals( 681,  d.getMilliseconds() )
