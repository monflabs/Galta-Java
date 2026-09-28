// Default radix 10
assertEquals("1.2", (1.2).toString())
assertEquals("4568", (4568).toString())
assertEquals("0", (0).toString())
assertEquals("-1", (-1).toString())

// Special values
assertEquals("NaN", NaN.toString())
assertEquals("Infinity", Infinity.toString())
assertEquals("-Infinity", (-Infinity).toString())

// Radix conversions
assertEquals("1111", (15).toString(2))   // binary
assertEquals("17", (15).toString(8))     // octal
assertEquals("f", (15).toString(16))     // hex
assertEquals("ff", (255).toString(16))
assertEquals("-ff", (-255).toString(16))

// Radix boundaries (valid: 2..36)
assertEquals("z", (35).toString(36))
assertEquals("10", (36).toString(36))

// RangeError for invalid radix
assertThrows(RangeError, () => (1).toString(1))
assertThrows(RangeError, () => (1).toString(37))
assertThrows(RangeError, () => (1).toString(0))

// Type error: must be called on a Number
assertThrows(TypeError, () => Number.prototype.toString.call("string"))
assertThrows(TypeError, () => Number.prototype.toString.call({}))

// prototype toString returns "0"
assertEquals("0", Number.prototype.toString())
