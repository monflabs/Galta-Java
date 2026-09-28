const g1 = Symbol.for("a")
const s1 = Symbol("b")

assertEquals( "a", Symbol.keyFor(g1) )
assertEquals( undefined, Symbol.keyFor(s1) )

assertThrows( () => Symbol.keyFor() )
assertThrows( () => Symbol.keyFor("a") )