assertThrows( () => eval("7_900_"));

var v1 = null
var v2 = 79
var v3 = 34.79
var v4 = true
var v5 = false
var v6 = "String1"
var v7 = 'String2'

var v8 = []
var v8a = [1]
var v8b = [1, 2, 3]

var v9 = {}
var v9a = { a: 'A' }
var v9b = { a: 'A', b: 'B', c: 'C' }

assertEquals(null,v1);
assertEquals(79,v2);
assertEquals(34.79,v3);
assertEquals(true,v4);
assertEquals(false,v5);
assertEquals("String1",v6);
assertEquals("String2",v7);

assertEquals(0,v8.length);

assertEquals(1,v8a.length);
assertEquals(1,v8a[0]);

assertEquals(3,v8b.length);
assertEquals(1,v8b[0]);
assertEquals(2,v8b[1]);
assertEquals(3,v8b[2]);

assertEquals(0,v9.$size());

assertEquals(1,v9a.$size());
assertEquals("A",v9a["a"]);

assertEquals(3,v9b.$size());
assertEquals("A",v9b["a"]);
assertEquals("B",v9b["b"]);
assertEquals("C",v9b["c"]);


//
// Integer, Base 2, 8 & 16
//

assertEquals(16, 0b10000)
assertEquals(16, 0B10000)
assertEquals(16, 0o20)
assertEquals(16, 0O20)
assertEquals(16, 0x10)
assertEquals(16, 0X10)

assertEquals(16, 0b10000i)
assertEquals(16, 0B10000i)
assertEquals(16, 0o20i)
assertEquals(16, 0O20i)
assertEquals(16, 0x10I)
assertEquals(16, 0X10I)

assertEquals(16, 0b10000l)
assertEquals(16, 0B10000l)
assertEquals(16, 0o20l)
assertEquals(16, 0O20l)
assertEquals(16, 0x10L)
assertEquals(16, 0X10L)

assertEquals(16, 0b10000n)
assertEquals(16, 0B10000n)
assertEquals(16, 0o20n)
assertEquals(16, 0O20n)
assertEquals(16, 0x10N)
assertEquals(16, 0X10N)
assertEquals(77, 077)
assertEquals(79, 079)


//
// Decimal numbers
//
assertEquals(0.0, 0.0)
assertEquals(0.0, -0.0)
assertSame(0.0, 0.0)
assertNotSame(0.0, -0.0)

assertEquals(3 / 2., 1.5)
assertEquals(100, 1.E2)
assertEquals(100, 1E2)
assertEquals(100, 1e2)
assertEquals(100, 1e+2)
assertEquals(0.01, 1e-2)

assertEquals(0.0f, 0.0f)
assertEquals(0.0f, -0.0f)
assertSame(0.0f, 0.0f)
assertNotSame(0.0f, -0.0f)
assertEquals(3f / 2.f, 1.5f)


//
// Numeric separators
// 
assertEquals( 7900, 7_900 );
assertEquals( 7900, 7_9_0_0 );
assertEquals( 79.00, 7_9.0_0 );
assertEquals( 0b111010, 0b11_1010 );
assertEquals( 0o345, 0o3_45 );
assertEquals( 0x1234, 0x12_34 );
// "e"/"E" are ordinary hex digits, not an exponent marker, so a separator
// next to them is valid in a hex literal (unlike in a decimal literal).
assertEquals( 0xee, 0xe_e );
assertEquals( 0xff, 0xf_f );
assertThrows( () => eval("7_900_"));
assertThrows( () => eval("7_.900"));
assertThrows( () => eval("76.900_"));
assertThrows( () => eval("7_e5"));
