const b = "AA"

assertEquals("AA",b.concat())
assertEquals("AAbb",b.concat("bb"))
assertEquals("AAbbcC",b.concat("bb","cC"))
assertEquals("AA12.3",b.concat(12.3))
assertEquals("AAtrue",b.concat(true))
