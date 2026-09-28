const e1 = new RangeError();
const e2 = new RangeError("My Message");
const e3 = new RangeError("Second Message", {cause:"unknown"});

assertTrue(e1 instanceof Error)
assertTrue(e1 instanceof RangeError)

assertEquals("RangeError",e1.toString())
assertEquals("RangeError: My Message",e2.toString())
assertEquals("RangeError: Second Message",e3.toString())

assertEquals("unknown", e3.cause)
assertEquals(undefined, e1.cause)


let ex = false;
try {
	throw new RangeError() 
} catch(e) {
	assertTrue(e instanceof Error)
	assertTrue(e instanceof RangeError)
	ex = true
}
assertTrue(ex)
