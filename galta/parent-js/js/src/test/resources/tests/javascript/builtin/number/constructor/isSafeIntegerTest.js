// Number.isSafeInteger — true only for integers within -(2^53-1) to 2^53-1

assertEquals( false, Number.isSafeInteger() )
assertEquals( false, Number.isSafeInteger(null) )
assertEquals( false, Number.isSafeInteger(undefined) )

assertEquals( false, Number.isSafeInteger(NaN) )
assertEquals( false, Number.isSafeInteger(Infinity) )
assertEquals( false, Number.isSafeInteger(-Infinity) )

assertEquals( false, Number.isSafeInteger('') )
assertEquals( false, Number.isSafeInteger('1') )
assertEquals( false, Number.isSafeInteger(true) )
assertEquals( false, Number.isSafeInteger(false) )

assertEquals( true, Number.isSafeInteger(0) )
assertEquals( true, Number.isSafeInteger(1) )
assertEquals( true, Number.isSafeInteger(-1) )
assertEquals( true, Number.isSafeInteger(42) )
assertEquals( true, Number.isSafeInteger(Number.MAX_SAFE_INTEGER) )
assertEquals( true, Number.isSafeInteger(Number.MIN_SAFE_INTEGER) )

// Floats — never safe integers
assertEquals( false, Number.isSafeInteger(1.5) )
assertEquals( false, Number.isSafeInteger(-1.5) )

// Beyond safe range
assertEquals( false, Number.isSafeInteger(Number.MAX_SAFE_INTEGER + 1) )
assertEquals( false, Number.isSafeInteger(Number.MIN_SAFE_INTEGER - 1) )
assertEquals( false, Number.isSafeInteger(9007199254740992) )   // 2^53
assertEquals( false, Number.isSafeInteger(-9007199254740992) )  // -(2^53)
