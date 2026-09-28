//
// Object ctor
//
const TestClass = Java.type("tests.classes.TestClass")

var o = new TestClass()
assertNotNull(o)
assertEquals("tests.classes.TestClass",o.getClass().getName())

var o = new (TestClass)()
assertNotNull(o)
assertEquals("tests.classes.TestClass",o.getClass().getName())

var o = new (TestClass)(1,2,1)
assertNotNull(o)
assertEquals("tests.classes.TestClass",o.getClass().getName())
assertEquals(4,o.intVar)

var o = new TestClass
assertNotNull(o)
assertEquals("tests.classes.TestClass",o.getClass().getName())

var o = new TestClass(1,2,3)
assertNotNull(o)
assertEquals("tests.classes.TestClass",o.getClass().getName())
assertEquals(6,o.intVar)

function f() {
	this.clazz = TestClass
	
	var o = new this.clazz()
	assertNotNull(o)
	assertEquals("tests.classes.TestClass",o.getClass().getName())

	var o = new this.clazz(1,2,3)
	assertNotNull(o)
	assertEquals("tests.classes.TestClass",o.getClass().getName())
	assertEquals(6,o.intVar)
}
new f()

const b1 = new Boolean(true)
assertEquals( true, b1 )
assertNotEqualsStrict( true, b1 )

const b2 = new Boolean
assertEquals( false, b2 )
assertNotEqualsStrict( false, b1 )

const b3 = new Boolean(false)
assertEquals( false, b3 )
assertNotEqualsStrict( false, b1 )


//
// Unavailable ctor
//
var incatch=false
try {
	var o = new TestClass(1,2)
	fail();
} catch(e) {
	// Desired exception: invalid ctor
	incatch=true
}	
assertEquals(true,incatch)


//
// Object array
//
var testArray = new TestClass[7];
assertEquals(7,testArray.length)
testArray[1] = new TestClass()
assertEquals("tests.classes.TestClass",testArray[1].getClass().getName())


//
// ... spred
//
function addition(a,b,c) {
	this.sum = a+b+c;
}
const v123 = [1,2,3]
assertEquals( 6, (new addition(1,2,3)).sum );
assertEquals( 6, (new addition(...v123)).sum );



//
// Java primitive types
//
const char = Java.type("char")
const byte = Java.type("byte")
const short = Java.type("short")
const int = Java.type("int")
const long = Java.type("long")
const float = Java.type("float")
const double = Java.type("double")
const boolean = Java.type("boolean")

var a1 = new char[2];
var a2 = new byte[2];
var a3 = new short[2];
var a4 = new int[2];
var a5 = new long[2];
var a6 = new float[2];
var a7 = new double[2];
var a8 = new boolean[2];

var intArray = new int[4];
assertEquals(4,intArray.length)
intArray[1] = 56
intArray[3] = 45
assertEquals(56,intArray[1])
assertEquals(0,intArray[2])
assertEquals(45,intArray[3])

var m1 = new int[][2];
m1[0] = new int[5]
m1[0][3] = 79
assertEquals(79,m1[0][3])

