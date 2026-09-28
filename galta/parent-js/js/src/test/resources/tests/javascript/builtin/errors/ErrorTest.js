const e1 = new Error();
const e2 = new Error("My Message");
const e3 = new Error("Second Message", {cause:"unknown"});

assertTrue(e1 instanceof Error)
assertTrue(e2 instanceof Error)
assertTrue(e3 instanceof Error)

assertEquals("Error",e1.toString())
assertEquals("Error: My Message",e2.toString())
assertEquals("Error: Second Message",e3.toString())

// cause property
assertEquals("unknown", e3.cause)
assertEquals(undefined, e1.cause)

// name and message properties
assertEquals("Error", e1.name)
assertEquals("", e1.message)
assertEquals("My Message", e2.message)

// Error is constructable and callable
let ex = false;
try {
	throw new Error()
} catch(e) {
	assertTrue(e instanceof Error)
	ex = true
}
assertTrue(ex)

// Stack property exists
assertTrue(typeof e2.stack === 'string' || e2.stack === undefined)

// Subclassing (instanceof chain)
assertTrue(new TypeError() instanceof Error)
assertTrue(new RangeError() instanceof Error)
assertTrue(new SyntaxError() instanceof Error)

// TypeError has name "TypeError"
assertEquals("TypeError", new TypeError("t").name)
assertEquals("RangeError", new RangeError("r").name)
assertEquals("SyntaxError", new SyntaxError("s").name)
assertEquals("ReferenceError", new ReferenceError("re").name)

// Error.isError: a brand check, not an instanceof/prototype-chain check
assertTrue(Error.isError(new Error()))
assertTrue(Error.isError(new TypeError()))
class MyError extends Error {}
assertTrue(Error.isError(new MyError()))
assertFalse(Error.isError({name:"Error", message:""}))
assertFalse(Error.isError(null))
assertFalse(Error.isError(42))
assertFalse(Error.isError(Error))

// Error.prototype.toString works on any object, not just genuine Errors
assertEquals("My: Msg", Error.prototype.toString.call({name:"My", message:"Msg"}))
assertEquals("Error", Error.prototype.toString.call({}))
assertEquals("Msg", Error.prototype.toString.call({name:"", message:"Msg"}))

// A NativeError constructor's own [[Prototype]] is Error (constructor-level
// inheritance), matching how NativeError.prototype's own chain reaches
// Error.prototype - not Function.prototype.
assertEquals(Error, Object.getPrototypeOf(TypeError))
assertEquals(Error, Object.getPrototypeOf(RangeError))
assertEquals(Error, Object.getPrototypeOf(ReferenceError))
assertEquals(Error, Object.getPrototypeOf(SyntaxError))
assertEquals(Error, Object.getPrototypeOf(URIError))
assertEquals(Error, Object.getPrototypeOf(EvalError))
assertEquals(Error, Object.getPrototypeOf(AggregateError))

// Every NativeError.prototype has its own "message" own property ("").
assertEquals("", TypeError.prototype.message)
assertEquals("", RangeError.prototype.message)
assertTrue(Object.prototype.hasOwnProperty.call(TypeError.prototype, "message"))
