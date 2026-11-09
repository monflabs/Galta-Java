// BigInteger: identity
assertEquals(5n, Math.floor(5n))
assertEquals(-5n, Math.floor(-5n))

// Decimal: rounds toward -infinity, returns a Decimal
assertEquals(5m, Math.floor(5.5m))
assertEquals(-6m, Math.floor(-5.5m))
assertEquals(5m, Math.floor(5m))
assertEquals("decimal", typeof Math.floor(5.5m))
assertTrue(Object.is(Math.floor(-0m), -0))
