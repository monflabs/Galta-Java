assertDeclared("aa")
assertDeclaredInScope("aa")

var aa=1
assertEquals(1,aa)

assertDeclared("aa")
assertDeclaredInScope("aa")

function f() {
	assertDeclared("aa")
	assertDeclared("ab")
	assertDeclaredInScope("aa")
	assertDeclaredInScope("ab")
	
	assertEquals(null,aa)
	var aa = 12
	assertEquals(12,aa)	

	var ab = 13
}
f();

assertEquals(1,aa)


assertDeclared("A")
assertDeclaredInScope("A")

// Check that the local variables are assigned to the right block context 
{
	const xx = 0; // To force a block scope to be created so the asserts below work
	assertNotDeclaredInScope("A")
	var A = 6 
	assertEquals(A,6)
	{ 
		let A=5; 
		assertEquals(A,5) 
	} 
	assertEquals(A,6)
} 
assertEquals(A,6) 



// Check that the scoped variables are assigned to the right block context 
let a=3; 
assertEquals(a,3) 
{ 
	let a=4; 
	assertEquals(a,4) 
	{ 
		let a=5; 
		assertEquals(a,5) 
	} 
	assertEquals(a,4) 
} 
assertEquals(a,3)

{ 
	let b=8
	assertEquals(b,8)
	b=12
	assertEquals(b,12)
}

try { 
	let b=8
	assertEquals(b,8)
} catch(e) {
	//Desired 
} 

	