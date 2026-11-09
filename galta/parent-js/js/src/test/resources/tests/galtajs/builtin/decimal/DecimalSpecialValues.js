// Decimal special values (IEEE 754 Decimal128 / TC39 proposal-decimal), shared by
// the Decimal128 and the BigDecimal engines: the same expectations hold whatever
// the representation. A Decimal128 carries its own NaN, infinities and -0; with
// BigDecimal they are the matching Numbers. The values are produced by Decimal
// operations, so each engine uses its own representation.
const NAN = 0m/0m;
const INF = 1m/0m;
const NINF = -1m/0m;
const NZERO = -0m;

const isNaNv = x => x !== x;
const isNegZero = x => x == 0m && Object.is(x, NZERO);
const isPosZero = x => x == 0m && !Object.is(x, NZERO);
const isInf = x => x === INF;
const isNInf = x => x === NINF;
const between = (x, lo, hi) => x > lo && x < hi;

// The special values themselves
assertTrue(isNaNv(NAN))
assertTrue(INF > 9.999999999999999999999999999999999e6144m)
assertTrue(NINF < -9.999999999999999999999999999999999e6144m)
assertTrue(isNegZero(NZERO))
assertTrue(isPosZero(0m))
assertFalse(Object.is(NZERO, 0m))
assertTrue(NZERO === 0m)
assertTrue(NZERO == 0m)
assertFalse(NAN === NAN)
assertFalse(NAN == NAN)
assertTrue(Object.is(NAN, NAN))
assertFalse(!!NAN)
assertFalse(!!NZERO)
assertTrue(!!INF)

//
// Addition
//
assertTrue(isNaNv(NAN + 1m))
assertTrue(isNaNv(1m + NAN))
assertTrue(isNaNv(INF + NINF))
assertTrue(isInf(INF + 1m))
assertTrue(isInf(INF + INF))
assertTrue(isNInf(NINF + 1e6000m))
assertTrue(isNegZero(NZERO + NZERO))
assertTrue(isPosZero(NZERO + 0m))
assertTrue(isPosZero(0m + NZERO))
assertTrue(isPosZero(-5m + 5m))
assertTrue(NZERO + 1m === 1m)
// A Number -0 operand is -0 too (only the Decimal() constructor turns it into +0)
assertTrue(isNegZero(NZERO + -0))
assertTrue(isNegZero(-0 * 1m))
assertTrue(Object.is(NZERO, -0))
assertFalse(Object.is(0m, -0))
assertTrue(NZERO + -1m === -1m)

//
// Subtraction
//
assertTrue(isNaNv(NAN - 1m))
assertTrue(isNaNv(INF - INF))
assertTrue(isInf(INF - NINF))
assertTrue(isNInf(1m - INF))
assertTrue(isPosZero(5m - 5m))
assertTrue(isNegZero(NZERO - 0m))
assertTrue(isPosZero(0m - 0m))
assertTrue(isPosZero(NZERO - NZERO))

//
// Multiplication
//
assertTrue(isNaNv(NAN * 2m))
assertTrue(isNaNv(INF * 0m))
assertTrue(isNaNv(NZERO * NINF))
assertTrue(isInf(INF * 2m))
assertTrue(isNInf(INF * -2m))
assertTrue(isInf(NINF * NINF))
assertTrue(isNegZero(-1m * 0m))
assertTrue(isNegZero(0m * -1m))
assertTrue(isNegZero(NZERO * 5m))
assertTrue(isPosZero(NZERO * -5m))
assertTrue(isPosZero(NZERO * NZERO))

//
// Division
//
assertTrue(isInf(1m / 0m))
assertTrue(isNInf(-1m / 0m))
assertTrue(isNInf(1m / NZERO))
assertTrue(isInf(-1m / NZERO))
assertTrue(isNaNv(0m / 0m))
assertTrue(isNaNv(NZERO / 0m))
assertTrue(isNaNv(INF / INF))
assertTrue(isNaNv(NAN / 1m))
assertTrue(isInf(INF / 2m))
assertTrue(isNInf(INF / -2m))
assertTrue(isNInf(INF / NZERO))
assertTrue(isPosZero(1m / INF))
assertTrue(isNegZero(-1m / INF))
assertTrue(isNegZero(1m / NINF))
assertTrue(isNegZero(0m / -5m))
assertTrue(isNegZero(NZERO / 5m))
assertTrue(1m / 4m === 0.25m)

//
// Remainder: the sign of the dividend, like Number's %
//
assertTrue(7m % 3m === 1m)
assertTrue(-7m % 3m === -1m)
assertTrue(7m % -3m === 1m)
assertTrue(isNegZero(-6m % 3m))
assertTrue(isNegZero(NZERO % 3m))
assertTrue(isPosZero(6m % -3m))
assertTrue(isNaNv(1m % 0m))
assertTrue(isNaNv(1m % NZERO))
assertTrue(isNaNv(INF % 2m))
assertTrue(isNaNv(NAN % 2m))
assertTrue(isNaNv(2m % NAN))
assertTrue(5.5m % INF === 5.5m)
assertTrue(isNegZero(NZERO % INF))
assertTrue(1e40m % 3m === 1m)

//
// Exponentiation
//
assertTrue(2m ** 10m === 1024m)
assertTrue((-2m) ** 3m === -8m)
assertTrue(NAN ** 0m === 1m)
assertTrue(INF ** 0m === 1m)
assertTrue(isNaNv(NAN ** 1m))
assertTrue(isNaNv(1m ** NAN))
assertTrue(isNaNv((-8m) ** 0.5m))
assertTrue(isNaNv((-1m) ** INF))
assertTrue(isInf(0m ** -1m))
assertTrue(isPosZero(0m ** 2m))
assertTrue(isNegZero(NZERO ** 3m))
assertTrue(isPosZero(NZERO ** 2m))
assertTrue(isNInf(NZERO ** -3m))
assertTrue(isInf(NZERO ** -2m))
assertTrue(isInf(INF ** 2m))
assertTrue(isPosZero(INF ** -1m))
assertTrue(isNInf(NINF ** 3m))
assertTrue(isInf(NINF ** 2m))
assertTrue(isNegZero(NINF ** -3m))
assertTrue(isInf(2m ** INF))
assertTrue(isPosZero(0.5m ** INF))
assertTrue(1m ** 1e30m === 1m)
assertTrue((-1m) ** 3m === -1m)
assertTrue(isInf(2m ** 1e30m))
assertTrue(isPosZero(0.5m ** 1e30m))
assertTrue(isNInf((-2m) ** 1000000001m))

//
// Unary minus, increment, decrement
//
assertTrue(isNegZero(-0m))
assertTrue(isNegZero(-(0m)))
assertTrue(isPosZero(-(NZERO)))
assertTrue(isNInf(-INF))
assertTrue(isInf(-NINF))
assertTrue(isNaNv(-NAN))
let v = NZERO;
v++;
assertTrue(v == 1m)
v = INF;
v++;
assertTrue(isInf(v))
v = NINF;
v--;
assertTrue(isNInf(v))
v = NAN;
v++;
assertTrue(isNaNv(v))
v = -1m;
v++;
assertTrue(isPosZero(v))
v = 1m;
v--;
assertTrue(isPosZero(v))

//
// Overflow and underflow of the Decimal128 exponent range
//
const MAX = 9.999999999999999999999999999999999e6144m;
assertTrue(isInf(MAX + MAX))
assertTrue(isNInf(-MAX - MAX))
assertTrue(isInf(9e6144m * 10m))
assertTrue(isNInf(-9e6144m * 10m))
assertTrue(isInf(MAX / 0.1m))
assertTrue(isInf(1e7000m))
assertTrue(isNInf(-1e7000m))
assertTrue(isPosZero(1e-7000m))
assertTrue(isPosZero(1e-6176m / 10m))
assertTrue(isNegZero(-1e-6176m / 10m))
assertTrue(isPosZero(1e-6176m * 1e-10m))
assertTrue(1e-6176m > 0m)
assertTrue(isInf(10m ** 7000m))
assertTrue(isNInf((-10m) ** 7001m))
assertTrue(isPosZero(10m ** -7000m))

//
// Comparisons
//
assertTrue(1m < INF)
assertTrue(NINF < -1e6000m)
assertTrue(INF > 1e6000m)
assertTrue(INF >= INF)
assertTrue(NINF <= NINF)
assertFalse(INF == 1m)
assertTrue(INF == INF)
assertTrue(INF === INF)
assertFalse(INF === NINF)
assertFalse(NAN < 1m)
assertFalse(NAN > 1m)
assertFalse(NAN <= 1m)
assertFalse(NAN >= 1m)
assertFalse(NAN == 1m)
assertTrue(NAN != NAN)
assertFalse(NZERO < 0m)
assertTrue(NZERO <= 0m)
assertTrue(NZERO >= 0m)

//
// Decimal()
//
assertTrue(isNaNv(Decimal("NaN")))
assertTrue(isInf(Decimal("Infinity")))
assertTrue(isNInf(Decimal("-Infinity")))
assertTrue(isNegZero(Decimal("-0")))
assertTrue(isNegZero(Decimal("-0.00")))
assertTrue(isInf(Decimal("1e7000")))
assertTrue(isNaNv(Decimal(NaN)))
assertTrue(isInf(Decimal(Infinity)))
assertTrue(isNInf(Decimal(-Infinity)))
// A Number goes through its string form (toExponential): -0 is +0, unlike the string "-0"
assertTrue(isPosZero(Decimal(-0)))
assertTrue(Decimal("12.5") === 12.5m)
assertThrows(() => Decimal("abc"))

//
// Math: special arguments
//
assertTrue(isInf(Math.abs(NINF)))
assertTrue(isPosZero(Math.abs(NZERO)))
assertTrue(isNaNv(Math.abs(NAN)))

assertTrue(isNaNv(Math.acos(2m)))
assertTrue(isNaNv(Math.acos(INF)))
assertTrue(isPosZero(Math.acos(1m)))
assertTrue(isNaNv(Math.acosh(0.5m)))
assertTrue(isInf(Math.acosh(INF)))
assertTrue(isPosZero(Math.acosh(1m)))
assertTrue(isNaNv(Math.asin(-2m)))
assertTrue(isNaNv(Math.asin(NINF)))
assertTrue(isNegZero(Math.asin(NZERO)))
assertTrue(isNInf(Math.asinh(NINF)))
assertTrue(isNegZero(Math.asinh(NZERO)))
assertTrue(between(Math.atan(INF), 1.5707963m, 1.5707964m))
assertTrue(between(Math.atan(NINF), -1.5707964m, -1.5707963m))
assertTrue(isNegZero(Math.atan(NZERO)))
assertTrue(isInf(Math.atanh(1m)))
assertTrue(isNInf(Math.atanh(-1m)))
assertTrue(isNaNv(Math.atanh(2m)))
assertTrue(isNegZero(Math.atanh(NZERO)))

assertTrue(isPosZero(Math.atan2(0m, 1m)))
assertTrue(isNegZero(Math.atan2(NZERO, 1m)))
assertTrue(isNegZero(Math.atan2(NZERO, 0m)))
assertTrue(between(Math.atan2(0m, -1m), 3.1415926m, 3.1415927m))
assertTrue(between(Math.atan2(0m, NZERO), 3.1415926m, 3.1415927m))
assertTrue(isPosZero(Math.atan2(1m, INF)))
assertTrue(between(Math.atan2(INF, INF), 0.7853981m, 0.7853982m))
assertTrue(between(Math.atan2(1m, 0m), 1.5707963m, 1.5707964m))
assertTrue(isNaNv(Math.atan2(NAN, 1m)))
assertTrue(isNaNv(Math.atan2(1m, NAN)))

assertTrue(Math.cbrt(-8m) === -2m)
assertTrue(Math.cbrt(27m) === 3m)
assertTrue(isNInf(Math.cbrt(NINF)))
assertTrue(isNegZero(Math.cbrt(NZERO)))

assertTrue(isNaNv(Math.cos(INF)))
assertTrue(Math.cos(NZERO) == 1m)
assertTrue(isInf(Math.cosh(NINF)))

assertTrue(isInf(Math.exp(100000m)))
assertTrue(isPosZero(Math.exp(-100000m)))
assertTrue(isInf(Math.exp(INF)))
assertTrue(isPosZero(Math.exp(NINF)))
assertTrue(isNaNv(Math.exp(NAN)))
assertTrue(Math.exp(NZERO) == 1m)
assertTrue(Math.expm1(-100000m) == -1m)
assertTrue(Math.expm1(NINF) == -1m)
assertTrue(isInf(Math.expm1(INF)))
assertTrue(isNegZero(Math.expm1(NZERO)))

assertTrue(isNInf(Math.log(0m)))
assertTrue(isNInf(Math.log(NZERO)))
assertTrue(isNaNv(Math.log(-1m)))
assertTrue(isInf(Math.log(INF)))
assertTrue(isNaNv(Math.log(NINF)))
assertTrue(isPosZero(Math.log(1m)))
assertTrue(isNInf(Math.log10(0m)))
assertTrue(isNaNv(Math.log10(-1m)))
assertTrue(Math.log10(1000m) == 3m)
assertTrue(isNInf(Math.log2(0m)))
assertTrue(isInf(Math.log2(INF)))
assertTrue(Math.log2(8m) == 3m)
assertTrue(isNInf(Math.log1p(-1m)))
assertTrue(isNaNv(Math.log1p(-2m)))
assertTrue(isInf(Math.log1p(INF)))
assertTrue(isNegZero(Math.log1p(NZERO)))

assertTrue(isNaNv(Math.sin(INF)))
assertTrue(isNegZero(Math.sin(NZERO)))
assertTrue(isInf(Math.sinh(INF)))
assertTrue(isNInf(Math.sinh(NINF)))
assertTrue(isNegZero(Math.sinh(NZERO)))
assertTrue(isNaNv(Math.tan(NINF)))
assertTrue(isNegZero(Math.tan(NZERO)))
assertTrue(Math.tanh(INF) == 1m)
assertTrue(Math.tanh(NINF) == -1m)
assertTrue(isNegZero(Math.tanh(NZERO)))

assertTrue(isNaNv(Math.sqrt(-1m)))
assertTrue(isNaNv(Math.sqrt(NINF)))
assertTrue(isInf(Math.sqrt(INF)))
assertTrue(isNegZero(Math.sqrt(NZERO)))
assertTrue(Math.sqrt(16m) === 4m)

assertTrue(isInf(Math.hypot(INF, NAN)))
assertTrue(isInf(Math.hypot(1m, NINF)))
assertTrue(isNaNv(Math.hypot(1m, NAN)))
assertTrue(Math.hypot(3m, 4m) == 5m)

assertTrue(isInf(Math.pow(0m, -2m)))
assertTrue(Math.pow(INF, 0m) == 1m)
assertTrue(isNaNv(Math.pow(NAN, 1m)))
assertTrue(isNegZero(Math.pow(NZERO, 3m)))
assertTrue(isInf(Math.pow(10m, 7000m)))

assertTrue(isInf(Math.max(1m, INF)))
assertTrue(Math.max(1m, NINF) == 1m)
assertTrue(isNInf(Math.min(1m, NINF)))
assertTrue(isNaNv(Math.max(1m, NAN, 2m)))
assertTrue(isNaNv(Math.max(NAN, 1m)))
assertTrue(isNaNv(Math.min(1m, NAN)))
assertTrue(isPosZero(Math.max(NZERO, 0m)))
assertTrue(isPosZero(Math.max(0m, NZERO)))
assertTrue(isNegZero(Math.min(0m, NZERO)))
assertTrue(isNegZero(Math.min(NZERO, 0m)))
assertTrue(Math.max(1m, 3m, 2m) === 3m)
assertTrue(Math.min(1m, 3m, -2m) === -2m)

assertTrue(Math.sign(-3m) == -1m)
assertTrue(Math.sign(3m) == 1m)
assertTrue(Math.sign(INF) == 1m)
assertTrue(Math.sign(NINF) == -1m)
assertTrue(isNegZero(Math.sign(NZERO)))
assertTrue(isPosZero(Math.sign(0m)))
assertTrue(isNaNv(Math.sign(NAN)))

// Rounding to an integer: a negative value rounding to zero is -0
assertTrue(Math.floor(5.5m) === 5m)
assertTrue(Math.floor(-5.5m) === -6m)
assertTrue(Math.floor(-0.5m) === -1m)
assertTrue(isNegZero(Math.floor(NZERO)))
assertTrue(isInf(Math.floor(INF)))
assertTrue(isNaNv(Math.floor(NAN)))
assertTrue(Math.ceil(5.5m) === 6m)
assertTrue(isNegZero(Math.ceil(-0.5m)))
assertTrue(isNInf(Math.ceil(NINF)))
assertTrue(isNaNv(Math.ceil(NAN)))
assertTrue(Math.trunc(-5.9m) === -5m)
assertTrue(isNegZero(Math.trunc(-0.3m)))
assertTrue(isPosZero(Math.trunc(0.3m)))
assertTrue(isInf(Math.trunc(INF)))
assertTrue(Math.round(2.5m) === 3m)
assertTrue(Math.round(-2.5m) === -2m)
assertTrue(Math.round(-5.6m) === -6m)
assertTrue(isNegZero(Math.round(-0.5m)))
assertTrue(isNegZero(Math.round(-0.2m)))
assertTrue(isPosZero(Math.round(0.2m)))
assertTrue(isNInf(Math.round(NINF)))
assertTrue(isNaNv(Math.round(NAN)))
