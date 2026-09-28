// obj is an object published from a sample library
// we access its fields here

assertNotNull(obj)
try {
	assertNull(obj2)
	fail();
} catch(e){}	

var o = obj
assertNotNull(o)

var v=obj.charVar
assertEquals('A',v)
var v=obj['charVar']
assertEquals('A',v)

var v=obj.byteVar
assertEquals(3,v)
var v=obj['byteVar']
assertEquals(3,v)

var v=obj.shortVar
assertEquals(156,v)
var v=obj['shortVar']
assertEquals(156,v)

var v=obj.intVar
assertEquals(-56,v)
var v=obj['intVar']
assertEquals(-56,v)

var v=obj.longVar
assertEquals(4789,v)
assertEquals(4789L,v)
var v=obj['longVar']
assertEquals(4789,v)

var v=obj.floatVar
assertEquals(10.56f,v)
var v=obj['floatVar']
assertEquals(10.56f,v)

var v=obj.doubleVar
assertEquals(56.89,v)
var v=obj['doubleVar']
assertEquals(56.89,v)

var v=obj.bigDecimalVar
assertEquals(10m,v)
var v=obj['bigDecimalVar']
assertEquals(10m,v)

var v=obj.booleanVar
assertEquals(true,v)
var v=obj['booleanVar']
assertEquals(true,v)

var v=obj.stringVar
assertEquals('AAA',v)
var v=obj['stringVar']
assertEquals('AAA',v)

// Bean
var v=obj.intProp
assertEquals(-56,v)


// Null ops

// . ?.
const nullobj = null
try {
	var v=nullobj.fake
	fail();
} catch(e) {}

var v=nullobj?.fake
assertEquals(null,v)

var v=nullobj?.fake.fake2
assertEquals(null,v)

var v=nullobj?.fake?.fake2
assertEquals(null,v)

// [ ?.[
try {
	var v=nullobj['fake']
	fail();
} catch(e) {}
assertEquals(null,v)

var v=nullobj?.['fake']
assertEquals(null,v)

var v=nullobj?.['fake']['fake2']
assertEquals(null,v)

var v=nullobj?.['fake']?.['fake2']
assertEquals(null,v)
