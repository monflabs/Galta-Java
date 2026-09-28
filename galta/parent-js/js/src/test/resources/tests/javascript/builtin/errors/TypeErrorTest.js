const e1 = new TypeError();
const e2 = new TypeError("My Message");
const e3 = new TypeError("Second Message", {cause:"unknown"});

assertTrue(e1 instanceof Error)
assertTrue(e1 instanceof TypeError)

assertEquals("TypeError",e1.toString())
assertEquals("TypeError: My Message",e2.toString())
assertEquals("TypeError: Second Message",e3.toString())

// cause property (InstallErrorCause)
assertEquals("unknown", e3.cause)
assertEquals(undefined, e1.cause)

let ex = false;
try {
	throw new TypeError() 
} catch(e) {
	assertTrue(e instanceof Error)
	assertTrue(e instanceof TypeError)
	ex = true
}
assertTrue(ex)
