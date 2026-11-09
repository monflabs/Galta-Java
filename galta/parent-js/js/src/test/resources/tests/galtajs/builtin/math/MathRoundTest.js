// BigInteger: identity
assertEquals(5n, Math.round(5n))
assertEquals(-5n, Math.round(-5n))

// Decimal: floor(x + 0.5), rounds half toward +infinity, returns a Decimal
assertEquals(6m, Math.round(5.5m))
assertEquals(6m, Math.round(5.6m))
assertEquals(5m, Math.round(5.4m))
assertEquals(-5m, Math.round(-5.5m))
assertEquals(-5m, Math.round(-5.4m))
assertEquals(-6m, Math.round(-5.6m))
assertEquals("decimal", typeof Math.round(5.5m))
assertTrue(Object.is(Math.round(-0.5m), -0))
assertTrue(Object.is(Math.round(-0.2m), -0))
