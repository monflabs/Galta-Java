// Object.getOwnPropertySymbols — returns all own symbol-keyed properties

const sym1 = Symbol('one')
const sym2 = Symbol('two')
const sym3 = Symbol('three')

const obj = {}
obj[sym1] = 1
obj[sym2] = 2
obj.string = 'not a symbol'

const syms = Object.getOwnPropertySymbols(obj)
assertEquals(2, syms.length)
assertEquals(true, syms.includes(sym1))
assertEquals(true, syms.includes(sym2))
assertEquals(false, syms.includes(sym3))

// String-keyed properties are not included
assertEquals(false, syms.includes('string'))

// Object with no symbol properties returns empty array
const plain = { a: 1, b: 2 }
assertEquals(0, Object.getOwnPropertySymbols(plain).length)

// Object with only symbol properties
const symOnly = {}
symOnly[sym3] = 'three'
const onlySyms = Object.getOwnPropertySymbols(symOnly)
assertEquals(1, onlySyms.length)
assertEquals(sym3, onlySyms[0])

// Well-known symbols are exposed on objects that set them
const withWKS = {}
withWKS[Symbol.iterator] = function() {}
const wkSyms = Object.getOwnPropertySymbols(withWKS)
assertEquals(1, wkSyms.length)
assertEquals(Symbol.iterator, wkSyms[0])

// Symbols from Object.getOwnPropertySymbols are the exact same symbol references
assertEquals(sym1, Object.getOwnPropertySymbols(obj).find(s => s === sym1))
