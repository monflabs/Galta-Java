// BigInteger: identity
assertEquals(5n, Math.floor(5n))
assertEquals(-5n, Math.floor(-5n))

// Decimal: rounds toward -infinity, returns BigInteger
assertEquals(5n, Math.floor(5.5m))
assertEquals(-6n, Math.floor(-5.5m))
assertEquals(5n, Math.floor(5m))
