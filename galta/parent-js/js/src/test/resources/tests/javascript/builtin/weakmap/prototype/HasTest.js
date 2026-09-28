const map3 = new WeakMap()

const ONE = {}
const TWO = new String("two")
const THREE = Symbol("sym1")
map3.set(ONE,"one")
map3.set(TWO,"two")
map3.set(THREE,"three")

assertTrue(map3.has(ONE))
assertTrue(map3.has(TWO))
assertTrue(map3.has(THREE))

assertFalse(map3.has(4))
