// Number.isInteger — only true for finite integer values; no coercion

assertEquals( false, Number.isInteger() )
assertEquals( false, Number.isInteger(null) )
assertEquals( false, Number.isInteger(undefined) )

assertEquals( false, Number.isInteger(NaN) )
assertEquals( false, Number.isInteger(Infinity) )
assertEquals( false, Number.isInteger(-Infinity) )

assertEquals( false, Number.isInteger('') )
assertEquals( false, Number.isInteger('1') )
assertEquals( false, Number.isInteger(true) )
assertEquals( false, Number.isInteger(false) )
assertEquals( false, Number.isInteger([]) )
assertEquals( false, Number.isInteger({}) )

assertEquals( true, Number.isInteger(0) )
assertEquals( true, Number.isInteger(1) )
assertEquals( true, Number.isInteger(-1) )
assertEquals( true, Number.isInteger(1000000) )
assertEquals( true, Number.isInteger(Number.MAX_SAFE_INTEGER) )
assertEquals( true, Number.isInteger(Number.MIN_SAFE_INTEGER) )

assertEquals( false, Number.isInteger(1.5) )
assertEquals( false, Number.isInteger(1.1) )
assertEquals( false, Number.isInteger(0.1) )
// 1.0000000000000001 cannot be represented exactly in IEEE 754 double and equals 1.0 → is integer
assertEquals( true,  Number.isInteger(1.0000000000000001) )
assertEquals( true,  Number.isInteger(1.0) )
assertEquals( true,  Number.isInteger(-0) )
