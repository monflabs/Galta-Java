const map3 = new WeakMap()

const ONE = {}
const TWO = new String("key")
const THREE = Symbol("sym1")
map3.set(ONE,"one")

assertEquals("one", map3.getOrInsert(ONE,"fake"))
assertEquals("two", map3.getOrInsert(TWO,"two"))
assertEquals("three", map3.getOrInsert(THREE,"three"))

assertEquals("one", map3.get(ONE))
assertEquals("two", map3.get(TWO))
assertEquals("three", map3.get(THREE))
assertEquals(undefined, map3.get(4))

map3.get(1)
assertThrows(TypeError, () => map3.getOrInsert(1, "b"))
