const e1 = new SyntaxError();
const e2 = new SyntaxError("My Message");
const e3 = new SyntaxError("Second Message", {cause:"unknown"});

assertTrue(e1 instanceof Error)
assertTrue(e1 instanceof SyntaxError)

assertEquals("SyntaxError",e1.toString())
assertEquals("SyntaxError: My Message",e2.toString())
assertEquals("SyntaxError: Second Message",e3.toString())

assertEquals("unknown", e3.cause)
assertEquals(undefined, e1.cause)


let ex = false;
try {
	throw new SyntaxError() 
} catch(e) {
	assertTrue(e instanceof Error)
	assertTrue(e instanceof SyntaxError)
	ex = true
}
assertTrue(ex)
