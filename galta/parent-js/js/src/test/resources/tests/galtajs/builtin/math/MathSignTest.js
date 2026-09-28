// BigInteger: returns integer signum
assertEquals(1, Math.sign(3n))
assertEquals(-1, Math.sign(-3n))
assertEquals(0, Math.sign(0n))

// Decimal: returns integer signum
assertEquals(1, Math.sign(3m))
assertEquals(-1, Math.sign(-3m))
assertEquals(0, Math.sign(0m))
