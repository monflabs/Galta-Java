const a = [ 1, 2, 3, 2, 3, 1, 2 ]

assertEquals([1,2,3],a.$distinct())

assertEquals(1,a.$getShort(0))
assertEquals(2,a.$getInt(1))
assertEquals(3,a.$getLong(2))

assertEquals(7,a.$size())
assertEquals(a.length,a.$size())

const a2 = a.$deepClone()
assertNotSame(a,a2)
assertEquals(a,a2)
