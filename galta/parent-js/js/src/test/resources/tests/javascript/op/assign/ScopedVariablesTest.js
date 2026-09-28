// Check that the variables are assigned to the right block context 
assertNotDeclared("a")
assertNotDeclaredInScope("a")

let a=3; 
assertEquals(a,3)
 
assertDeclared("a")
assertDeclaredInScope("a")

{
	// This block re-declares its own `a` below (shadowing the outer one) -
	// per TDZ, the outer `a` is NOT accessible here; referencing it must
	// throw rather than transparently fall through to the outer binding.
	assertThrows(ReferenceError, () => a)
	assertNotDeclaredInScope("a")
	assertNotDeclared("b")
	assertNotDeclaredInScope("b")
	
	let a=4, b=7;
	
	assertDeclared("a")
	assertDeclared("b")
	assertDeclaredInScope("a")
	assertDeclaredInScope("b")
	
	assertEquals(a,4) 
	{ 
		let a=5; 
		assertEquals(a,5) 
	} 
	assertEquals(a,4) 
} 
assertEquals(a,3)

{
	a = 6 
	assertEquals(a,6)
	{ 
		let a=5; 
		assertEquals(a,5) 
	} 
	assertEquals(a,6)
} 
assertEquals(a,6) 
