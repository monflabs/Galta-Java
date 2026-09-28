const v = {}

const s = new WeakRef(v);
assertEquals(v, s.deref())

assertThrows( () => new WeakRef(null) )
assertThrows( () => new WeakRef(undefined) )
assertThrows( () => new WeakRef(1) )
assertThrows( () => new WeakRef("abc") )
assertThrows( () => new WeakRef(true) )
assertThrows( () => new WeakRef(Symbol.for("blob")) )
