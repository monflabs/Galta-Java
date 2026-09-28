// Check that the scoped variables are assigned to the right block context 
assertNotDeclared("a")
assertNotDeclaredInScope("a")
assertNotDeclared("b")
assertNotDeclaredInScope("b")

const a=3; 
assertEquals(a,3)

assertDeclared("a")
assertDeclaredInScope("a")

try { 
	const xx = 0; // To force a scope to be created so the asserts below work
	assertDeclared("a")
	assertNotDeclaredInScope("a")
} catch(e) {
	//Desired 
} 
assertEquals(a,3)

try { 
	assertNotDeclared("b")
	assertNotDeclaredInScope("b")

	const b=8
	assertEquals(b,8)

	assertDeclared("b")
	assertDeclaredInScope("b")
} catch(e) {
	//Desired 
} 

{
	const xx = 0; // To force a block scope to be created so the asserts below work
	assertDeclared("a")
	assertNotDeclaredInScope("a")
}

{
	// This block re-declares its own `a` below (shadowing the outer one) -
	// per TDZ, the outer `a` is NOT accessible here; referencing it must
	// throw rather than transparently fall through to the outer binding.
	assertThrows(ReferenceError, () => a)
	assertNotDeclaredInScope("a")

	const a=9
	
	assertDeclared("a")
	assertDeclaredInScope("a")
	
	try { 
		assertDeclared("a")
		assertNotDeclaredInScope("a")
	} catch(e) {
		//Desired 
	} 	
	assertEquals(a,9)
}

assertEquals(a,3)
