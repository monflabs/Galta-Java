assertEquals("true", true.toString())
assertEquals("false", false.toString())

// Via prototype
assertEquals("true", Boolean.prototype.toString.call(true))
assertEquals("false", Boolean.prototype.toString.call(false))

// On prototype itself (should return "false")
assertEquals("false", Boolean.prototype.toString())

// TypeError when called on non-Boolean
assertThrows(TypeError, () => Boolean.prototype.toString.call(1))
assertThrows(TypeError, () => Boolean.prototype.toString.call(""))
assertThrows(TypeError, () => Boolean.prototype.toString.call({}))
