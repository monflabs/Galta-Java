const b = "abcd"

assertEquals("a",b.at())
assertEquals("a",b.at(0))
assertEquals("b",b.at(1))
assertEquals("d",b.at(-1))
assertEquals("b",b.at(-3))
assertEquals(undefined,b.at(-5))
assertEquals(undefined,b.at(4))