const p = "The quick brown fox jumps over the lazy dog. If the dog reacted, was it really lazy?";
 
assertEquals(
	'The quick brown fox jumps over the lazy monkey. If the dog reacted, was it really lazy?',
	p.replace('dog', 'monkey')
);

assertEquals(
	'The quick brown fox jumps over the lazy ferret. If the dog reacted, was it really lazy?',
	p.replace(/Dog/i, 'ferret')
);

assertEquals(
	'The quick brown fox jumps over the lazy ferret. If the ferret reacted, was it really lazy?',
	p.replace(/Dog/ig, 'ferret')
);

assertEquals(
	'The quick brown fox jumps over the lazy FERRET. If the dog reacted, was it really lazy?',
	p.replace('dog', (match,offset,string) => {
		assertEquals('dog',match)
		assertEquals(40,offset)
		assertEquals(p,string)
		return 'FERRET';
	} )
);

assertEquals(
	'The quick brown fox jumps over the lazy FERRET. If the dog reacted, was it really lazy?',
	p.replace(/Dog/i, (match,offset,string) => {
		assertEquals('dog',match)
		assertEquals(40,offset)
		assertEquals(p,string)
		return 'FERRET';
	} )
);

assertEquals( "Xaa", "aa".replace("", "X") )


const Replace1 = {
	value: 'bar',
	[Symbol.replace]: function(string) {
	  return `s/${string}/${Replace1.value}/g`;
	}
}
assertEquals('s/foo/bar/g','foo'.replace(Replace1));
