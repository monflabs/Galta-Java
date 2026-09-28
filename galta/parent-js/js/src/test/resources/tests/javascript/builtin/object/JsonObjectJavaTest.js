const o = {a:1, b:2, c:3, d:4, e: 5}

assertEquals(1,o.$getShort("a"))
assertEquals(2,o.$getInt("b"))
assertEquals(3,o.$getLong("c"))

assertEquals(5,o.$size())

const o2 = o.$deepClone()
assertNotSame(o,o2)
assertEquals(o,o2)
