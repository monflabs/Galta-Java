const map3 = new WeakMap()

const ONE = {}
const TWO = new String("two")
const THREE = Symbol("sym1")
map3.set(ONE,"one")
map3.set(TWO,"two")
map3.set(THREE,"three")

assertEquals("one", map3.get(ONE))
assertEquals("two", map3.get(TWO))
assertEquals("three", map3.get(THREE))

assertEquals(undefined, map3.get(4))
