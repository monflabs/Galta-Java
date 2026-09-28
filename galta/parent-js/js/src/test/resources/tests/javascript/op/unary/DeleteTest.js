// Built-in object deletion 
{
	const o1 = {a:1, b:2}
	assertEquals( {a:1, b:2}, o1);
	assertTrue  ( delete o1.a );
	assertEquals( {b:2}, o1);
	assertTrue  ( delete o1.c );
	assertEquals( {b:2}, o1);
}


// Built-in array deletion
{
	const a = ['a','b','c']
	assertEquals( ['a','b','c'], a);
	assertTrue  ( delete a[1] );
	assertEquals( ['a',,'c'], a);
	assertTrue ( delete a["5"] ); // String is converted to a number!
	assertEquals( ['a',,'c'], a);
}

// Variables
{
	var v1 = 9;
	const v2 = 11;
	let v3 = 12;
	
	assertEquals( 9, v1 );
	assertThrows( () => delete v1 ); // Strict Mode
	
	assertThrows( () => delete v2 ); // Strict Mode
	assertEquals( 11, v2 );
	
	assertThrows( () => delete v3 ); // Strict Mode
	assertEquals( 12, v3 );
	assertThrows( () => delete v4 ); // Strict Mode
}

// SuperReferences may never be deleted, regardless of what the property key
// expression or the super base would otherwise do.
{
	class Base { x = 1; }
	class Sub extends Base {
		deleteIt() {
			delete super.x;
		}
	}
	assertThrows(ReferenceError, () => new Sub().deleteIt());

	let keyEvaluated = false;
	class Sub2 extends Base {
		deleteComputed() {
			delete super[(keyEvaluated = true, 'x')];
		}
	}
	assertThrows(ReferenceError, () => new Sub2().deleteComputed());
	assertFalse(keyEvaluated);
}