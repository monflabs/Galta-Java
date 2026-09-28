// BigInteger: result type is BigInteger
assertEquals(3n, Math.max(1n, 3n, 2n))
assertEquals(-1n, Math.max(-1n, -3n, -2n))

// Decimal: result type is Decimal
assertEquals(3m, Math.max(1m, 3m, 2m))

// Mixed: result type matches the winning value
assertEquals(3n, Math.max(1n, 3n, 2m))
assertEquals(3.5m, Math.max(1n, 3.5m, 2n))

// Mixed int + BigInteger: result is the original winning Number
assertEquals(3n, Math.max(1, 3n, 2n))
assertEquals(3n, Math.max(3n, 1, 2n))
