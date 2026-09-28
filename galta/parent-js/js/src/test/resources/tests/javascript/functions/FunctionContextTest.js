// Shows how a inner function access it parent function context
assertDeclared("v")
function f(v) {
	assertDeclared("v")
	assertDeclaredInScope("v")
	function inner() {
		assertDeclared("v")
		assertNotDeclaredInScope("v")
		return v+1;
	}
	return inner;
}

var v = f(2)();
assertEquals(v,3)
