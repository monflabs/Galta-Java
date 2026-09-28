// Built-in object deletion 
{
	const o2 = {a:1, b:2, c:3}
	assertTrue( delete o2['a','c'] );
	assertEquals( {b:2}, o2);
	assertTrue ( delete o2['kk'] );
	assertEquals( {b:2}, o2);
}


// Built-in array deletion
{
	const a2 = ['a','b','c','d','e']
	assertTrue( delete a2[1,3] );
	assertEquals( ['a',,'c',,'e'], a2);
	 
	const a3 = ['a','b','c','d','e']
	assertTrue ( delete a3[*][1:3] );
	assertEquals( ['a','b','c','d','e'], a3);
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