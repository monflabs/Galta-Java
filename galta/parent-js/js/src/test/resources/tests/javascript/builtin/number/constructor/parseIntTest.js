// Number.parseInt — same as global parseInt; parses integer from string

assertEquals( 42,  Number.parseInt('42') )
assertEquals( 42,  Number.parseInt('  42  ') )
assertEquals( 42,  Number.parseInt('42abc') )
assertEquals( -42, Number.parseInt('-42') )
assertEquals( 0,   Number.parseInt('0') )

// Radix
assertEquals( 255, Number.parseInt('ff', 16) )
assertEquals( 255, Number.parseInt('FF', 16) )
assertEquals( 7,   Number.parseInt('111', 2) )
assertEquals( 8,   Number.parseInt('10', 8) )
assertEquals( 10,  Number.parseInt('A', 16) )

// 0x prefix implies hex
assertEquals( 255, Number.parseInt('0xff') )
assertEquals( 255, Number.parseInt('0xFF') )

// Floats are truncated to integer part
assertEquals( 3, Number.parseInt('3.14') )
assertEquals( 3, Number.parseInt('3.99') )

// Non-parseable → NaN
assertEquals( true, isNaN(Number.parseInt('')) )
assertEquals( true, isNaN(Number.parseInt('abc')) )
assertEquals( true, isNaN(Number.parseInt(undefined)) )
assertEquals( true, isNaN(Number.parseInt(null)) )

// Radix 0 treated as 10
assertEquals( 42, Number.parseInt('42', 0) )
assertEquals( 42, Number.parseInt('42', 10) )

// Only leading valid chars
assertEquals( 10, Number.parseInt('10 20') )

// Radix argument uses real ToInt32 (truncate, then modulo 2^32 wraparound),
// not a clamp-to-int-range/infinite-value conversion.
assertEquals( 11, Number.parseInt('11', Infinity) )   // ToInt32(Infinity) === 0 -> default radix 10
assertEquals( 11, Number.parseInt('11', -Infinity) )  // ToInt32(-Infinity) === 0 -> default radix 10
assertEquals( 3,  Number.parseInt('11', 4294967298) ) // ToInt32 wraps 2^32+2 to 2
assertEquals( 11, Number.parseInt('11', NaN) )        // ToInt32(NaN) === 0 -> default radix 10
