let numObj = 5.123456

assertEquals("5.123456", numObj.toPrecision())
assertEquals("5.1235", numObj.toPrecision(5))
assertEquals("5.1", numObj.toPrecision(2))
assertEquals("5", numObj.toPrecision(1))

numObj = 0.000123

assertEquals("0.000123", numObj.toPrecision())
assertEquals("0.00012300", numObj.toPrecision(5))
assertEquals("0.00012", numObj.toPrecision(2))
assertEquals("0.0001", numObj.toPrecision(1))

// note that exponential notation might be returned in some circumstances
assertEquals("1.2e+3", (1234.5).toPrecision(2))

// Special values
assertEquals("NaN", NaN.toPrecision())
assertEquals("NaN", NaN.toPrecision(3))
assertEquals("Infinity", Infinity.toPrecision())
assertEquals("Infinity", Infinity.toPrecision(3))
assertEquals("-Infinity", (-Infinity).toPrecision(3))

// undefined argument behaves like no argument
assertEquals("5.123456", (5.123456).toPrecision(undefined))

// RangeError for out-of-range precision
assertThrows(RangeError, () => (1).toPrecision(0))
assertThrows(RangeError, () => (1).toPrecision(101))
assertThrows(RangeError, () => (1).toPrecision(-1))
