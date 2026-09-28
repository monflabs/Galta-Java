//
// Default value
function e1(a=4) {
	return a;
}
function e2(a=4,b=a+3) {
	return b;
}
const ac = 89
function e3(a=ac) {
	return a;
}
function e4(a=ac,b=a+8) {
	return b;
}
function e5({a,b}={b:14}) {
	return b;
}


assertEquals( 4, e1() )
assertEquals( 4, e1(undefined) ) // explicit undefined means default value
assertEquals( 7, e2() )
assertEquals( 9, e2(6) )
assertEquals( 2, e2(6,2) )
assertEquals( 89, e3() )
assertEquals( 97, e4() )
assertEquals( 97, e4() )
assertEquals( 14, e5() )
assertEquals( 9, e5({b:9}) )



//
// Destructuring Arrays
function f1([a]) {
	return a;
}
function f2([a,b]) {
	return a+b;
}
function f3([a,b,c]) {
	return a+b+c;
}


assertEquals( 1, f1([1,2,3]) )
assertEquals( 3, f2([1,2,3]) )
assertEquals( 6, f3([1,2,3]) )


//
// Destructuring Objects
function g1({a}) {
	return a;
}
function g2({a,b}) {
	return a+b;
}
function g3({a,b,c}) {
	return a+b+c;
}
function g4({a:aa,b,c}) {
	return aa+b+c;
}


assertEquals( 1, g1({a:1, b:2, c:3}) )
assertEquals( 3, g2({a:1, b:2, c:3}) )
assertEquals( 6, g3({a:1, b:2, c:3}) )
assertEquals( 6, g4({a:1, b:2, c:3}) )
