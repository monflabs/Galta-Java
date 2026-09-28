assertEquals(6, Math.sumPrecise([1, 2, 3]))
assertEquals(0, Math.sumPrecise([]))
assertEquals(true, Object.is(-0, Math.sumPrecise([])))
assertEquals(true, Object.is(-0, Math.sumPrecise([-0, -0])))
assertEquals(0, Math.sumPrecise([-0, 0]))
assertEquals(Infinity, Math.sumPrecise([Infinity, 1]))
assertEquals(true, isNaN(Math.sumPrecise([Infinity, -Infinity])))
assertEquals(true, isNaN(Math.sumPrecise([NaN, 1])))

// Exact summation avoids the precision loss of naive sequential addition.
assertEquals(1, Math.sumPrecise([1e300, 1, -1e300]))

try {
	Math.sumPrecise([1, "2"])
	fail("Expected TypeError")
} catch(e) {
	assertEquals(true, e instanceof TypeError)
}
