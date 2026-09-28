const set3 = new WeakMap()

const ONE = {}
const TWO = new String("two")
const THREE = Symbol("sym1")
set3.set(ONE,"one")
set3.set(TWO,"two")
set3.set(THREE,"three")

assertTrue(set3.has(ONE))
assertTrue(set3.has(TWO))
assertTrue(set3.has(THREE))

assertFalse(set3.has(4))
