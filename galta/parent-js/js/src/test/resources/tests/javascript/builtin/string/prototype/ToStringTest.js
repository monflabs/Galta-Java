assertEquals("", "".toString())
assertEquals("34", "34".toString())
assertEquals("hello", "hello".toString())

// Via prototype
assertEquals("world", String.prototype.toString.call("world"))

// On prototype itself — returns ""
assertEquals("", String.prototype.toString())

// TypeError when called on non-String
assertThrows(TypeError, () => String.prototype.toString.call(42))
assertThrows(TypeError, () => String.prototype.toString.call({}))
assertThrows(TypeError, () => String.prototype.toString.call(true))
