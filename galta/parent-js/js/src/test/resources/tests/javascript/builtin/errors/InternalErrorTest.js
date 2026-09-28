const e1 = new InternalError();
const e2 = new InternalError("My Message");
const e3 = new InternalError("Second Message", {cause:"unknown"});

assertTrue(e1 instanceof Error)
assertTrue(e1 instanceof InternalError)

assertEquals("InternalError",e1.toString())
assertEquals("InternalError: My Message",e2.toString())
assertEquals("InternalError: Second Message",e3.toString())

assertEquals("unknown", e3.cause)
assertEquals(undefined, e1.cause)


let ex = false;
try {
	throw new InternalError() 
} catch(e) {
	assertTrue(e instanceof Error)
	assertTrue(e instanceof InternalError)
	ex = true
}
assertTrue(ex)
