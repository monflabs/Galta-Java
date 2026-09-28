const b = "456"

assertEquals("",b.repeat(0))
assertEquals("456",b.repeat(1))
assertEquals("456456",b.repeat(2))
assertEquals("","".repeat(1000))

// NaN treated as 0
assertEquals("", b.repeat(NaN))

// RangeError for negative count or Infinity
assertThrows(RangeError, () => b.repeat(-1))
assertThrows(RangeError, () => b.repeat(Infinity))

// Large repetition
assertEquals("aaa", "a".repeat(3))
