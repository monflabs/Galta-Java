// Decimal128 carries its own NaN, infinities and -0: every special result is a Decimal128
const D = "org.monflabs.util.number.Decimal128"
const cls = v => v.$getClass().getName()
const isD128 = v => typeof v == "decimal" && cls(v) == D

const NAN = 0m/0m, INF = 1m/0m, NINF = -1m/0m, NZERO = -0m;
assertTrue(isD128(NAN) && NAN.isNaN())
assertTrue(isD128(INF) && !INF.isFinite() && INF.greaterThan(0m))
assertTrue(isD128(NINF) && !NINF.isFinite() && NINF.lessThan(0m))
assertTrue(isD128(NZERO) && Object.is(NZERO, Decimal128("-0")))
assertTrue(INF === Decimal128("Infinity"))
assertTrue(NINF === Decimal128("-Infinity"))
assertTrue(Object.is(NAN, Decimal128("NaN")))

// Arithmetic results
assertTrue(isD128(INF + 1m) && isD128(INF - INF) && isD128(-1m * 0m) && isD128(1m % 0m))
assertTrue(isD128(NZERO ** 3m) && isD128(0m ** -1m) && isD128(NAN ** 0m))
assertTrue(isD128(9e6144m * 10m) && isD128(1e-6176m / 10m) && isD128(1e7000m))
assertTrue(isD128(-(NZERO)) && isD128(-INF))
let v = NZERO;
v++;
assertTrue(isD128(v) && v === 1m)
v = INF;
v--;
assertTrue(isD128(v))

// Math results
assertTrue(isD128(Math.sqrt(-1m)) && isD128(Math.log(0m)) && isD128(Math.atanh(1m)))
assertTrue(isD128(Math.exp(100000m)) && isD128(Math.exp(NINF)) && isD128(Math.expm1(NINF)))
assertTrue(isD128(Math.ceil(-0.5m)) && isD128(Math.round(-0.2m)) && isD128(Math.trunc(-0.3m)))
assertTrue(isD128(Math.floor(INF)) && isD128(Math.sign(NZERO)) && isD128(Math.abs(NINF)))
assertTrue(isD128(Math.atan(INF)) && isD128(Math.atan2(NZERO, 0m)) && isD128(Math.hypot(INF, NAN)))
assertTrue(isD128(Math.max(NZERO, 0m)) && isD128(Math.min(1m, NINF)) && isD128(Math.max(1m, NAN)))
assertTrue(isD128(Math.pow(0m, -2m)) && isD128(Math.cbrt(NZERO)) && isD128(Math.sin(NZERO)))

// The Decimal128 methods agree with the operators
assertTrue(1m.divide(0m) === INF)
assertTrue(0m.divide(0m).isNaN())
assertTrue(Object.is((-1m).multiply(0m), NZERO))
assertTrue(1m.remainder(0m).isNaN())
assertTrue(INF.subtract(INF).isNaN())
assertTrue(NZERO.negate() === 0m && !Object.is(NZERO.negate(), NZERO))
assertTrue(NINF.abs() === INF)
// The proposal's round(): a value rounding to zero is +0 (Math.round/ceil/trunc give -0)
assertTrue(Decimal128("-0.4").round(0) === 0m && !Object.is(Decimal128("-0.4").round(0), NZERO))
assertTrue(Object.is(Math.round(-0.4m), NZERO))
assertTrue(isD128(Decimal(-0)) && !Object.is(Decimal(-0), NZERO))
assertTrue(NAN.compare(1m) != 0 && NAN.compare(1m) != 1 && NAN.compare(1m) != -1)
