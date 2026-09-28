const p = "The quick brown fox jumps over the lazy dog. If the dog reacted, was it really lazy?";
 
assertEquals(
	'The quick brown fox jumps over the lazy monkey. If the monkey reacted, was it really lazy?',
	p.replaceAll('dog', 'monkey')
);

assertThrows( () => {
		p.replaceAll(/Dog/i, 'ferret') // Not global-> exception
	}
);

assertEquals(
	'The quick brown fox jumps over the lazy FERRET. If the FERRET reacted, was it really lazy?',
	p.replaceAll('dog', (match,offset,string) => {
		assertEquals('dog',match)
		//assertEquals(offset,40)
		assertTrue(offset==40 || offset==52)
		assertEquals(p,string)
		return 'FERRET';
	} )
);

assertEquals(
	'The quick brown fox jumps over the lazy FERRET. If the FERRET reacted, was it really lazy?',
	p.replaceAll(/Dog/ig, (match,offset,string) => {
		assertEquals('dog',match)
		assertTrue(offset==40 || offset==52)
		assertEquals(p,string)
		return 'FERRET';
	} )
);

assertEquals( "XaXaX", "aa".replaceAll("", "X") )


const Replace1 = {
	value: 'bar',
	[Symbol.replace]: function(string) {
	  return `s/${string}/${Replace1.value}/g`;
	}
}
assertEquals('s/foo/bar/g','foo'.replaceAll(Replace1));

// The searchValue's non-global-flag check must happen BEFORE `this` is
// stringified - a poisoned this.toString() must not fire ahead of the
// spec-mandated TypeError for a non-global RegExp searchValue.
let thisToStringCalled = false;
const poisonedThis = { toString: () => { thisToStringCalled = true; return "x"; } };
assertThrows( () => String.prototype.replaceAll.call(poisonedThis, /x/, "y") );
assertFalse(thisToStringCalled);
