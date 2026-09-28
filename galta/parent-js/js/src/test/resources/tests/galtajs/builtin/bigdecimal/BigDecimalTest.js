const bigdec = 123456.789m;
assertEquals( 123456.789, bigdec )

const b2 = Decimal("123456789123456789.25689");
assertEquals( 123456789123456789.25689m, b2 )

assertThrows( () => new Decimal("1") );

assertEquals( "decimal", typeof 1m )
