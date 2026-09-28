// BigInteger: identity
assertEquals(5n, Math.ceil(5n))
assertEquals(-5n, Math.ceil(-5n))

// Decimal: rounds toward +infinity, returns BigInteger
assertEquals(6n, Math.ceil(5.5m))
assertEquals(-5n, Math.ceil(-5.5m))
assertEquals(5n, Math.ceil(5m))
