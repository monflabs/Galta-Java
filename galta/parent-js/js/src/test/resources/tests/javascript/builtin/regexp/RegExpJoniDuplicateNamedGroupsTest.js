// Duplicate named capture groups: the same GroupSpecifier name reused across
// mutually exclusive alternatives (e.g. "(?<x>a)|(?<x>b)") is valid, and only
// whichever alternative actually matched populates the `.groups` value.
// Reused sequentially in the SAME alternative (no shared disjunction branch
// to distinguish them) must remain a SyntaxError.

// Basic mutual-exclusion validation.
assertEquals('(?<x>a)|(?<x>b)', new RegExp('(?<x>a)|(?<x>b)').source);
assertThrows(SyntaxError, () => new RegExp('(?<x>a)(?<x>b)'));
assertThrows(SyntaxError, () => new RegExp('(?:(?<x>a))(?:(?<x>b))'));
assertThrows(SyntaxError, () => new RegExp('(?<x>(?<x>a))'));

// Whichever alternative captured populates .groups; the other stays undefined.
{
	let re = /(?<x>a)|(?<x>b)/;
	assertEquals('a', re.exec('a').groups.x);
	assertEquals('b', re.exec('b').groups.x);
}

// Numbered captures are unaffected by the name being shared - each group
// number is still reported independently.
assertEquals(['b', undefined, 'b'], /(?<x>a)|(?<x>b)/.exec('bab'));
assertEquals(['b', 'b', undefined], /(?<x>b)|(?<x>a)/.exec('bab'));

// \k<name> backreference resolves natively against whichever duplicate
// alternative actually participated in the match.
assertEquals(['aa', 'a', undefined], /(?:(?<x>a)|(?<x>b))\k<x>/.exec('aa'));
assertEquals(['bb', undefined, 'b'], /(?:(?<x>a)|(?<x>b))\k<x>/.exec('bb'));
assertEquals(null, /(?:(?<x>a)|(?<x>b))\k<x>/.exec('abab'));
assertEquals(null, /(?:(?<x>a)|(?<x>b))\k<x>/.exec('cdef'));

// Property enumeration order on .groups follows source order of first
// occurrence of each name, not which alternative actually matched.
{
	let re = /(?<y>a)(?<x>a)|(?<x>b)(?<y>b)/;
	assertEquals(['y', 'x'], Object.keys(re.exec('aa').groups));
	assertEquals(['y', 'x'], Object.keys(re.exec('bb').groups));
}

// $<name> replacement substitution also resolves the participating alternative.
assertEquals('[b]', /(?<x>a)|(?<x>b)/.exec('b') && 'b'.replace(/(?<x>a)|(?<x>b)/, '[$<x>]'));

// Three-way duplicate, mutually exclusive pairwise across a disjunction.
{
	let re = /(?<a>x)|(?<a>y)|(?<a>z)/;
	assertEquals('x', re.exec('x').groups.a);
	assertEquals('y', re.exec('y').groups.a);
	assertEquals('z', re.exec('z').groups.a);
}
