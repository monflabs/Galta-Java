const s = new WeakSet();
assertNotNull(s)

const snull = new WeakSet(null);
assertNotNull(snull)
assertFalse(snull.has(null))

const sundefined = new WeakSet(undefined);
assertNotNull(sundefined)
assertFalse(sundefined.has(undefined))


const ONE = {}
const TWO = new String("two")
const THREE = Symbol("sym1")
const FOUR = new Number(79)

const setp = new WeakSet([ONE,TWO,THREE]);
assertNotNull(setp)
assertTrue(setp.has(ONE))
assertTrue(setp.has(TWO))
assertTrue(setp.has(THREE))

const set3 = new WeakSet();
set3.add(ONE)
set3.add(TWO)
set3.add(THREE)
set3.add(FOUR)
set3.add(Symbol.iterator)

assertTrue(set3.has(ONE))
assertTrue(set3.has(TWO))
assertTrue(set3.has(THREE))
assertTrue(set3.has(FOUR))
assertTrue(set3.has(Symbol.iterator))
assertFalse(set3.has("SSS"))

assertThrows( () => set3.add(null) )
assertThrows( () => set3.add(undefined) )
assertThrows( () => set3.add(1) )
assertThrows( () => set3.add("abc") )
assertThrows( () => set3.add(true) )
assertThrows( () => set3.add(Symbol.for("blob")) )


// Check property assignment
{
	const s1 = new WeakSet();
	s1.a = 123
	assertEquals(123, s1.a)
	const sy = Symbol()
	s1[sy] = 456
	assertEquals(456, s1[sy])
	assertEquals(123, s1.a)
}
