// #if INTERPRETER
const f = new Function("return 79;");
assertEquals( 79, f() );

const f1 = new Function("a", "return a*5;");
assertEquals( 30, f1(6) );

const f2 = new Function("a","b", "return a*b;");
assertEquals(15, f2(3,5) );

const f3 = new Function("a,b", "return a*b;");
assertEquals(15, f3(3,5) );

const f4 = new Function("{a,b}", "return a*b;");
assertEquals(15, f4({a:3,b:5}) );

const g = Function("return 34;");
assertEquals( 34, g() );

const g2 = Function("a", "return a+8;");
assertEquals( 14, g2(6) );
// #endif