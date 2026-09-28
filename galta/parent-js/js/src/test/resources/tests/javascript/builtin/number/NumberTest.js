// ctor
{
	let b = new Number()
	assertEquals(0,b)
	let b1 = new Number(12)
	assertEquals(12,b1)
	let b2 = new Number("24")
	assertEquals(24,b2)
	let b3 = new Number(true)
	assertEquals(1,b3)

	let o1 = new Number(1)
	let o2 = new Number(1)
	assertNotSame(o1,o2)
}

// function call
{
	let b = Number()
	assertEquals(0,b)
	let b1 = Number(12)
	assertEquals(12,b1)
	let b2 = Number("24")
	assertEquals(24,b2)
	let b3 = Number(true)
	assertEquals(1,b3)

	let o1 = Number(1)
	let o2 = Number(1)
	assertSame(o1,o2)
}

// Properties
{
	let bc = Number(1)
	assertThrows( () => bc.prop = 22 );

	let bo = new Number(1)
	bo.prop = 22
	assertEquals( 22, bo.prop )
}

// Check the prototype
assertEquals( Number.prototype, Object.getPrototypeOf(1))

// Number.isFinite/isNaN/isInteger/isSafeInteger must not unbox a Number
// object - they're strict-type checks, not ToNumber coercions.
{
	assertEquals(false, Number.isFinite(new Number(42)))
	assertEquals(true, Number.isFinite(42))
	assertEquals(false, Number.isNaN(new Number(NaN)))
	assertEquals(true, Number.isNaN(NaN))
	assertEquals(false, Number.isInteger(new Number(42)))
	assertEquals(true, Number.isInteger(42))
	assertEquals(false, Number.isSafeInteger(new Number(42)))
	assertEquals(true, Number.isSafeInteger(42))
}

// %Number.prototype% itself has internal [[NumberData]] +0, so its own
// methods can be called directly on it (not just on Number instances).
{
	assertEquals("0", Number.prototype.toString())
	assertEquals(0, Number.prototype.valueOf())
	assertEquals("0e+0", Number.prototype.toExponential(0))
	assertEquals("0", Number.prototype.toFixed())
	assertEquals("0", Number.prototype.toPrecision(1))
}

// Number(str) non-decimal integer literals must not accept a sign.
{
	assertEquals(0, Number("0b0"))
	assertEquals(5, Number("0b101"))
	assertEquals(true, isNaN(Number("+0x10")))
	assertEquals(true, isNaN(Number("-0b1")))
}
