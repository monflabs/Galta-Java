const regexp = /t(e)(st(\d?))/g;
const str = 'test1test2';

let i = 0;
for( const a of str.matchAll(regexp) ) {
	if(i==0) {
		assertEquals(["test1", "e", "st1", "1"], a);
		assertEquals(0, a.index);
		assertEquals("test1test2", a.input);
	} else if(i==1) {
 		assertEquals(["test2", "e", "st2", "2"], a);
		assertEquals(5, a.index);
		assertEquals("test1test2", a.input);
	} else {
		fail();
	}
	i++;
}

// A non-RegExp object with a Symbol.matchAll method must be dispatched to
// generically (not just genuine RegExp instances).
const Matcher1 = {
	[Symbol.matchAll]: function(string) {
		return [`matchedAll:${string}`][Symbol.iterator]();
	}
}
assertEquals(['matchedAll:hello'], Array.from('hello'.matchAll(Matcher1)))

// A non-global RegExp must throw TypeError.
assertThrows( () => 'test1test2'.matchAll(/t(e)(st(\d?))/) )
