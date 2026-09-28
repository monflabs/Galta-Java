const I64_CEIL = 2n ** 63n;

assertEquals(9223372036854775807n, BigInt.asIntN(64, I64_CEIL - 1n));
// 9223372036854775807n (2n ** 64n - 1n, the maximum non-wrapping value)
assertEquals(-9223372036854775808n, BigInt.asIntN(64, I64_CEIL));
// -9223372036854775808n (wraps to min value)
assertEquals(-9223372036854775807n, BigInt.asIntN(64, I64_CEIL + 1n));
// -9223372036854775807n (min value + 1n)
assertEquals(0, BigInt.asIntN(64, I64_CEIL * 2n));
// 0n (wrapped around to zero)
assertEquals(0, BigInt.asIntN(64, -I64_CEIL * -42n));
// 0n (also wraps on negative multiples)
