// Without mixed big numbers: a Decimal and a Decimal special value still combine,
// other Numbers do not. Shared by the Decimal128 and the BigDecimal engines.
const NAN = 0m/0m;
const INF = 1m/0m;
const NZERO = -0m;
const isNegZero = x => x == 0m && Object.is(x, NZERO);

assertTrue(INF + 1m === INF)
assertTrue(1m - INF === -INF)
assertTrue(INF * -2m === -INF)
assertTrue((NAN + 1m) !== (NAN + 1m))
assertTrue((NAN * 2m) !== (NAN * 2m))
assertTrue(NZERO + 1m === 1m)
assertTrue(isNegZero(NZERO * 1m))
assertTrue(isNegZero(-1m * 0m))
assertTrue(isNegZero(NZERO % 2m))
assertTrue(1m / NZERO === -INF)
assertTrue(NZERO === 0m)
assertTrue(1m < INF)
assertTrue(-INF < -1m)
assertFalse(NAN < 1m)
assertThrows(() => 1m + 1.5)
assertThrows(() => 1m + 1)
assertThrows(() => 2 * 1m)
