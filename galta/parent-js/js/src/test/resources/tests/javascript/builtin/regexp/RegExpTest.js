function matcher(regex, input) {
  return () => {
    const match = regex.exec(input)
    const lastIndex = regex.lastIndex
    return { lastIndex, match: match ? [...match] : null }
  }
}

// Check the prototype
assertEquals( RegExp.prototype, Object.getPrototypeOf(/x/))

// Each evaluation of a RegExp literal must create a fresh object (ES5+), even
// at the same source position (e.g. inside a loop or a repeatedly-called function).
function makeRegExp() { return /x/; }
assertFalse( makeRegExp() === makeRegExp() )
for (let i = 0; i < 2; i++) {
	if (i === 0) { var first = /x/; } else { assertFalse( first === /x/ ); }
}


//
// Flags
//
test('RegExp flags - global', function() {
	assertEquals( '', /x/.flags )
	assertEquals( 'dgimsuy', /x/dgimsuy.flags )
	
	assertEquals( true, /x/d.hasIndices )
	assertEquals( true, /x/g.global )
	assertEquals( true, /x/i.ignoreCase )
	assertEquals( true, /x/m.multiline )
	assertEquals( true, /x/s.dotAll )
	assertEquals( true, /x/u.unicode )
	assertEquals( true, /x/y.sticky )
});

//
// expr() - Global and Sticky
//
test('RegExp exec() - not global/sticky', function() {
	const input = 'haha haha haha'
	const nextGlobal = matcher(/ha/, input)
	assertEquals({ lastIndex: 0, match: ['ha'] }, nextGlobal())
	assertEquals({ lastIndex: 0, match: ['ha'] }, nextGlobal())
	assertEquals({ lastIndex: 0, match: ['ha'] }, nextGlobal())
})

test('RegExp exec() - global', function() {
	const input = 'haha haha haha'
	const nextGlobal = matcher(/ha/g, input)
	assertEquals({ lastIndex: 2, match: ['ha'] }, nextGlobal())
	assertEquals({ lastIndex: 4, match: ['ha'] }, nextGlobal())
	assertEquals({ lastIndex: 7, match: ['ha'] }, nextGlobal())
})

test('RegExp exec() - sticky', function() {
	const input = 'haha haha haha'
	const nextSticky = matcher(/ha/y, input)
	assertEquals({ lastIndex: 2, match: ['ha'] }, nextSticky())
	assertEquals({ lastIndex: 4, match: ['ha'] }, nextSticky())
	assertEquals({ lastIndex: 0, match: null }, nextSticky())
});


//
// test()
//
test('RegExp test()', function() {
	const str = 'table football';

	const regex = new RegExp('foo*');
	const globalRegex = new RegExp('foo*', 'g');

	assertEquals(true, regex.test(str));

	assertEquals(0, globalRegex.lastIndex);
	assertEquals(true, globalRegex.test(str));
	assertEquals(9, globalRegex.lastIndex);
	
	const str2 = 'hello world!';
	assertEquals( true, /^hello/.test(str2) );
	

	// Case sensitvity
	assertEquals(true, /[a-z]/.test("a"));
	assertEquals(false, /[a-z]/.test("A"));
	assertEquals(true, /[a-z]/i.test("A"));
	
})



//
// Compliance
//


// Test file for ECMA-262 spec compliance fixes

//
// Test 1: Duplicate flags should throw
//
test('RegExp duplicate flags', function() {
    try {
        new RegExp('a', 'gg');
        fail('Should throw SyntaxError for duplicate flags');
    } catch(e) {
        assertEquals('SyntaxError', e.name);
    }

    try {
        new RegExp('a', 'gigi');
        fail('Should throw SyntaxError for duplicate flags');
    } catch(e) {
        assertEquals('SyntaxError', e.name);
    }
});

//
// Test 2: Mutual exclusion of 'u' and 'v' flags
//
test('RegExp u and v flags mutual exclusion', function() {
    try {
        new RegExp('a', 'uv');
        fail('Should throw SyntaxError for both u and v flags');
    } catch(e) {
        assertEquals('SyntaxError', e.name);
    }

    try {
        new RegExp('a', 'vu');
        fail('Should throw SyntaxError for both u and v flags');
    } catch(e) {
        assertEquals('SyntaxError', e.name);
    }
});

//
// Test 3: RegExp constructor with flags parameter
//
test('RegExp constructor with flags', function() {
    const re1 = /abc/g;
    const re2 = new RegExp(re1, 'i');

    assertEquals('abc', re2.source);
    assertEquals('i', re2.flags);
    assertEquals(false, re2.global);
    assertEquals(true, re2.ignoreCase);
});

//
// Test 4: matchAll global flag behavior
//
test('RegExp matchAll global flag behavior', function() {
    // String.prototype.matchAll requires global flag
    try {
        'aaa'.matchAll(/a/);
        fail('String.prototype.matchAll should throw TypeError without global flag');
    } catch(e) {
        assertEquals('TypeError', e.name);
    }

    // But RegExp.prototype[@@matchAll] works without global flag
    // It just matches once and stops
    const re = /a/;
    const matches1 = [...re[Symbol.matchAll]('aaa')];
    assertEquals(1, matches1.length);
    assertEquals('a', matches1[0][0]);
    assertEquals(0, matches1[0].index);

    // With global flag, it matches all
    const reGlobal = /a/g;
    const matches2 = [...reGlobal[Symbol.matchAll]('aaa')];
    assertEquals(3, matches2.length);
});

//
// Test 5: Named capture groups
//
test('RegExp named capture groups', function() {
    const re = /(?<year>\d{4})-(?<month>\d{2})-(?<day>\d{2})/;
    const match = re.exec('2024-12-25');

    assertNotNull(match);
    assertEquals('2024', match.groups.year);
    assertEquals('12', match.groups.month);
    assertEquals('25', match.groups.day);
});

//
// Test 6: split() with capturing groups
//
test('RegExp split with capturing groups', function() {
    // Single capture group
    const result = 'a1b2c'.split(/(\d)/);
    assertEquals(['a', '1', 'b', '2', 'c'], result);

    // Multiple capture groups with undefined
    const result2 = 'a1b2c'.split(/(\d)(\d)?/);
    // Should include all capture groups, even undefined ones
    assertEquals(7, result2.length);
    assertEquals('a', result2[0]);
    assertEquals('1', result2[1]);
    assertEquals(undefined, result2[2]);  // group 2 didn't match
    assertEquals('b', result2[3]);
    assertEquals('2', result2[4]);
    assertEquals(undefined, result2[5]);  // group 2 didn't match
    assertEquals('c', result2[6]);

    // Multiple capture groups that all match
    const result3 = 'a12b34c'.split(/(\d)(\d)/);
    assertEquals(['a', '1', '2', 'b', '3', '4', 'c'], result3);
    assertEquals(7, result3.length);
});

//
// Test 7: replace() $0 should be literal
//
test('RegExp replace $0 literal', function() {
    const result = 'abc'.replace(/b/, '$0');
    assertEquals('a$0c', result);

    const result2 = 'abc'.replace(/b/, '$1');
    assertEquals('a$1c', result2); // No group 1, so literal
});

//
// Test 8: Source escaping in toString
//
test('RegExp toString escapes slashes', function() {
    const re = new RegExp('a/b');
    const str = re.toString();
    assertEquals('/a\\/b/', str);
});

//
// Test 9: Unicode flag with octal escapes
//
test('RegExp unicode flag forbids octal escapes', function() {
    // \0 not followed by digit is allowed in unicode mode
    const re1 = new RegExp('\\0', 'u');
    const match = re1.exec('a\u0000');
    assertNotNull(match);
    assertEquals('\u0000', match[0]);  // Should match the null character
    assertEquals(1, match.index);      // Found at position 1 (after 'a')

    // \0 followed by a digit is forbidden in unicode mode
    try {
        new RegExp('\\01', 'u');
        fail('Should throw SyntaxError for octal escape in unicode mode');
    } catch(e) {
        assertEquals('SyntaxError', e.name);
    }

    // Other octal escapes are also forbidden
    try {
        new RegExp('\\07', 'u');
        fail('Should throw SyntaxError for octal escape in unicode mode');
    } catch(e) {
        assertEquals('SyntaxError', e.name);
    }

    // Without unicode mode, octal escapes work
    const re2 = new RegExp('\\01');  // No 'u' flag
    const match2 = re2.exec('\u0001abc');
    assertNotNull(match2);
    assertEquals('\u0001', match2[0]);
});

//
// Test 10: Unicode escape validation
//
test('RegExp unicode escape validation', function() {
    // Valid unicode escape
    const re1 = new RegExp('\\u0041');
    assertEquals('A', re1.exec('A')[0]);

    // Invalid unicode escape in non-unicode mode is treated as literal 'u'
    // The source property shows the original pattern
    const re2 = new RegExp('\\u004G');
    assertEquals('\\u004G', re2.source);  // Source shows the backslash
    // But the pattern actually matches literal 'u004G'
    assertNotNull(re2.exec('u004G'));
    assertEquals('u004G', re2.exec('u004G')[0]);

    // Invalid unicode escape in unicode mode should throw
    try {
        new RegExp('\\u004G', 'u');
        fail('Should throw SyntaxError for invalid unicode escape in unicode mode');
    } catch(e) {
        assertEquals('SyntaxError', e.name);
    }
});

//
// Test 11: Unicode code point escapes
//
test('RegExp unicode code point escapes', function() {
    // \u{...} syntax only works in unicode mode
    const re = new RegExp('\\u{1F4A9}', 'u');
    assertEquals('💩', re.exec('💩')[0]);

    // Multiple characters
    const re2 = new RegExp('\\u{41}\\u{42}', 'u');
    assertEquals('AB', re2.exec('AB')[0]);
});

//
// Test 12: lastIndex boundary check
//
test('RegExp lastIndex at string length', function() {
    const re = /(?:)/g; // Empty pattern
    const str = 'abc';

    re.lastIndex = 3; // At end of string
    const match = re.exec(str);
    assertNotNull(match); // Should match empty string at position 3
    assertEquals(3, match.index);
});

//
// Test 13: Empty pattern split with unicode
//
test('RegExp empty pattern split', function() {
    const str = 'abc';

    // Without unicode flag
    const result1 = str.split(new RegExp(''));
    assertEquals(['a', 'b', 'c'], result1);

    // With unicode flag and surrogate pairs
    const str2 = 'a💩b';
    const result2 = str2.split(new RegExp('', 'u'));
    assertEquals(['a', '💩', 'b'], result2);
});

//
// Test 14: hasIndices flag
//
test('RegExp hasIndices flag', function() {
    const re = /a+/d;
    const match = re.exec('zaab');

    assertNotNull(match);
    assertNotNull(match.indices);
    assertEquals([1, 3], match.indices[0]);
});

//
// Test 15: Named groups in indices
//
test('RegExp named groups in indices', function() {
    const re = /(?<word>\w+)/d;
    const match = re.exec('abc');

    assertNotNull(match);
    assertNotNull(match.indices);
    assertNotNull(match.indices.groups);
    assertEquals([0, 3], match.indices.groups.word);
});

//
// Test 16: Zero-width matches advance correctly
//
test('RegExp zero-width matches advance', function() {
    const s = "abc";
    const re = /(?=[a-b])/g;
    const matches = [...s.matchAll(re)];

    assertEquals(2, matches.length);
    assertEquals("", matches[0][0]);
    assertEquals(0, matches[0].index);
    assertEquals("", matches[1][0]);
    assertEquals(1, matches[1].index);
});

//
// Test 17: Zero-width matches with exec
//
test('RegExp zero-width matches with exec', function() {
    const s = "abc";
    const re = /(?=[a-b])/g;

    // Per spec (RegExpBuiltinExec), lastIndex is set to the match's END
    // index unconditionally, even for a zero-width match - exec() itself
    // does NOT step past it. That "advance by one position on an empty
    // match" behavior belongs exclusively to the CALLER (the match/
    // matchAll/replace/split algorithms' own AdvanceStringIndex step), not
    // to exec(), so calling exec() again without manually moving lastIndex
    // re-finds the same zero-width match at the same position.
    const match1 = re.exec(s);
    assertNotNull(match1);
    assertEquals("", match1[0]);
    assertEquals(0, match1.index);
    assertEquals(0, re.lastIndex);

    const match1b = re.exec(s);
    assertNotNull(match1b);
    assertEquals(0, match1b.index);
    assertEquals(0, re.lastIndex);

    // A caller advances lastIndex itself to progress past a zero-width match.
    re.lastIndex = 1;
    const match2 = re.exec(s);
    assertNotNull(match2);
    assertEquals("", match2[0]);
    assertEquals(1, match2.index);
    assertEquals(1, re.lastIndex);

    re.lastIndex = 2;
    const match3 = re.exec(s);
    assertNull(match3); // 'c' at index 2 isn't followed by [a-b]
    assertEquals(0, re.lastIndex); // Should reset to 0
});

//
// Test 18: matchAll with backreferences (non-global)
//
test('RegExp matchAll with backreferences non-global', function() {
    const s = "aabbc";
    const re = /([a-z])\1/;  // No 'g' flag

    // RegExp.prototype[@@matchAll] works without global flag
    const matches = [...re[Symbol.matchAll](s)];

    assertEquals(1, matches.length);
    assertEquals('aa', matches[0][0]);
    assertEquals('a', matches[0][1]);
    assertEquals(0, matches[0].index);
});

//
// Test 19: matchAll with non-callable Symbol.matchAll
//
test('String matchAll with non-callable Symbol.matchAll', function() {
    const s = "aabbc";
    const re = {
        [Symbol.matchAll]: 42
    };

    try {
        s.matchAll(re);
        fail('Should throw TypeError for non-callable Symbol.matchAll');
    } catch(err) {
        assertEquals('TypeError', err.name);
        assertTrue(err.message.includes('Symbol.matchAll'));
        assertTrue(err.message.includes('not a function'));
    }
});

//
// Test 20: matchAll with custom Symbol.matchAll function
//
test('String matchAll with custom Symbol.matchAll', function() {
    const s = "abc";
    const customMatcher = {
        [Symbol.matchAll]: function(str) {
            // Custom implementation that returns a simple iterator
            return {
                next: function() {
                    return { done: true, value: undefined };
                },
                [Symbol.iterator]: function() { return this; }
            };
        }
    };

    const result = s.matchAll(customMatcher);
    const match = result.next();
    assertTrue(match.done);
    assertEquals(undefined, match.value);
});

//
// Test 21: split() with end-of-string anchor ($)
//
test('RegExp split with end anchor', function() {
    // Pattern that matches spaces and semicolons, including at end of string
    const names = 'Harry Trump ;Fred Barney; Helen Rigby ; Bill Abel ;Chris Hand ';
    const result = names.split(/\s*(?:;|$)\s*/);

    // Should have one empty string at the end (from the $ match)
    // but not two (no duplicate from remaining substring)
    assertEquals(6, result.length);
    assertEquals('Harry Trump', result[0]);
    assertEquals('Fred Barney', result[1]);
    assertEquals('Helen Rigby', result[2]);
    assertEquals('Bill Abel', result[3]);
    assertEquals('Chris Hand', result[4]);
    assertEquals('', result[5]);
});

//
// Test 22: Dot should not match \r, \u2028, \u2029
//
test('RegExp dot excludes all JS line terminators', function() {
    const re = /./;

    // Should not match \n
    assertNull(re.exec('\n'));
    // Should not match \r
    assertNull(re.exec('\r'));
    // Should not match \u2028 (line separator)
    assertNull(re.exec('\u2028'));
    // Should not match \u2029 (paragraph separator)
    assertNull(re.exec('\u2029'));

    // Should match regular characters
    assertNotNull(re.exec('a'));
    assertNotNull(re.exec(' '));
    assertNotNull(re.exec('\t'));
});

//
// Test 23: Dot with dotAll flag matches everything
//
test('RegExp dot with s flag matches all', function() {
    const re = /./s;

    assertNotNull(re.exec('\n'));
    assertNotNull(re.exec('\r'));
    assertNotNull(re.exec('\u2028'));
    assertNotNull(re.exec('\u2029'));
    assertNotNull(re.exec('a'));
});

//
// Test 24: ^ in multiline only matches after JS line terminators
//
test('RegExp ^ multiline uses JS line terminators', function() {
    // Should match after \n
    const m1 = /^x/m.exec('a\nx');
    assertNotNull(m1);
    assertEquals('x', m1[0]);

    // Should match after \r
    const m2 = /^x/m.exec('a\rx');
    assertNotNull(m2);
    assertEquals('x', m2[0]);

    // Should match after \u2028
    const m3 = /^x/m.exec('a\u2028x');
    assertNotNull(m3);
    assertEquals('x', m3[0]);

    // Should match after \u2029
    const m4 = /^x/m.exec('a\u2029x');
    assertNotNull(m4);
    assertEquals('x', m4[0]);

    // Should NOT match after \u0085 (Java considers this a line terminator but JS does not)
    const m5 = /^x/m.exec('a\u0085x');
    assertNull(m5);

    // Should match at start of string
    const m6 = /^x/m.exec('x');
    assertNotNull(m6);
});

//
// Test 25: $ in multiline only matches before JS line terminators
//
test('RegExp $ multiline uses JS line terminators', function() {
    // Should match before \n
    const m1 = /x$/m.exec('x\na');
    assertNotNull(m1);
    assertEquals('x', m1[0]);

    // Should match before \r
    const m2 = /x$/m.exec('x\ra');
    assertNotNull(m2);
    assertEquals('x', m2[0]);

    // Should match before \u2028
    const m3 = /x$/m.exec('x\u2028a');
    assertNotNull(m3);
    assertEquals('x', m3[0]);

    // Should match before \u2029
    const m4 = /x$/m.exec('x\u2029a');
    assertNotNull(m4);
    assertEquals('x', m4[0]);

    // Should NOT match before \u0085 (Java considers this a line terminator but JS does not)
    const m5 = /x$/m.exec('x\u0085a');
    assertNull(m5);

    // Should match at end of string
    const m6 = /x$/m.exec('x');
    assertNotNull(m6);
});

//
// Test 26: $ handles \r\n as a single line terminator
//
test('RegExp $ before CRLF', function() {
    const m = /x$/m.exec('x\r\na');
    assertNotNull(m);
    assertEquals('x', m[0]);
    assertEquals(0, m.index);
});

//
// Test 27: Unicode property escapes - General Category
//
test('RegExp unicode property General_Category', function() {
    // Short form
    const re1 = new RegExp('\\p{Lu}', 'u');
    assertNotNull(re1.exec('A'));
    assertNull(re1.exec('a'));
    assertNull(re1.exec('1'));

    // Long form
    const re2 = new RegExp('\\p{Uppercase_Letter}', 'u');
    assertNotNull(re2.exec('A'));
    assertNull(re2.exec('a'));

    // Composite category
    const re3 = new RegExp('\\p{L}', 'u');
    assertNotNull(re3.exec('A'));
    assertNotNull(re3.exec('a'));
    assertNull(re3.exec('1'));

    // With explicit property name
    const re4 = new RegExp('\\p{General_Category=Nd}', 'u');
    assertNotNull(re4.exec('5'));
    assertNull(re4.exec('a'));

    // Negated
    const re5 = new RegExp('\\P{L}', 'u');
    assertNull(re5.exec('A'));
    assertNotNull(re5.exec('1'));
});

//
// Test 28: Unicode property escapes - Script
//
test('RegExp unicode property Script', function() {
    const re1 = new RegExp('\\p{Script=Greek}', 'u');
    assertNotNull(re1.exec('α')); // alpha
    assertNull(re1.exec('a'));

    const re2 = new RegExp('\\p{Script=Latin}', 'u');
    assertNotNull(re2.exec('a'));
    assertNull(re2.exec('α'));

    // Short alias
    const re3 = new RegExp('\\p{sc=Cyrillic}', 'u');
    assertNotNull(re3.exec('А')); // Cyrillic A

    // A script name without "Script=" ("\\p{Greek}") is a SyntaxError in
    // the spec; see RegExpJoniEarlyErrorsTest.js.
});

//
// Test 29: Unicode property escapes - Binary properties
//
test('RegExp unicode property binary', function() {
    // Alphabetic
    const re1 = new RegExp('\\p{Alphabetic}', 'u');
    assertNotNull(re1.exec('a'));
    assertNotNull(re1.exec('A'));
    assertNull(re1.exec('1'));

    // White_Space
    const re2 = new RegExp('\\p{White_Space}', 'u');
    assertNotNull(re2.exec(' '));
    assertNotNull(re2.exec('\t'));
    assertNull(re2.exec('a'));

    // ASCII
    const re3 = new RegExp('^\\p{ASCII}+$', 'u');
    assertNotNull(re3.exec('hello'));
    assertNull(re3.exec('café'));

    // Hex_Digit
    const re4 = new RegExp('\\p{Hex_Digit}', 'u');
    assertNotNull(re4.exec('a'));
    assertNotNull(re4.exec('F'));
    assertNotNull(re4.exec('9'));
    assertNull(re4.exec('g'));
});

//
// Test 30: Unicode property escapes - Emoji
//
test('RegExp unicode property Emoji', function() {
    const re1 = new RegExp('\\p{Emoji}', 'u');
    assertNotNull(re1.exec('❤')); // heart

    const re2 = new RegExp('\\p{Emoji_Modifier}', 'u');
    assertNotNull(re2.exec('🏻')); // skin tone 1 (U+1F3FB as surrogate pair)
});

//
// Test 31: Unicode property escapes must require u flag
//
test('RegExp unicode property requires u flag', function() {
    // Without u flag, \p is treated as a literal escape
    const re = new RegExp('\\p{Lu}');
    // Should not match as a property escape - matches literal p{Lu}
    assertNotNull(re.exec('p{Lu}'));
    assertNull(re.exec('A'));
});

//
// Test 32: Invalid unicode property throws SyntaxError
//
test('RegExp invalid unicode property', function() {
    try {
        new RegExp('\\p{InvalidProperty}', 'u');
        fail('Should throw SyntaxError for invalid property');
    } catch(e) {
        assertEquals('SyntaxError', e.name);
    }

    try {
        new RegExp('\\p{General_Category=InvalidValue}', 'u');
        fail('Should throw SyntaxError for invalid GC value');
    } catch(e) {
        assertEquals('SyntaxError', e.name);
    }
});

//
// Test 33: Identity escape validation in unicode mode
//
test('RegExp identity escape in unicode mode', function() {
    // Valid escapes should work
    assertNotNull(new RegExp('\\/', 'u')); // escaped slash
    assertNotNull(new RegExp('\\.', 'u')); // escaped dot
    assertNotNull(new RegExp('\\^', 'u')); // escaped caret

    // Invalid letter escapes should throw in unicode mode
    try {
        new RegExp('\\q', 'u');
        fail('Should throw SyntaxError for \\q in unicode mode');
    } catch(e) {
        assertEquals('SyntaxError', e.name);
    }

    try {
        new RegExp('\\a', 'u');
        fail('Should throw SyntaxError for \\a in unicode mode');
    } catch(e) {
        assertEquals('SyntaxError', e.name);
    }

    // Without unicode mode, any escape is allowed
    assertNotNull(new RegExp('\\q'));
});

//
// Test 34: Unicode property in character class
//
test('RegExp unicode property in character class', function() {
    const re = new RegExp('[\\p{Lu}\\p{Ll}]', 'u');
    assertNotNull(re.exec('A'));
    assertNotNull(re.exec('z'));
    assertNull(re.exec('1'));

    // Negated inside class
    const re2 = new RegExp('[^\\p{L}]', 'u');
    assertNull(re2.exec('A'));
    assertNotNull(re2.exec('1'));
});

//
// Test 35: RegExp.escape() static method
//
test('RegExp.escape basic', function() {
    // Syntax characters are escaped
    assertEquals('\\^\\$\\\\\\.\\*\\+\\?\\(\\)\\[\\]\\{\\}\\|\\/Hello', RegExp.escape('^$\\.*+?()[]{}|/Hello'));

    // A leading ASCII letter or digit is escaped as \xHH (not passed
    // through) so the result can't be misread as an extension of a
    // preceding \0/\1.. DecimalEscape or \c control-letter escape when
    // spliced into a wider pattern.
    assertEquals('\\x68ello', RegExp.escape('hello'));

    // Leading digit is escaped as \xHH too, not the legacy \N form
    assertEquals('\\x31abc', RegExp.escape('1abc'));
    assertEquals('\\x30', RegExp.escape('0'));

    // Leading ASCII letter/digit rule only applies at position 0
    assertEquals('\\x611b', RegExp.escape('a1b'));

    // ControlEscape letters (\t \n \r) take priority over the generic
    // \xHH form; NUL matches no EncodeForRegExpEscape category at all,
    // so it passes through unescaped.
    assertEquals(' ', RegExp.escape(' '));
    assertEquals('\\t', RegExp.escape('\t'));
    assertEquals('\\n', RegExp.escape('\n'));
    assertEquals('\\r', RegExp.escape('\r'));

    // Empty string
    assertEquals('', RegExp.escape(''));
});

//
// Test 36: RegExp.escape() TypeError for non-string
//
test('RegExp.escape type check', function() {
    try {
        RegExp.escape(123);
        fail('Should throw TypeError for non-string');
    } catch(e) {
        assertEquals('TypeError', e.name);
    }

    try {
        RegExp.escape(undefined);
        fail('Should throw TypeError for undefined');
    } catch(e) {
        assertEquals('TypeError', e.name);
    }

    try {
        RegExp.escape(null);
        fail('Should throw TypeError for null');
    } catch(e) {
        assertEquals('TypeError', e.name);
    }
});

//
// Test 37: RegExp.escape() result is usable in RegExp
//
test('RegExp.escape round trip', function() {
    const special = '(hello)[world]{1,2}';
    const escaped = RegExp.escape(special);
    const re = new RegExp(escaped);
    assertNotNull(re.exec(special));
    assertEquals(special, re.exec(special)[0]);

    // Should NOT match unescaped interpretation
    assertNull(re.exec('helloworld'));
});

//
// Test 38: Dot in character class is literal
//
test('RegExp dot in character class is literal', function() {
    const re = /[.]/;
    assertNotNull(re.exec('.'));
    assertNull(re.exec('a'));

    // Dot outside class excludes line terminators
    const re2 = /a.b/;
    assertNotNull(re2.exec('axb'));
    assertNull(re2.exec('a\nb'));
    assertNull(re2.exec('a\rb'));
});

//
// Test 39: Multiline ^ and $ with various newline sequences
//
test('RegExp multiline CRLF handling', function() {
    // CRLF is a single line terminator
    const text = 'line1\r\nline2\r\nline3';
    const re = /^\w+/mg;
    const matches = [...text.matchAll(re)];
    assertEquals(3, matches.length);
    assertEquals('line1', matches[0][0]);
    assertEquals('line2', matches[1][0]);
    assertEquals('line3', matches[2][0]);

    // CR alone also works
    const text2 = 'line1\rline2\rline3';
    const matches2 = [...text2.matchAll(re)];
    assertEquals(3, matches2.length);
});

//
// Test 40: Non-multiline ^ and $ are anchors
//
test('RegExp non-multiline anchors', function() {
    const re = /^hello$/;
    assertNotNull(re.exec('hello'));
    assertNull(re.exec('hello\nworld'));
    assertNull(re.exec('say hello'));

    // Without multiline, ^ only matches start
    const re2 = /^line/;
    assertNotNull(re2.exec('line1\nline2'));
    assertEquals(0, re2.exec('line1\nline2').index);
});

//
// Test 41: dotAll combined with multiline
//
test('RegExp dotAll with multiline', function() {
    const re = /^.$/ms;
    // With s flag, dot matches \n, so full string matches
    assertNotNull(re.exec('\n'));
    // With m flag, ^ and $ match at line boundaries
    const re2 = /^(.+)$/mg;
    const text = 'aaa\nbbb\nccc';
    const matches = [...text.matchAll(re2)];
    assertEquals(3, matches.length);
    assertEquals('aaa', matches[0][1]);
    assertEquals('bbb', matches[1][1]);
    assertEquals('ccc', matches[2][1]);
});

//
// Test 42: Backreferences
//
test('RegExp backreferences', function() {
    const re = /([a-z])\1/;
    assertNotNull(re.exec('aab'));
    assertEquals('aa', re.exec('aab')[0]);
    assertNull(re.exec('abc'));

    // Named backreference
    const re2 = /(?<ch>[a-z])\k<ch>/;
    assertNotNull(re2.exec('bb'));
    assertEquals('bb', re2.exec('bb')[0]);
    assertNull(re2.exec('ab'));
});

//
// Test 43: Lookahead and lookbehind
//
test('RegExp lookahead and lookbehind', function() {
    // Positive lookahead
    const re1 = /foo(?=bar)/;
    assertNotNull(re1.exec('foobar'));
    assertEquals('foo', re1.exec('foobar')[0]);
    assertNull(re1.exec('foobaz'));

    // Negative lookahead
    const re2 = /foo(?!bar)/;
    assertNull(re2.exec('foobar'));
    assertNotNull(re2.exec('foobaz'));

    // Positive lookbehind
    const re3 = /(?<=foo)bar/;
    assertNotNull(re3.exec('foobar'));
    assertEquals('bar', re3.exec('foobar')[0]);
    assertNull(re3.exec('bazbar'));

    // Negative lookbehind
    const re4 = /(?<!foo)bar/;
    assertNull(re4.exec('foobar'));
    assertNotNull(re4.exec('bazbar'));
});

//
// Test 44: Word boundary with non-ASCII
//
test('RegExp word boundary', function() {
    const re = /\bfoo\b/;
    assertNotNull(re.exec('foo bar'));
    assertNotNull(re.exec('bar foo'));
    assertNull(re.exec('foobar'));
    assertNull(re.exec('barfoo'));
});

//
// Test 45: Quantifier edge cases
//
test('RegExp quantifiers', function() {
    // {n,m} bounds
    const re1 = /a{2,4}/;
    assertNull(re1.exec('a'));
    assertEquals('aa', re1.exec('aa')[0]);
    assertEquals('aaa', re1.exec('aaa')[0]);
    assertEquals('aaaa', re1.exec('aaaa')[0]);
    assertEquals('aaaa', re1.exec('aaaaa')[0]); // greedy: takes 4

    // Lazy quantifier
    const re2 = /a{2,4}?/;
    assertEquals('aa', re2.exec('aaaa')[0]); // lazy: takes 2

    // {0,} is same as *
    const re3 = /a{0,}/;
    assertEquals('', re3.exec('b')[0]);
    assertEquals('aaa', re3.exec('aaa')[0]);
});

//
// Test 46: Character class ranges and escapes
//
test('RegExp character class', function() {
    // Range
    const re1 = /[a-z]/;
    assertNotNull(re1.exec('m'));
    assertNull(re1.exec('M'));

    // Escape inside class
    const re2 = /[\d]/;
    assertNotNull(re2.exec('5'));
    assertNull(re2.exec('a'));

    // Negated class
    const re3 = /[^0-9]/;
    assertNotNull(re3.exec('a'));
    assertNull(re3.exec('5'));

    // Literal hyphen at start
    const re4 = /[-a]/;
    assertNotNull(re4.exec('-'));
    assertNotNull(re4.exec('a'));
    assertNull(re4.exec('b'));
});

//
// Test 47: Unicode flag basic matching
//
test('RegExp unicode flag basic', function() {
    // Unicode flag enables full code point matching for \u{} escapes
    const re1 = new RegExp('\\u{41}', 'u');
    assertNotNull(re1.exec('A'));

    // Unicode flag with character class
    const re2 = new RegExp('[\\u{61}-\\u{7A}]', 'u');
    assertNotNull(re2.exec('m'));
    assertNull(re2.exec('M'));
});

//
// Test 48: Sticky flag
//
test('RegExp sticky flag', function() {
    const re = /foo/y;
    re.lastIndex = 0;
    assertNotNull(re.exec('foobar'));
    assertEquals(3, re.lastIndex);

    // Won't match if lastIndex doesn't point to a match
    re.lastIndex = 1;
    assertNull(re.exec('foobar'));
    assertEquals(0, re.lastIndex);

    // Sticky with global
    const re2 = /\d+/gy;
    re2.lastIndex = 0;
    assertEquals('123', re2.exec('123abc456')[0]);
    assertNull(re2.exec('123abc456'));
});

// Test 49 (capture groups in repetitions) used to live here, but its
// expected values depend on whether the engine implements spec-correct
// RepeatMatcher per-iteration capture reset (22.2.2.5.1) - Joni (via the
// vendored, patched fork) now does; java.util.regex still doesn't, so the
// two engines genuinely diverge on this file's shared assertions. See
// RegExpJoniRepeatCaptureResetTest.js for the Joni-only, spec-correct
// version of this test.

//
// Test 50: Non-participating groups in alternation
//
test('RegExp non-participating groups', function() {
    // Only the matched alternative contributes groups
    var m = /(x)|(y)/.exec("y");
    assertEquals('y', m[0]);
    assertEquals(undefined, m[1]);
    assertEquals('y', m[2]);

    // First alternative wins even if shorter
    var m2 = /(a)|(ab)/.exec("ab");
    assertEquals('a', m2[0]);
    assertEquals('a', m2[1]);
    assertEquals(undefined, m2[2]);

    // Non-participating optional group
    var m3 = /a(b)?c/.exec("ac");
    assertEquals('ac', m3[0]);
    assertEquals(undefined, m3[1]);
});

//
// Test 51: Unicode astral plane matching
//
test('RegExp unicode astral plane', function() {
    // Astral code point as a single character with u flag
    const re1 = /^.$/u;
    assertNotNull(re1.exec('😀')); // 😀 U+1F600 matches as one code point
    assertNull(re1.exec('ab'));

    // Without the u flag, whether . matches a full astral character (two
    // UTF-16 code units) as a single "character" is engine-specific -
    // java.util.regex's own Dot node is code-point-aware by design
    // regardless of any flag, while Joni correctly matches exactly one
    // raw UTF-16 code unit per spec; see RegExpJoniLoneSurrogateTest.js
    // for the Joni-specific assertion.

    // Unicode flag with astral in character class
    const re3 = new RegExp('[\\u{1F600}-\\u{1F64F}]', 'u');
    assertNotNull(re3.exec('😀'));
    assertNotNull(re3.exec('🙏'));
    assertNull(re3.exec('A'));

    // Dot matches full code point with u flag
    const re4 = /(.)/gu;
    const chars = [...'A💩B'.matchAll(re4)].map(m => m[0]);
    assertEquals(['A', '💩', 'B'], chars);
});

//
// Test 52: Unicode flag with quantifiers on astral chars
//
test('RegExp unicode quantifiers on astral', function() {
    const re = new RegExp('\\u{1F600}+', 'u');
    const m = re.exec('😀😀😀');
    assertNotNull(m);
    assertEquals('😀😀😀', m[0]);

    // Repetition count is by code point
    const re2 = new RegExp('.{3}', 'u');
    const input = '💩💩💩';
    assertNotNull(re2.exec(input));
    assertEquals(input, re2.exec(input)[0]);
});

//
// Test 53: replace() with function callback
//
test('RegExp replace with function', function() {
    // Basic function replacement
    const result = 'hello world'.replace(/(\w+)/g, function(match, g1, offset, str) {
        return g1.toUpperCase();
    });
    assertEquals('HELLO WORLD', result);

    // Function receives correct arguments
    const args = [];
    'ab12cd'.replace(/(\d)(\d)/, function() {
        args.push(Array.from(arguments));
        return 'X';
    });
    assertEquals(1, args.length);
    assertEquals('12', args[0][0]);  // match
    assertEquals('1', args[0][1]);   // group 1
    assertEquals('2', args[0][2]);   // group 2
    assertEquals(2, args[0][3]);     // offset
    assertEquals('ab12cd', args[0][4]); // original string

    // Function with named groups
    const result2 = '2024-01-15'.replace(
        /(?<y>\d{4})-(?<m>\d{2})-(?<d>\d{2})/,
        function(match, y, m, d, offset, str, groups) {
            return d + '/' + m + '/' + y;
        }
    );
    assertEquals('15/01/2024', result2);
});

//
// Test 54: replace() substitution patterns
//
test('RegExp replace substitution patterns', function() {
    // $& - matched substring
    assertEquals('<<hello>>', 'hello'.replace(/hello/, '<<$&>>'));

    // $` - portion before match
    assertEquals('ab[ab]cd', 'abcd'.replace(/cd/, '[$`]cd'));

    // $' - portion after match
    assertEquals("ab[cd]cd", 'abcd'.replace(/ab/, "ab[$']"));

    // $$ - literal dollar sign
    assertEquals('a$c', 'abc'.replace(/b/, '$$'));

    // $n with groups
    assertEquals('world hello', 'hello world'.replace(/(\w+) (\w+)/, '$2 $1'));

// #if REGEXP_JONI
    // $<name> with named groups
    assertEquals('25/12/2024', '2024-12-25'.replace(
        /(?<year>\d{4})-(?<month>\d{2})-(?<day>\d{2})/,
        '$<day>/$<month>/$<year>'
    ));
// #endif

    // $nn for double-digit groups
    const groups = '0123456789ab'.replace(
        /(0)(1)(2)(3)(4)(5)(6)(7)(8)(9)(a)(b)/,
        '$11$12'
    );
    assertEquals('ab', groups); // $11=group 11='a', $12=group 12='b'
});

//
// Test 55: replace() with global flag
//
test('RegExp replace global', function() {
    assertEquals('x-x-x', 'a-b-c'.replace(/[a-c]/g, 'x'));
    assertEquals('', 'aaa'.replace(/a/g, ''));

    // Global replace with function
    const indices = [];
    'abab'.replace(/a/g, function(m, offset) {
        indices.push(offset);
        return 'x';
    });
    assertEquals([0, 2], indices);
});

//
// Test 56: Non-BMP in character classes with u flag
//
test('RegExp non-BMP character class with u flag', function() {
    // Range including astral plane
    const re = new RegExp('[A-Z\\u{1F600}-\\u{1F64F}]', 'u');
    assertNotNull(re.exec('A'));
    assertNotNull(re.exec('😀'));
    assertNull(re.exec('a'));
    assertNull(re.exec('1'));

    // Negated class with non-BMP
    const re2 = new RegExp('[^\\u{0}-\\u{7F}]', 'u');
    assertNull(re2.exec('A'));
    assertNotNull(re2.exec('é'));
    assertNotNull(re2.exec('💩'));
});

//
// Test 57: Greedy vs lazy with captures
//
test('RegExp greedy vs lazy captures', function() {
    // Greedy - captures last
    const m1 = /(a+)(a+)/.exec('aaaa');
    assertEquals('aaaa', m1[0]);
    assertEquals('aaa', m1[1]);  // greedy takes as much as possible
    assertEquals('a', m1[2]);

    // Lazy - captures first
    const m2 = /(a+?)(a+)/.exec('aaaa');
    assertEquals('aaaa', m2[0]);
    assertEquals('a', m2[1]);    // lazy takes as little as possible
    assertEquals('aaa', m2[2]);
});

//
// Test 58: Alternation semantics
//
test('RegExp alternation left-to-right', function() {
    // First alternative wins (left to right)
    const m = /a|ab/.exec('ab');
    assertEquals('a', m[0]); // not 'ab'

    // In a longer context
    const m2 = /(a|ab)c/.exec('abc');
    assertEquals('abc', m2[0]);
    assertEquals('ab', m2[1]); // 'a' + 'c' doesn't work, so tries 'ab' + 'c'
});

//
// Test 59: Empty alternative
//
test('RegExp empty alternative', function() {
    // Empty alternative matches empty string
    const re = /|a/;
    const m = re.exec('a');
    assertEquals('', m[0]); // empty alternative matches first
    assertEquals(0, m.index);

    const re2 = /a|/;
    const m2 = re2.exec('b');
    assertEquals('', m2[0]); // falls through to empty alternative
});

//
// Test 60: Nested groups and backreferences
//
test('RegExp nested groups', function() {
    // Nested groups numbered left-to-right by opening paren
    const m = /((a)(b))((c)(d))/.exec('abcd');
    assertEquals('abcd', m[0]);
    assertEquals('ab', m[1]);   // ((a)(b))
    assertEquals('a', m[2]);    // (a)
    assertEquals('b', m[3]);    // (b)
    assertEquals('cd', m[4]);   // ((c)(d))
    assertEquals('c', m[5]);    // (c)
    assertEquals('d', m[6]);    // (d)

    // Backreference to nested group
    const m2 = /((a+)b\2)/.exec('aabaa');
    assertEquals('aabaa', m2[0]);
    assertEquals('aabaa', m2[1]);
    assertEquals('aa', m2[2]);
});

//
// Test 61: Backreference basics
//
test('RegExp backreference basics', function() {
    // Backreference matches same text as group captured
    const m = /(ab)c\1/.exec('abcab');
    assertNotNull(m);
    assertEquals('abcab', m[0]);
    assertEquals('ab', m[1]);

    // Backreference with quantifier
    const m2 = /(a)\1{2}/.exec('aaa');
    assertNotNull(m2);
    assertEquals('aaa', m2[0]);
});

//
// Test 62: Non-capturing groups
//
test('RegExp non-capturing groups', function() {
    const m = /(?:a)(b)(?:c)(d)/.exec('abcd');
    assertEquals('abcd', m[0]);
    assertEquals('b', m[1]);  // first capturing group
    assertEquals('d', m[2]);  // second capturing group
    assertEquals(3, m.length); // only 2 groups + full match
});

//
// Test 63: replace with unmatched groups
//
test('RegExp replace unmatched group reference', function() {
    // Reference to group that didn't participate = empty string in replacement
    const result = 'abc'.replace(/(a)|(b)/, '[$1][$2]');
    // First alt matches 'a': $1='a', $2=undefined -> ''
    assertEquals('[a][]bc', result);
});

//
// Test 64: split edge cases
//
test('RegExp split edge cases', function() {
    // Split at beginning
    assertEquals(['', 'a', 'b'], 'xaxb'.split(/x/));

    // Split with limit
    assertEquals(['a', 'b'], 'a,b,c'.split(/,/, 2));

    // Split empty string that doesn't match returns array with empty string
    assertEquals([''], ''.split(/a/));

    // Match at start and end — n matches produce n+1 segments
    assertEquals(['', '', '', ''], 'aaa'.split(/a/));
    assertEquals(['', ''], 'a'.split(/a/));
});

//
// Test 65: search() method
//
test('RegExp search method', function() {
    assertEquals(2, 'abcdef'.search(/cd/));
    assertEquals(-1, 'abcdef'.search(/xyz/));
    assertEquals(0, 'abc'.search(/^/));

    // search ignores lastIndex (always searches from start)
    const re = /b/g;
    re.lastIndex = 5;
    assertEquals(1, 'abc'.search(re));
});

//
// Test 66: match() without global flag
//
test('RegExp match without global', function() {
    const m = 'abcabc'.match(/a(b)c/);
    assertNotNull(m);
    assertEquals('abc', m[0]);
    assertEquals('b', m[1]);
    assertEquals(0, m.index);
    assertEquals('abcabc', m.input);
});

//
// Test 67: match() with global flag
//
test('RegExp match with global', function() {
    const m = 'abcabc'.match(/a(b)c/g);
    assertNotNull(m);
    assertEquals(2, m.length);
    assertEquals('abc', m[0]);
    assertEquals('abc', m[1]);
    // No groups in global match
    assertEquals(undefined, m.index);
});

//
// Test 68: match() returns null on no match
//
test('RegExp match returns null', function() {
    assertNull('abc'.match(/xyz/));
    assertNull('abc'.match(/xyz/g));
});

//
// Test 69: Backtracking with quantifiers
//
test('RegExp backtracking', function() {
    // Engine must backtrack to find a valid match
    const m = /a.*b(.*)$/.exec('axxbyybyy');
    assertNotNull(m);
    assertEquals('axxbyybyy', m[0]);
    assertEquals('yy', m[1]);

    // Non-greedy backtracking
    const m2 = /a.*?b(.*)$/.exec('axxbyybyy');
    assertNotNull(m2);
    assertEquals('axxbyybyy', m2[0]);
    assertEquals('yybyy', m2[1]);
});

//
// Test 70: Unicode case folding with i flag
//
test('RegExp unicode case folding', function() {
    // Basic case insensitive
    const re = /abc/i;
    assertNotNull(re.exec('ABC'));
    assertNotNull(re.exec('AbC'));

    // Case insensitive in character class
    const re2 = /[a-z]/i;
    assertNotNull(re2.exec('A'));
    assertNotNull(re2.exec('Z'));

    // German sharp s does NOT match 'ss' (per ES spec, simple case folding)
    const re3 = /ß/iu;
    assertNotNull(re3.exec('ß'));
});

//
// Test 71: exec() with global flag state management
//
test('RegExp exec global state', function() {
    const re = /a/g;
    const str = 'axa';

    const m1 = re.exec(str);
    assertEquals(0, m1.index);
    assertEquals(1, re.lastIndex);

    const m2 = re.exec(str);
    assertEquals(2, m2.index);
    assertEquals(3, re.lastIndex);

    const m3 = re.exec(str);
    assertNull(m3);
    assertEquals(0, re.lastIndex);

    // Starts over
    const m4 = re.exec(str);
    assertEquals(0, m4.index);
});

//
// Test 72: Assertions don't consume characters
//
test('RegExp assertions zero-width', function() {
    // Lookahead doesn't consume
    const m = /a(?=b)/.exec('abc');
    assertEquals('a', m[0]);
    assertEquals(0, m.index);

    // Word boundary is zero-width
    const m2 = /\bfoo\b/.exec('a foo b');
    assertEquals('foo', m2[0]);
    assertEquals(2, m2.index);

    // ^ and $ are zero-width
    const m3 = /^/.exec('abc');
    assertEquals('', m3[0]);
    assertEquals(0, m3.index);
});

//
// Test 73: Escaped special characters
//
test('RegExp escaped specials', function() {
    // All special regex chars escaped
    const specials = '^$\\.*+?()[]{}|/';
    for (let i = 0; i < specials.length; i++) {
        const ch = specials[i];
        const re = new RegExp('\\' + ch);
        assertNotNull(re.exec(ch));
    }
});

//
// Test 74: \d \D \w \W \s \S character classes
//
test('RegExp shorthand character classes', function() {
    // \d = [0-9]
    assertNotNull(/^\d$/.exec('5'));
    assertNull(/^\d$/.exec('a'));

    // \D = [^0-9]
    assertNotNull(/^\D$/.exec('a'));
    assertNull(/^\D$/.exec('5'));

    // \w = [A-Za-z0-9_]
    assertNotNull(/^\w$/.exec('a'));
    assertNotNull(/^\w$/.exec('_'));
    assertNull(/^\w$/.exec('-'));

    // \W = [^A-Za-z0-9_]
    assertNotNull(/^\W$/.exec('-'));
    assertNull(/^\W$/.exec('a'));

    // \s includes all ES whitespace
    assertNotNull(/^\s$/.exec(' '));
    assertNotNull(/^\s$/.exec('\t'));
    assertNotNull(/^\s$/.exec('\n'));
    assertNotNull(/^\s$/.exec('\r'));
    assertNotNull(/^\s$/.exec(' '));  // NBSP
    assertNotNull(/^\s$/.exec('\u2028'));  // Line Separator
    assertNotNull(/^\s$/.exec('\u2029'));  // Paragraph Separator

    // \S is complement
    assertNull(/^\S$/.exec(' '));
    assertNotNull(/^\S$/.exec('a'));
});

//
// Test 75: Multiline + dotAll combined behavior
//
test('RegExp multiline dotAll combined', function() {
    const text = 'line1\nline2\nline3';

    // m flag: ^ and $ match at line boundaries
    // s flag: . matches \n
    const re = /^(.+)$/gms;
    const m = re.exec(text);
    assertNotNull(m);
    // With both m and s, .+ is greedy and . matches \n,
    // but $ still anchors at line end due to m flag
    // Actually with s flag, .+ will match everything including \n
    // So ^(.+)$ with ms matches the entire string
    assertEquals(text, m[0]);
});

//
// Match
//
test('RegExp match', function() {
	let re = /[0-9]+/g
	let str = '2016-01-02';
	assertEquals(["2016", "01", "02"], (re)[Symbol.match](str))
	assertEquals(["2016"], new RegExp("[0-9]+","y")[Symbol.match](str))
	assertEquals(["2016"], new RegExp("[0-9]+","")[Symbol.match](str))
	assertEquals(["2016"], /[0-9]+/y[Symbol.match](str))
	assertEquals(["2016"], /[0-9]+/[Symbol.match](str))
	assertEquals(["2016"], (/[0-9]+/y)[Symbol.match](str))
	assertEquals(["2016"], (/[0-9]+/)[Symbol.match](str))
});

//
// Test 76: replaceAll with string pattern
//
test('String replaceAll', function() {
    assertEquals('xbxbx', 'ababa'.replaceAll('a', 'x'));
    assertEquals('abc', 'abc'.replaceAll('x', 'y'));
    assertEquals('', ''.replaceAll('a', 'b'));

    // replaceAll with regex requires global flag
    assertEquals('xbxb', 'abab'.replaceAll(/a/g, 'x'));
    try {
        'ab'.replaceAll(/a/, 'x'); // non-global
        fail('Should throw TypeError');
    } catch(e) {
        assertEquals('TypeError', e.name);
    }
});

//
// Test 77: match/matchAll/split/replace dispatch through a custom "exec"
// on a non-RegExp receiver (RegExpExec-based generic dispatch)
//
test('RegExp Symbol.match/matchAll/split/replace generic dispatch', function() {
    function makeCustom(execImpl) {
        return {
            global: true,
            unicode: false,
            flags: 'g',
            exec: execImpl,
        };
    }

    // match: honors a receiver's own exec override.
    {
        let calls = 0;
        let custom = makeCustom(function(s) {
            calls++;
            return calls === 1 ? Object.assign(['a'], {index: 0}) : null;
        });
        assertEquals(['a'], RegExp.prototype[Symbol.match].call(custom, 'abc'));
        assertEquals(2, calls);
    }

    // replace: honors a receiver's own exec override too.
    {
        let calls = 0;
        let custom = makeCustom(function(s) {
            calls++;
            return calls === 1 ? Object.assign(['a'], {index: 0}) : null;
        });
        assertEquals('[a]bc', RegExp.prototype[Symbol.replace].call(custom, 'abc', '[$&]'));
    }

    // matchAll: per spec it matches against a SpeciesConstructor-built clone
    // rather than the receiver directly - a real RegExp subclass overriding
    // exec() is the spec-accurate way this is observable.
    {
        let calls = 0;
        class CustomRegExp extends RegExp {
            exec(s) {
                calls++;
                return calls <= 2 ? Object.assign([String(calls)], {index: calls - 1}) : null;
            }
        }
        let all = [...RegExp.prototype[Symbol.matchAll].call(new CustomRegExp('.', 'g'), 'xy')];
        assertEquals(2, all.length);
        assertEquals('1', all[0][0]);
        assertEquals('2', all[1][0]);
        assertEquals(3, calls);
    }

    // split doesn't hard-require `instanceof RegExp` either.
    assertEquals(['a', 'b', 'c'], /,/[Symbol.split]('a,b,c'));
    assertEquals('a-b-c', /,/g[Symbol.replace]('a,b,c', '-'));

    // GetSubstitution: numbered and named capture references, $$, $&, $`, $'.
    assertEquals('[a][a]', /(?<x>a)/[Symbol.replace]('a', '[$1][$<x>]'));
    assertEquals('$1 literal', /a/[Symbol.replace]('a', '$1 literal'));
    assertEquals('a$b', /x/[Symbol.replace]('axb', '$$'));
    assertEquals('a[a|c]c', /b/[Symbol.replace]('abc', "[$`|$']"));
});

// Legacy static properties (RegExp.$1-$9, input/$_, lastMatch/$&,
// lastParen/$+, leftContext/$`, rightContext/$') track the last successful
// match of any RegExp, whatever the operation.
test('RegExp legacy static properties', function() {
    /(\d+)-(\d+)/.exec('call 555-1234 now');
    assertEquals('555', RegExp.$1);
    assertEquals('1234', RegExp.$2);
    assertEquals('', RegExp.$3);
    assertEquals('555-1234', RegExp.lastMatch);
    assertEquals('555-1234', RegExp['$&']);
    assertEquals('1234', RegExp.lastParen);
    assertEquals('call ', RegExp.leftContext);
    assertEquals(' now', RegExp.rightContext);
    assertEquals('call 555-1234 now', RegExp.input);
    assertEquals('call 555-1234 now', RegExp.$_);

    // A failed match leaves them unchanged
    assertNull(/zzz/.exec('abc'));
    assertEquals('555', RegExp.$1);

    // test(), including a pattern without groups
    assertTrue(/b+/.test('abbbc'));
    assertEquals('bbb', RegExp.lastMatch);
    assertEquals('', RegExp.$1);
    assertEquals('', RegExp.lastParen);
    assertTrue(/(x)(y)?/.test('-x-'));
    assertEquals('x', RegExp.$1);
    assertEquals('', RegExp.$2); // a capture that did not participate is ""

    // replace/match/search/split: the last match wins
    'a1b2c3'.replace(/[a-z](\d)/g, '');
    assertEquals('3', RegExp.$1);
    assertEquals('c3', RegExp.lastMatch);
    'x10y20'.match(/\d+/g);
    assertEquals('20', RegExp.lastMatch);
    'hello world'.search(/o\s(w)/);
    assertEquals('w', RegExp.$1);
    'a,b;c'.split(/([,;])/);
    assertEquals(';', RegExp.$1);

    // input is writable; the other properties are read-only accessors
    RegExp.input = 'changed';
    assertEquals('changed', RegExp.$_);
    assertEquals(';', RegExp.$1);
});
