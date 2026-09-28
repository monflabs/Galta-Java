// Per spec (RepeatMatcher, 22.2.2.5.1), every capture inside a quantified
// group is reset to unmatched at the START of each new iteration attempt -
// so a group not touched by the LAST successful iteration ends up
// undefined, even if an earlier iteration did capture something into it.
// Joni (and java.util.regex) don't do this natively; the vendored fork adds
// a genuine per-iteration reset (gated to provably-non-nullable quantified
// bodies - see RegExpEngineJoni's own comments for the narrower remaining
// gaps this doesn't cover).

// Groups in repeated alternation don't retain an earlier iteration's value.
{
	let m = /(?:(a)|(b))+/.exec("ab");
	assertEquals('ab', m[0]);
	assertUndefined(m[1]);
	assertEquals('b', m[2]);
}

// Reversed order.
{
	let m = /(?:(a)|(b))+/.exec("ba");
	assertEquals('ba', m[0]);
	assertEquals('a', m[1]);
	assertUndefined(m[2]);
}

// Outer group gets the last iteration; an inner group not touched by that
// last iteration resets even though an earlier iteration captured into it.
{
	let m = /((a)|(b))+/.exec("ab");
	assertEquals('ab', m[0]);
	assertEquals('b', m[1]);   // last captured by outer group
	assertUndefined(m[2]);
	assertEquals('b', m[3]);
}

// Optional group inside repetition is reset (not retained) when the last
// iteration's optional alternative doesn't match.
{
	let m = /(a(b)?)+/.exec("aba");
	assertEquals('aba', m[0]);
	assertEquals('a', m[1]);
	assertUndefined(m[2]);
}
