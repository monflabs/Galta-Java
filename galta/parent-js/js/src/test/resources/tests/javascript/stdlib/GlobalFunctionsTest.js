{ 
// isNaN
	assertEquals( true, isNaN() )
	assertEquals( false, isNaN(null) )
	assertEquals( true, isNaN(undefined) )

	assertEquals( false, isNaN(0) )
	assertEquals( false, isNaN(1) )
	assertEquals( false, isNaN(Infinity) )
	assertEquals( false, isNaN(true) )
	assertEquals( false, isNaN(false) )
	assertEquals( false, isNaN('') )

	assertEquals( true, isNaN('true') )
	assertEquals( true, isNaN(NaN) )
}

{ 
// isFinite
	assertEquals( false, isFinite() )
	assertEquals( true, isFinite(null) )
	assertEquals( false, isFinite(undefined) )

	assertEquals( true, isFinite(0) )
	assertEquals( true, isFinite(1) )
	assertEquals( true, isFinite(true) )
	assertEquals( true, isFinite(false) )
	assertEquals( true, isFinite('') )

	assertEquals( false, isFinite('true') )
	assertEquals( false, isFinite(NaN) )
}

{ 
// parseInt
	assertEquals( 1, parseInt("1") )
	assertEquals( 11, parseInt(" 11") )
	assertEquals( 11, parseInt(" 11a") )
	assertEquals( -2, parseInt(" -2a") )
	assertEquals( -32, parseInt(" -32a") )
	assertEquals( 255, parseInt("0xFF") )
	assertEquals( NaN, parseInt("0xk9") )

	assertEquals( 0, parseInt("0o10",4) )
	assertEquals( 8, parseInt("0o10",8) )
	
	assertEquals( 0, parseInt("0x10",4) )
	assertEquals( 16, parseInt("0x10",16) )

	assertEquals( 16, parseInt("10",16) )
	assertEquals( 33, parseInt("xz",35) )
	
}

{ 
// parseFloat
	assertEquals( 1.1, parseFloat("1.1") )
	assertEquals( 11.22, parseFloat(" 11.22") )
	assertEquals( 11.22, parseFloat(" 11.22a") )
	assertEquals( -11.22, parseFloat(" -11.22a") )
	assertEquals( 11000, parseFloat("1.1e4") )
	assertEquals( 1.1, parseFloat("1.1e") )
}

{ 
// encodeURI
	assertEquals( "undefined", encodeURI() )
	assertEquals( "", encodeURI("") )
	assertEquals( ";/?:@&=+$,#", encodeURI(";/?:@&=+$,#") )
	assertEquals( "-.!~*'()", encodeURI("-.!~*'()") )
	assertEquals( "ABC%20abc%20123", encodeURI("ABC abc 123") )
}
{ 
// encodeURIComponent
	assertEquals( "undefined", encodeURIComponent() )
	assertEquals( "", encodeURIComponent("") )
	assertEquals( "%3B%2F%3F%3A%40%26%3D%2B%24%2C%23", encodeURIComponent(";/?:@&=+$,#") )
	assertEquals( "-.!~*'()", encodeURIComponent("-.!~*'()") )
	assertEquals( "ABC%20abc%20123", encodeURIComponent("ABC abc 123") )
}
{ 
// decodeURI
	assertEquals( "undefined", decodeURI() )
	assertEquals( "", decodeURI("") )
	assertEquals( ";/?:@&=+$,#", decodeURI(";/?:@&=+$,#") )
	assertEquals( "-.!~*'()", decodeURI("-.!~*'()") )
	assertEquals( "ABC abc 123", decodeURI("ABC%20abc%20123") )
}
{ 
// decodeURIComponent
	assertEquals( "undefined", decodeURIComponent() )
	assertEquals( "", decodeURIComponent("") )
	assertEquals( ";/?:@&=+$,#", decodeURIComponent("%3B%2F%3F%3A%40%26%3D%2B%24%2C%23") )
	assertEquals( "-.!~*'()", decodeURIComponent("-.!~*'()") )
	assertEquals( "ABC abc 123", decodeURIComponent("ABC%20abc%20123") )
}

{
// escape
	assertEquals( "undefined", escape() );
	assertEquals( "", escape("") );
	assertEquals( "abc123", escape("abc123") );
	assertEquals( "%E4%F6%FC", escape("äöü") );
	assertEquals( "%u0107", escape("ć") );
	assertEquals( "@*_+-./", escape("@*_+-./") );
}

{
// unescape
	assertEquals( "undefined", unescape() );
	assertEquals( "", unescape("") );
	assertEquals( "abc123", unescape("abc123") );
	assertEquals( "äöü", unescape("%E4%F6%FC") );
	assertEquals( "ć", unescape("%u0107") );
	assertEquals( "@*_+-./", unescape("@*_+-./") );
}