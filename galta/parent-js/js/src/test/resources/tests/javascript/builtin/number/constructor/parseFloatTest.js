// Number.parseFloat — same as global parseFloat; no coercion of non-string types beyond toString

assertEquals( 3.14, Number.parseFloat('3.14') )
assertEquals( 3.14, Number.parseFloat('  3.14  ') )
assertEquals( 3.14, Number.parseFloat('3.14more text') )
assertEquals( 0,    Number.parseFloat('0') )
assertEquals( -1.5, Number.parseFloat('-1.5') )
assertEquals( 1.5,  Number.parseFloat('+1.5') )

// Infinity
assertEquals( Infinity,  Number.parseFloat('Infinity') )
assertEquals( -Infinity, Number.parseFloat('-Infinity') )

// Scientific notation
assertEquals( 314,    Number.parseFloat('3.14e2') )
assertEquals( 0.0314, Number.parseFloat('3.14e-2') )

// Non-parseable → NaN
assertEquals( true, isNaN(Number.parseFloat('')) )
assertEquals( true, isNaN(Number.parseFloat('abc')) )
assertEquals( true, isNaN(Number.parseFloat(undefined)) )
assertEquals( true, isNaN(Number.parseFloat(null)) )

// Numbers pass through unchanged
assertEquals( 42, Number.parseFloat(42) )
assertEquals( 1.5, Number.parseFloat(1.5) )
