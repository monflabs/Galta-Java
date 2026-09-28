let a = [1,2,3]

assertEquals(1,a.at(0))
assertEquals(2,a.at(1))
assertEquals(3,a.at(2))
assertEquals(undefined,a.at(3))
assertEquals(3,a.at(-1))
assertEquals(2,a.at(-2))
assertEquals(1,a.at(-3))
assertEquals(undefined,a.at(-4))
