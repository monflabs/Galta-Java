// Symbol.prototype.toString — returns "Symbol(<description>)"

const s1 = Symbol()
assertEquals('Symbol()', s1.toString())

const s2 = Symbol('foo')
assertEquals('Symbol(foo)', s2.toString())

const s3 = Symbol('bar baz')
assertEquals('Symbol(bar baz)', s3.toString())

// Well-known symbols
assertEquals('Symbol(Symbol.iterator)', Symbol.iterator.toString())
assertEquals('Symbol(Symbol.toPrimitive)', Symbol.toPrimitive.toString())

// Global symbols (Symbol.for)
const sg = Symbol.for('globalSym')
assertEquals('Symbol(globalSym)', sg.toString())

// toString called explicitly via prototype
assertEquals('Symbol(foo)', Symbol.prototype.toString.call(s2))

// toString must not be called on non-Symbol
assertThrows(TypeError, () => Symbol.prototype.toString.call(42))
assertThrows(TypeError, () => Symbol.prototype.toString.call('str'))

// Symbols cannot be implicitly coerced to string via +
assertThrows(TypeError, () => 'prefix' + s2)

// Explicit String() conversion uses toString()
assertEquals('Symbol(foo)', String(s2))
