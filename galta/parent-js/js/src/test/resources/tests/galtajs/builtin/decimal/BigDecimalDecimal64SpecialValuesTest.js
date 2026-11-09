// The exponent range follows the MathContext precision: DECIMAL64 is 16 digits, adjusted exponent up to 384
const isNegZero = x => x == 0m && Object.is(x, -0);
assertTrue(1m / 3m === 0.3333333333333333m)
assertTrue(9e384m * 10m === Infinity)
assertTrue(-9e384m * 10m === -Infinity)
assertTrue(9e384m > 0m && typeof 9e384m == "decimal")
assertTrue(1e385m === Infinity)
assertTrue(1e-398m / 10m === 0m)
assertTrue(isNegZero(-1e-398m / 10m))
assertTrue(10m ** 400m === Infinity)
assertTrue(Math.exp(1000m) === Infinity)
