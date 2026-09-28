assertEquals(36,6**2)
assertEquals(36,6**2L)
assertThrows(TypeError, () => 6**2n)
assertEquals(36,6**2.0)
assertEquals(36.0,6**2.0f)
assertEquals(36.0,6**2.0)
assertThrows( TypeError, () => 6**2.0m)

assertEquals(9.610000000000001,3.1**2)
assertThrows( TypeError, () => 3.1m**2)

// ** is right-associative, unlike every other binary operator: 2**3**2 === 2**(3**2) = 512, not (2**3)**2 = 64.
assertEquals(512, 2**3**2)
assertEquals(512, 2**(3**2))

// An exponent of +-0 is always 1, even for a NaN base (Math.pow's IEEE-754 rule).
assertEquals(1, NaN**0)
assertEquals(1, NaN**-0)
