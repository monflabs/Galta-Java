const s1 = Symbol.for("a")
const s1_ = Symbol.for("a")
	
const s2 = Symbol.for("b")
const s2_ = Symbol.for("b")

assertEquals(s1,s1_);
assertEquals(s2,s2_);

assertNotEquals(s1,s2);
assertNotEquals(s1_,s2_);
