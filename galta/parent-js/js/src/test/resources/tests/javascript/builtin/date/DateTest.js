const d = new Date(2020,8,27)

assertEquals( 2020, d.getFullYear() )
assertEquals( 8, d.getMonth() )
assertEquals( 27, d.getDate() )

const n2 = new Date()
const now = Date.now()
assertTrue(Math.abs(n2-now)<1000) // 1 sec...

// [[DateValue]] must be read BEFORE argument ToNumber conversion - a
// poisoned valueOf() mutating the date mid-call must not affect the
// already-captured value.
{
	const dt = new Date(0)
	let valueOfCalled = 0
	const value = { valueOf() { valueOfCalled++; dt.setTime(NaN); return 1 } }
	const result = dt.setFullYear(value)
	assertEquals( 1, valueOfCalled )
	assertTrue( !Number.isNaN(result) )
	assertEquals( result, dt.getTime() )
}

// setDate (and the other non-FullYear setters): if the ORIGINAL [[DateValue]]
// was NaN, the setter returns NaN WITHOUT touching the object - a side
// effect from the argument's valueOf() during conversion must still persist.
{
	const dt = new Date(NaN)
	const value = { valueOf() { dt.setTime(0); return 1 } }
	const result = dt.setDate(value)
	assertTrue( Number.isNaN(result) )
	assertEquals( 0, dt.getTime() )
}

// setFullYear/setUTCFullYear "revive" an invalid Date by treating the
// missing [[DateValue]] as 1970-01-01T00:00:00.000 in LOCAL wall-clock
// terms (not as UTC epoch 0 converted to local time).
{
	const dt = new Date(NaN)
	const result = dt.setFullYear(2016)
	assertEquals( new Date(2016, 0).getTime(), result )
	assertEquals( new Date(2016, 0).getTime(), dt.getTime() )
}

// Date.prototype[Symbol.toPrimitive]: works on any object (not just a real
// Date), validates the hint strictly, and dispatches string-vs-number
// try-order without ever touching DateUtil's own formatting for a
// non-Date receiver.
{
	assertThrows( TypeError, () => Date.prototype[Symbol.toPrimitive].call(42, 'number') )
	assertThrows( TypeError, () => Date.prototype[Symbol.toPrimitive].call(new Date(), 'bogus') )
	assertThrows( TypeError, () => Date.prototype[Symbol.toPrimitive].call(new Date()) )

	let tsCalls = 0, voCalls = 0
	const obj = {
		toString() { tsCalls++; return 'str' },
		valueOf() { voCalls++; return 1 },
	}
	assertEquals( 'str', Date.prototype[Symbol.toPrimitive].call(obj, 'default') )
	assertEquals( 1, tsCalls )
	assertEquals( 0, voCalls )
}

// Date.prototype.toJSON is generic: works on any object via ToObject +
// ToPrimitive(Number) + Invoke(O, "toISOString") - not tied to this
// receiver actually being a Date.
{
	assertEquals( new Date(0).toISOString(), new Date(0).toJSON() )
	assertEquals( null, Date.prototype.toJSON.call({valueOf: () => NaN, toISOString: () => { throw new Error() }}) )
	assertEquals( 'custom', Date.prototype.toJSON.call({toISOString: () => 'custom'}) )
	assertThrows( TypeError, () => Date.prototype.toJSON.call(undefined) )
}

// %Date.prototype% itself has no [[DateValue]] slot - calling a Date.prototype
// method directly on it (this = Date.prototype) must throw, not silently
// return a placeholder value.
assertThrows( TypeError, () => Date.prototype.toString() )
assertThrows( TypeError, () => Date.prototype.valueOf() )

// Date.UTC: real MakeDay/MakeTime/MakeDate/TimeClip arithmetic, not
// java.util.Calendar's int-based overflow handling - covers the 0<=y<=99
// INCLUSIVE year-offset boundary and month/hour overflow carrying.
assertTrue( Number.isNaN(Date.UTC()) )
assertEquals( 0, Date.UTC(1970, 0) )
assertEquals( Date.UTC(1999, 0), Date.UTC(99, 0) )
assertEquals( Date.UTC(2016, 13), Date.UTC(2017, 1) )
assertEquals( Date.UTC(2016, 6, 5, 24), Date.UTC(2016, 6, 6) )
