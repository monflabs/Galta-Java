// Just to make sure that the method works, we are not testing the result
// Eval does not work with the transpiler

assertEquals( null, eval() );
assertEquals( null, eval("") );

assertEquals( 3, eval("1+2") );
assertEquals( "abc78", eval("'abc'+78") );

assertThrows( () => eval("1cvf") );
var TT = 13;
assertEquals( 13, eval("TT") );

TT = 45
assertEquals( 66, eval("let TT=66; TT") );
assertEquals( 45, TT );

assertEquals( 66, eval("const TT=66; TT") );
assertEquals( 45, TT );


const T2 = 14;
assertEquals( 14, eval("T2") );

let T3 = 15;
assertEquals( 15, eval("T3") );

assertEquals( 45, eval("var TT=45; TT") );
assertEquals( 45, TT );

// A direct eval called from genuinely sloppy code creates its own new `var`
// declarations in the CALLER's scope (spec EvalDeclarationInstantiation) -
// confirmed against test262 (annexB/language/eval-code/*-init.js).
assertEquals( 46, eval("var TT2=46; TT2") );
assertEquals( "number", typeof TT2 );
assertEquals( 46, TT2 );


//
// Update the global variables

let U1 = 15;
assertEquals( 56, eval("U1 = 56") );
assertEquals( 56, U1 );

var U2 = 16;
assertEquals( 57, eval("U2 = 57") );
assertEquals( 57, U2 );

var U3 = 33;
assertEquals( 35, eval("U3 += 2") );
assertEquals( 35, U3 );

var U4 = 44;
assertEquals( 45, eval("++U4") );
assertEquals( 45, U4 );

var U5 = 55;
assertEquals( 55, eval("U5++") );
assertEquals( 56, U5 );

var U6 = 66;
assertEquals( 65, eval("--U6") );
assertEquals( 65, U6 );

var U7 = 77;
assertEquals( 77, eval("U7--") );
assertEquals( 76, U7 );


{
	assertEquals( undefined, eval("") );
	// in GaltaJS this compile as an empty object while it is an empty block in JavaScript
	//assertEquals( undefined, eval("{}") );
	assertThrows( () =>  eval("i=9") ); // Strict mode
	assertEquals( undefined, eval("let i=9") );
	assertEquals( undefined, eval("const i=9") );
	assertEquals( undefined, eval("var i=9") );
	assertEquals( 9, eval("9") );
	assertEquals( 88, eval("99;88") );
	assertEquals( 99, eval("{99}") );
	assertEquals( 88, eval("{99;88}") );
	assertEquals( 3, eval("switch(1) { case 1: 3; break; default: 8}") );
	assertEquals( 8, eval("switch(2) { case 1: 3; break; default: 8}") );
	assertEquals( 7, eval("let i=9; if(i==9) { 7 } else { 5 }") );
	assertEquals( 5, eval("let i=9; if(i!=9) { 7 } else { 5 }") );
	assertEquals( 2, eval("let i=9; for(i=0; i<3; i++) {i}") );
	assertEquals( 'a', eval("let i=9; for(i in {a:1}) {i}") );
	assertEquals( 11, eval("let i=9; for(i of [11]) {i}") );
	assertEquals( 7, eval("let i=5; do {i} while(++i<8)") );
	assertEquals( 8, eval("let i=5; while(++i<9) {i}") );
}