// BigInteger: result type is BigInteger
assertEquals(1n, Math.min(1n, 3n, 2n))
assertEquals(-3n, Math.min(-1n, -3n, -2n))

// Decimal: result type is Decimal
assertEquals(1m, Math.min(1m, 3m, 2m))

// Mixed: result type matches the winning value
assertEquals(1n, Math.min(1n, 3n, 2m))
assertEquals(0.5m, Math.min(1n, 3.5m, 0.5m))

// Mixed int + BigInteger: result is the original winning Number
assertEquals(1n, Math.min(3, 1n, 2n))
assertEquals(1n, Math.min(1n, 3, 2n))
