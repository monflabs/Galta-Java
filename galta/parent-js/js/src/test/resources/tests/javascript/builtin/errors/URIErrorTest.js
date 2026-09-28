const e1 = new URIError();
const e2 = new URIError("My Message");
const e3 = new URIError("Second Message", {cause:"unknown"});

assertTrue(e1 instanceof Error)
assertTrue(e1 instanceof URIError)

assertEquals("URIError",e1.toString())
assertEquals("URIError: My Message",e2.toString())
assertEquals("URIError: Second Message",e3.toString())

assertEquals("unknown", e3.cause)
assertEquals(undefined, e1.cause)


let ex = false;
try {
	throw new URIError() 
} catch(e) {
	assertTrue(e instanceof Error)
	assertTrue(e instanceof URIError)
	ex = true
}
assertTrue(ex)
