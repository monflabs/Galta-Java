// Without the "u"/"v" flag, . matches exactly one UTF-16 code unit, so an
// astral character (a surrogate pair, two code units) doesn't fit between
// ^ and $ - but a single lone surrogate half does.
assertNull(/^.$/.exec('😀'));
assertNotNull(/^.$/.exec('\ud83d'));
assertNotNull(/^.$/.exec('\ude00'));

// A lone-surrogate pattern atom (no "u" flag) matches its raw code-unit
// value directly within a string, even where that same code unit forms a
// valid surrogate pair with its neighbor - test262's own
// built-ins/RegExp/prototype/Symbol.match/builtin-infer-unicode.js repro.
assertEquals(['\udf06'], /\udf06/.exec('𝌆'));
assertEquals(['\ud834'], /\ud834/.exec('𝌆'));

// With the "u" flag, the SAME lone-surrogate atom must NOT match inside a
// valid pair - the pair is one combined (non-BMP) code point, and a lone
// surrogate half is a different, standalone code point that doesn't occur
// here at all.
assertNull(/\udf06/u.exec('𝌆'));

// [[OriginalFlags]] (the internal slot), not the observable "unicode"
// property, decides which of the two modes above RegExpBuiltinExec uses -
// an overridden "unicode" own-property must not change the actual match.
{
	let r = /\udf06/;
	Object.defineProperty(r, 'unicode', { value: true });
	assertNotNull(r[Symbol.match]('𝌆'));

	let r2 = /\udf06/u;
	Object.defineProperty(r2, 'unicode', { value: false });
	assertNull(r2[Symbol.match]('𝌆'));
}
