// BigInteger: identity
assertEquals(5n, Math.round(5n))
assertEquals(-5n, Math.round(-5n))

// Decimal: floor(x + 0.5), rounds half toward +infinity, returns BigInteger
assertEquals(6n, Math.round(5.5m))
assertEquals(6n, Math.round(5.6m))
assertEquals(5n, Math.round(5.4m))
assertEquals(-5n, Math.round(-5.5m))
assertEquals(-5n, Math.round(-5.4m))
assertEquals(-6n, Math.round(-5.6m))
