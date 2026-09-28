const s = new WeakSet();

const ONE = {}
const TWO = new String("two")

s.add(ONE)
assertTrue(s.has(ONE))
assertFalse(s.has(TWO))

s.add(TWO)
assertTrue(s.has(TWO))
