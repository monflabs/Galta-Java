const bigint = 123456n;
assertEquals( 123456, bigint )

const b2 = BigInt("123456789123456789");
assertEquals( 123456789123456789n, b2 )

assertThrows( () => new BigInt("1") );


assertEquals( "bigint", typeof 1n)

// Postfix inc/dec must use ToNumeric (which passes BigInt through), not
// ToNumber (which throws on BigInt), for the returned "original value".
let x = 1n;
assertEquals( 1n, x-- );
assertEquals( 0n, x );
let y = 1n;
assertEquals( 1n, y++ );
assertEquals( 2n, y );

// BigInt <-> Number equality must compare mathematical values exactly, not
// go through a lossy double conversion.
assertEquals( true, 0n == -0 );
assertEquals( false, 0n == 0.5 );
assertEquals( true, 9007199254740991n == 9007199254740991 );

// BigInt <-> String equality uses StringToBigInt (exact), not StringToNumber
// (which would lose precision for large integers).
assertEquals( true, 900719925474099101n == '900719925474099101' );
assertEquals( false, 900719925474099102n == '900719925474099101' );
assertEquals( true, 0n == '' );
assertEquals( false, 0n == 'foo' );

// BigInt <-> Object equality recurses through ToPrimitive.
assertEquals( true, 900719925474099101n == { valueOf() { return 900719925474099101n; } } );

// StringToBigInt (used for relational comparisons too) supports the
// non-decimal integer literal forms, not just plain decimal digits.
assertEquals( true, '0x10' < 17n );
assertEquals( false, '0x10' < 16n );
assertEquals( true, '0o10' < 9n );
assertEquals( true, '0b10' < 3n );

// BigInt(number): must be an integral, finite value.
assertEquals( 5n, BigInt(5) );
assertThrows( RangeError, () => BigInt(5.5) );
assertThrows( RangeError, () => BigInt(NaN) );
assertThrows( RangeError, () => BigInt(Infinity) );

// BigInt(string): trims whitespace, allows an empty string (0n), and
// understands the 0x/0o/0b prefixes - not just plain decimal digits.
assertEquals( 0n, BigInt("") );
assertEquals( 0n, BigInt("   ") );
assertEquals( 255n, BigInt("0xFF") );
assertEquals( 8n, BigInt("0o10") );
assertEquals( 5n, BigInt("  5  ") );
assertThrows( SyntaxError, () => BigInt("not a number") );

// BigInt(object): calls ToPrimitive once, then dispatches on the result.
assertEquals( 42n, BigInt({ valueOf: () => "42" }) );

// Number(bigint): explicitly allowed (unlike arithmetic ToNumber, which
// throws for a BigInt operand).
assertEquals( 5, Number(5n) );

// BigInt.asIntN/asUintN: the "bits" argument uses ToIndex (range [0, 2**53-1],
// RangeError otherwise; TypeError for a BigInt/Symbol bits argument), and the
// "bigint" argument uses ToBigInt (TypeError for a plain Number, unlike the
// BigInt() constructor).
assertThrows( RangeError, () => BigInt.asIntN(-1, 0n) );
assertThrows( TypeError, () => BigInt.asIntN(0n, 0n) );
assertThrows( TypeError, () => BigInt.asIntN(0, 0) );
 