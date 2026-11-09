// A BigDecimal has no NaN, infinities or -0: these Decimal values are the matching Numbers
const NAN = 0m/0m, INF = 1m/0m, NINF = -1m/0m, NZERO = -0m;
assertTrue(typeof 1m == "decimal" && 1m.$getClass().getName() == "java.math.BigDecimal")
assertTrue(typeof NAN == "number" && Number.isNaN(NAN))
assertTrue(typeof INF == "number" && INF === Infinity)
assertTrue(typeof NINF == "number" && NINF === -Infinity)
assertTrue(typeof NZERO == "number" && Object.is(NZERO, -0))

// Finite results stay Decimals
assertTrue(typeof (NZERO + 1m) == "decimal")
assertTrue(typeof (5.5m % INF) == "decimal")
assertTrue(typeof (NAN ** 0m) == "decimal")
assertTrue(typeof (INF ** 0m) == "decimal")
assertTrue(typeof Math.ceil(5.5m) == "decimal")
assertTrue(typeof Math.round(-5.6m) == "decimal")
assertTrue(typeof Math.max(1m, NINF) == "decimal")
assertTrue(typeof Math.pow(INF, 0m) == "decimal")
assertTrue(typeof Math.sqrt(2m) == "decimal")

// Special results are Numbers
assertTrue(typeof (INF + 1m) == "number")
assertTrue(Object.is(-1m * 0m, -0))
assertTrue(Object.is(Math.ceil(-0.5m), -0))
assertTrue(Math.log(0m) === -Infinity)
assertTrue(Number.isNaN(Math.sqrt(-1m)))

// The Number special values combine with Decimals as Decimal special values
assertTrue(Infinity + 1m === Infinity)
assertTrue(Number.isNaN(NaN * 1m))
assertTrue(-0 + 1m === 1m)
assertTrue(typeof (-0 + 1m) == "decimal")
assertTrue(Object.is(-0 * 1m, -0))
assertTrue(1m < Infinity)
assertTrue(-0 === 0m)
assertTrue(typeof Decimal(-0) == "decimal" && !Object.is(Decimal(-0), -0))
assertTrue(Object.is(Decimal("-0"), -0))
