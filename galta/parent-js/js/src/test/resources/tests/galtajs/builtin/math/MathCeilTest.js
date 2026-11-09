// BigInteger: identity
assertEquals(5n, Math.ceil(5n))
assertEquals(-5n, Math.ceil(-5n))

// Decimal: rounds toward +infinity, returns a Decimal
assertEquals(6m, Math.ceil(5.5m))
assertEquals(-5m, Math.ceil(-5.5m))
assertEquals(5m, Math.ceil(5m))
assertEquals("decimal", typeof Math.ceil(5.5m))
// A negative value rounding to zero is -0
assertTrue(Object.is(Math.ceil(-0.5m), -0))
