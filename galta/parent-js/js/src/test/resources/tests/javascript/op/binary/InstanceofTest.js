const char = Java.type("char")
const byte = Java.type("byte")
const short = Java.type("short")
const int = Java.type("int")
const long = Java.type("long")
const float = Java.type("float")
const double = Java.type("double")
const boolean = Java.type("boolean")

const Character = Java.type("java.lang.Character")
const Byte = Java.type("java.lang.Byte")
const Short = Java.type("java.lang.Short")
const Integer = Java.type("java.lang.Integer")
const Long = Java.type("java.lang.Long")
const Float = Java.type("java.lang.Float")
const Double = Java.type("java.lang.Double")
const BigInteger = Java.type("java.math.BigInteger")
const BigDecimal = Java.type("java.math.BigDecimal")
const javaString = Java.type("java.lang.String")

assertTrue(1 instanceof int)
assertTrue(1.0 instanceof int)
assertTrue(1e2 instanceof int)
assertTrue(1L instanceof long)
assertTrue(1.0f instanceof float)
assertTrue(1.0d instanceof double)
assertTrue(1n instanceof BigInteger)
assertTrue(1.0m instanceof BigDecimal)
assertTrue(true instanceof boolean)
assertTrue('str' instanceof javaString)

// Converts to primitive types
assertTrue(typeof Byte(1)==="number")
assertTrue(Byte(1) instanceof byte)
assertTrue(Short(1) instanceof short)
assertTrue(Integer(1) instanceof int)
assertTrue(Long(1) instanceof long)
assertTrue(Float(1) instanceof float)
assertTrue(Double(1) instanceof double)
assertTrue(BigInteger(1) instanceof BigInteger)
assertTrue(BigDecimal(1) instanceof BigDecimal)

assertTrue(new Character('A') instanceof char)
assertTrue(new String('abc') instanceof String)

// This creates Object instances, not primitive types
assertTrue(typeof new Byte(1)!=="number")
assertTrue(new Byte(0) instanceof Object)
assertTrue(new Byte(0) instanceof byte)
assertTrue(new Short(0) instanceof short)
assertTrue(new Integer(0) instanceof int)
assertTrue(new Long(0) instanceof long)
assertTrue(new Float(0f) instanceof float)
assertTrue(new Double(0d) instanceof double)

assertTrue(new Byte(0) instanceof Byte)
assertTrue(new Short(0) instanceof Short)
assertTrue(new Integer(0) instanceof Integer)
assertTrue(new Long(0) instanceof Long)
assertTrue(new Float(0f) instanceof Float)
assertTrue(new Double(0d) instanceof Double)
assertTrue(new BigInteger("0",10) instanceof BigInteger)
assertTrue(new BigDecimal(0.0) instanceof BigDecimal)


function f1() {	
}
assertEquals( f1.prototype, Object.getPrototypeOf(new f1()) )
assertTrue  ( new f1() instanceof f1 )
assertEquals( undefined, (new f1()).a )


function f2() {	
}
f2.prototype = {a: 2}
assertEquals( f2.prototype, Object.getPrototypeOf(new f2()) )
assertTrue  ( new f2() instanceof f2 )
assertEquals( 2, (new f2()).a )


function f3() {	
}
f3.prototype = {a: 3}
Object.setPrototypeOf(f3.prototype,f2.prototype)
assertTrue  ( new f3() instanceof f2 )
assertTrue  ( new f3() instanceof f3 )
assertEquals( 3, (new f3()).a )


// Symbol.hasInstance: a custom handler must be invoked regardless of whether
// the left-hand value is a primitive.
// Function.prototype[Symbol.hasInstance] is {writable:false,enumerable:false,
// configurable:false} (test262: built-ins/Function/prototype/Symbol.hasInstance/
// prop-desc.js) - a plain `F[Symbol.hasInstance] = ...` assignment on an
// ordinary function must go through the inherited non-writable data property
// and be REJECTED (OrdinarySet); only [[DefineOwnProperty]]
// (Object.defineProperty, or class static method syntax) can install an own
// override, exactly like any other inherited non-writable builtin method.
function F() {}
let callCount = 0;
let thisValue, argValue;
Object.defineProperty(F, Symbol.hasInstance, { value: function(v) {
	callCount++;
	thisValue = this;
	argValue = v;
	return true;
}, configurable: true });
assertTrue(0 instanceof F);
assertEquals(1, callCount);
assertEquals(F, thisValue);
assertEquals(0, argValue);

Object.defineProperty(F, Symbol.hasInstance, { value: function() { return true; }, configurable: true });
assertTrue(0 instanceof F);
Object.defineProperty(F, Symbol.hasInstance, { value: function() { return NaN; }, configurable: true });
assertFalse(0 instanceof F);

// Defined-but-non-callable @@hasInstance is a TypeError (GetMethod semantics);
// null/undefined means "no handler" and falls back to OrdinaryHasInstance.
Object.defineProperty(F, Symbol.hasInstance, { value: 42, configurable: true });
assertThrows(TypeError, () => 0 instanceof F);

const plainObj = {};
plainObj[Symbol.hasInstance] = null;
assertThrows(TypeError, () => 0 instanceof plainObj);

// OrdinaryHasInstance: a non-object left-hand value is simply not an instance,
// but a non-object .prototype on the constructor is a TypeError.
function G() {}
assertFalse(0 instanceof G);
G.prototype = undefined;
assertThrows(TypeError, () => ({}) instanceof G);

// A dynamically-created function's own .prototype must be writable, same as
// an ordinary function declaration's.
const DynFn = new Function();
DynFn.prototype = { marker: 1 };
assertEquals(1, DynFn.prototype.marker);
