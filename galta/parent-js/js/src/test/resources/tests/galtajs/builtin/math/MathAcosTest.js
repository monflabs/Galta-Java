// BigInteger: result is Decimal
// acos uses internal higher-precision pi, so results may differ from Math.PIm/2m by 1 ULP
assertTrue(Math.abs(Math.acos(0n) - Math.PIm/2m) < 1e-30m)
assertTrue(Math.abs(Math.acos(1n)) < 1e-30m)

// Decimal
assertTrue(Math.abs(Math.acos(0m) - Math.PIm/2m) < 1e-30m)
assertTrue(Math.abs(Math.acos(1m)) < 1e-30m)
