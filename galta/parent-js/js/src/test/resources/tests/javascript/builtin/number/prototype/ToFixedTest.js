var numObj = 12345.6789;

assertEquals("12346", numObj.toFixed());
assertEquals("12345.7", numObj.toFixed(1));
assertEquals("12345.678900", numObj.toFixed(6));

assertEquals("123000000000000000000.00", (1.23e+20).toFixed(2));
assertEquals("0.00", (1.23e-10).toFixed(2));
assertEquals("2.3", 2.34.toFixed(1));
assertEquals("2.4", 2.35.toFixed(1));
assertEquals("2.5", 2.55.toFixed(1));
//assertEquals("-2.3", -2.34.toFixed(1)); -> depends on the engine and how "-" is interpreted
assertEquals("-2.3", (-2.34).toFixed(1));

// Special values
assertEquals("NaN", NaN.toFixed(2));
assertEquals("Infinity", Infinity.toFixed(2));
assertEquals("-Infinity", (-Infinity).toFixed(2));

// 0 fraction digits (default)
assertEquals("12346", numObj.toFixed(0));

// Max allowed (100): "1." + 100 zeros = 102 chars
assertEquals(102, (1).toFixed(100).length)

// RangeError: negative or > 100
assertThrows(RangeError, () => (1).toFixed(-1));
assertThrows(RangeError, () => (1).toFixed(101));
