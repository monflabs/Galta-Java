// Symbol.prototype.valueOf — returns the symbol primitive itself

const s1 = Symbol('test')
assertSame(s1, s1.valueOf())

const s2 = Symbol()
assertSame(s2, s2.valueOf())

// Global symbols
const sg = Symbol.for('valKey')
assertSame(sg, sg.valueOf())

// Different symbols with same description have distinct valueOf results
const a = Symbol('x')
const b = Symbol('x')
assertNotEquals(a.valueOf(), b.valueOf())

// valueOf called via prototype
assertSame(s1, Symbol.prototype.valueOf.call(s1))

// valueOf must not be called on non-Symbol
assertThrows(TypeError, () => Symbol.prototype.valueOf.call(42))
assertThrows(TypeError, () => Symbol.prototype.valueOf.call('str'))

// Result is the exact same reference as the symbol
const s3 = Symbol('eq')
const v = s3.valueOf()
assertSame(s3, v)
assertEquals(s3, v)
