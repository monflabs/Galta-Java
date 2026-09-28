const U64_CEIL = 2n ** 64n;

assertEquals(18446744073709551615n, BigInt.asUintN(64, U64_CEIL - 1n));
// 18446744073709551615n (2n ** 64n - 1n, the maximum non-wrapping value)
assertEquals(0, BigInt.asUintN(64, U64_CEIL));
// 0n (wraps to zero)
assertEquals(1, BigInt.asUintN(64, U64_CEIL + 1n));
// 1n
assertEquals(0, BigInt.asUintN(64, U64_CEIL * 2n));
// 0n (wraps on multiples)
assertEquals(0, BigInt.asUintN(64, U64_CEIL * -42n));
// 0n (also wraps on negative multiples)
