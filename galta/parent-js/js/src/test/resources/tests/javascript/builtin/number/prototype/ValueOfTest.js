// valueOf returns the primitive number value
assertEquals(14, (14).valueOf())
assertEquals(0, (0).valueOf())
assertEquals(-1, (-1).valueOf())
assertEquals(1.5, (1.5).valueOf())

// Special values
assertEquals(NaN, NaN.valueOf())
assertTrue(isNaN(NaN.valueOf()))
assertEquals(Infinity, Infinity.valueOf())
assertEquals(-Infinity, (-Infinity).valueOf())

// valueOf via prototype
assertEquals(42, Number.prototype.valueOf.call(42))
assertEquals(0, Number.prototype.valueOf.call(Number.prototype))

// TypeError when called on non-Number
assertThrows(TypeError, () => Number.prototype.valueOf.call("string"))
assertThrows(TypeError, () => Number.prototype.valueOf.call({}))
assertThrows(TypeError, () => Number.prototype.valueOf.call(true))
