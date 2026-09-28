assertEquals(0, Math.abs(0))
assertEquals(1, Math.abs(1))
assertEquals(1, Math.abs(-1))

assertEquals(0, Math.abs(0L))
assertEquals(1, Math.abs(1L))
assertEquals(1, Math.abs(-1L))

assertEquals(0.0, Math.abs(0.0))
assertEquals(1.1, Math.abs(1.1))
assertEquals(1.1, Math.abs(-1.1))

assertEquals(0.0, Math.abs(0.0))
assertEquals(1.1, Math.abs(1.1))
assertEquals(1.1, Math.abs(-1.1))

assertThrows(TypeError, () => Math.abs(0n))
assertThrows(TypeError, () => Math.abs(0m))
