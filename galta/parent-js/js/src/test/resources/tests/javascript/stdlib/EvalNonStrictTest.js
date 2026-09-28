//
// Update the global variables

let U1 = 15;
eval("U1 = 56");
assertEquals( 56, U1 );

var U2 = 16;
eval("U2 = 57");
assertEquals( 57, U2 );

var U3 = 17;
eval("var U3 = 58");
assertEquals( 58, U3 );

var U4 = 98;
eval("++U4");
assertEquals( 99, U4 );
