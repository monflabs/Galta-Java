assertEquals(true, true.valueOf())
assertEquals(false, false.valueOf())

// Via prototype
assertEquals(true, Boolean.prototype.valueOf.call(true))
assertEquals(false, Boolean.prototype.valueOf.call(false))

// On prototype itself (should return false)
assertEquals(false, Boolean.prototype.valueOf())

// Result is a primitive boolean
assertEqualsStrict("boolean", typeof true.valueOf())
assertEqualsStrict("boolean", typeof false.valueOf())

// TypeError when called on non-Boolean
assertThrows(TypeError, () => Boolean.prototype.valueOf.call(1))
assertThrows(TypeError, () => Boolean.prototype.valueOf.call("string"))
assertThrows(TypeError, () => Boolean.prototype.valueOf.call({}))
