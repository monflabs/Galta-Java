assertEquals("AbC", "AbC".valueOf())
assertEquals("", "".valueOf())
assertEquals("hello world", "hello world".valueOf())

// Via prototype
assertEquals("test", String.prototype.valueOf.call("test"))

// On prototype itself — returns ""
assertEquals("", String.prototype.valueOf())

// Result is the same primitive string
const s = "hello"
assertEqualsStrict(s, s.valueOf())

// TypeError when called on non-String
assertThrows(TypeError, () => String.prototype.valueOf.call(42))
assertThrows(TypeError, () => String.prototype.valueOf.call({}))
assertThrows(TypeError, () => String.prototype.valueOf.call(true))
