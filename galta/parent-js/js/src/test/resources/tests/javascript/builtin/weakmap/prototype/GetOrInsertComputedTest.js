const map3 = new WeakMap()

const ONE = {}
const TWO = new String("key")
const THREE = Symbol("sym1")
map3.set(ONE,"one")

assertEquals("one", map3.getOrInsertComputed(ONE, () => "fake"))
assertEquals("two", map3.getOrInsertComputed(TWO, () => "two"))
assertEquals("three", map3.getOrInsertComputed(THREE, () => "three"))

assertEquals("one", map3.get(ONE))
assertEquals("two", map3.get(TWO))
assertEquals("three", map3.get(THREE))
assertEquals(undefined, map3.get(4))

map3.get(1)
assertThrows(TypeError, () => map3.getOrInsertComputed(1, () => "b"))
