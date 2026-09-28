var numObj = 77.1234;

assertEquals("7.71234e+1", numObj.toExponential());
assertEquals("7.7123e+1", numObj.toExponential(4));
assertEquals("7.71e+1", numObj.toExponential(2));
assertEquals("7.71234e+1", 77.1234.toExponential());
assertEquals("7.7e+1", 77 .toExponential());

// Special values
assertEquals("NaN", NaN.toExponential());
assertEquals("Infinity", Infinity.toExponential());
assertEquals("-Infinity", (-Infinity).toExponential());

// Zero digits
assertEquals("8e+1", (77.1234).toExponential(0));

// Negative number
assertEquals("-7.71234e+1", (-77.1234).toExponential());

// undefined argument behaves like no argument
assertEquals("7.71234e+1", (77.1234).toExponential(undefined));

// RangeError for out-of-range fraction digits
assertThrows(RangeError, () => (1).toExponential(-1));
assertThrows(RangeError, () => (1).toExponential(101));
