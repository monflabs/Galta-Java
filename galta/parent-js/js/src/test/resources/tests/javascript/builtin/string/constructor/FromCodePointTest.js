assertEquals( "*", String.fromCodePoint(42) )
assertEquals( "AZ", String.fromCodePoint(65, 90) )
assertEquals( "\u0404", String.fromCodePoint(0x404) )
assertEquals( "\uD87E\uDC04", String.fromCodePoint(0x2F804) )
assertEquals( "\uD87E\uDC04", String.fromCodePoint(194564) )
assertEquals( "\uD834\uDF06a\uD834\uDF07", String.fromCodePoint(0x1D306, 0x61, 0x1D307) )

assertThrows( () => String.fromCodePoint('_') ); 
assertThrows( () => String.fromCodePoint(Infinity) ); 
assertThrows( () => String.fromCodePoint(-1) ); 
assertThrows( () => String.fromCodePoint(3.14) ); 
assertThrows( () => String.fromCodePoint(3e-2) ); 
assertThrows( () => String.fromCodePoint(NaN) ); 

assertEquals( String.fromCodePoint(0x1F303), String.fromCharCode(0xD83C, 0xDF03) )
assertEquals( "\uD83C\uDF03", String.fromCharCode(55356, 57091) )

// ToNumber must actually be called on non-Number arguments (a numeric string,
// or an object with valueOf()) rather than short-circuiting to a RangeError.
assertEquals( "*", String.fromCodePoint("42") )
assertEquals( "*", String.fromCodePoint({ valueOf: () => 42 }) )

// A poisoned valueOf() must propagate its own exception, not a RangeError.
assertThrows( () => String.fromCodePoint({ valueOf: () => { throw new Error("boom"); } }) )
