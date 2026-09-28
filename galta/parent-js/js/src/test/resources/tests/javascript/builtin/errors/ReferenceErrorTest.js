const e1 = new ReferenceError();
const e2 = new ReferenceError("My Message");
const e3 = new ReferenceError("Second Message", {cause:"unknown"});

assertTrue(e1 instanceof Error)
assertTrue(e1 instanceof ReferenceError)

assertEquals("ReferenceError",e1.toString())
assertEquals("ReferenceError: My Message",e2.toString())
assertEquals("ReferenceError: Second Message",e3.toString())

assertEquals("unknown", e3.cause)
assertEquals(undefined, e1.cause)


let ex = false;
try {
	throw new ReferenceError() 
} catch(e) {
	assertTrue(e instanceof Error)
	assertTrue(e instanceof ReferenceError)
	ex = true
}
assertTrue(ex)
