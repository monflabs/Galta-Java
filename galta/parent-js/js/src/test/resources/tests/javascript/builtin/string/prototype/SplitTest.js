const b = "45|678|9"

assertEquals( ['45|678|9'], b.split() )
assertEquals( ['45','678','9'], b.split('|') )
assertEquals( ['45|678|9'], b.split(';') )
assertEquals( ['45|','78|9'], b.split('6') )


assertEquals( [''], "".split() )
assertEquals( [''], "".split(';') )

assertEquals( ['',''], ";".split(';') )
assertEquals( ['a','b'], "a;b".split(';') )


assertEquals( ['a;b','c','d;e'], "a;b;;c;;d;e".split(';;') )


//
// Char split when the sep is empty
//
assertEquals( [], "".split('') )
assertEquals( ['a','b','c'], "abc".split('') )


//
// Limit
//
assertEquals( ['Hello','World.','How'], 'Hello World. How are you doing?'.split(' ',3) )


//
// Regexp
// With and without capturing groups
//
assertEquals( [ "Hello ", " word. Sentence number ", "." ], 'Hello 1 word. Sentence number 2.'.split(/\d/) )
assertEquals( [ "Hello ", "1", " word. Sentence number ", "2", "." ], 'Hello 1 word. Sentence number 2.'.split(/(\d)/) )

// Remove spaces
const names = 'Harry Trump ;Fred Barney; Helen Rigby ; Bill Abel ;Chris Hand '
assertEquals( [ "Harry Trump", "Fred Barney", "Helen Rigby", "Bill Abel", "Chris Hand", "" ], names.split(/\s*(?:;|$)\s*/) )

// A non-RegExp object with a Symbol.split method must be dispatched to
// generically (not just genuine RegExp instances).
const Splitter1 = {
	[Symbol.split]: function(string) {
		return string.split('-');
	}
}
assertEquals( ['a','b','c'], 'a-b-c'.split(Splitter1) )
