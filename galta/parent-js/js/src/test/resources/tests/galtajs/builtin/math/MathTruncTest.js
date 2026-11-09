// BigInteger: identity
assertEquals(5n, Math.trunc(5n))
assertEquals(-5n, Math.trunc(-5n))

// Decimal: rounds toward zero, returns a Decimal
assertEquals(5m, Math.trunc(5.9m))
assertEquals(-5m, Math.trunc(-5.9m))
assertEquals(5m, Math.trunc(5m))
assertEquals("decimal", typeof Math.trunc(5.9m))
assertTrue(Object.is(Math.trunc(-0.3m), -0))
assertEquals("decimal", typeof Math.trunc(0.3m))
