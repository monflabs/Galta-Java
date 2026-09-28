// BigInteger: identity
assertEquals(5n, Math.trunc(5n))
assertEquals(-5n, Math.trunc(-5n))

// Decimal: rounds toward zero, returns BigInteger
assertEquals(5n, Math.trunc(5.9m))
assertEquals(-5n, Math.trunc(-5.9m))
assertEquals(5n, Math.trunc(5m))
