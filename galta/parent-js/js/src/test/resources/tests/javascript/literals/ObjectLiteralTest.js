var a = 'A'
var ab = {a: 'a', b: 'b'}

// To be checked by the caller
// Checks if maps the proper Java objects
var v1 = {a}
var v2 = {...ab}
var v3 = {a,...ab}
var v4 = {a:'c',...ab}
var v5 = {a:'c',...ab,b:'B'}
var v6 = {a:'c',...ab,a}

assertEquals({a:'A'},v1);
assertEquals({a:'a',b:'b'},v2);
assertEquals({a:'a',b:'b'},v3);
assertEquals({a:'a',b:'b'},v4);
assertEquals({a:'a',b:'B'},v5);
assertEquals({a:'A',b:'b'},v6);


// Keywords
var ak = {null: 1, undefined: 2, class: 3}
assertEquals( 1, ak[null] );
assertEquals( 1, ak["null"] );
assertEquals( 2, ak[undefined] );
assertEquals( 2, ak["undefined"] );
assertEquals( 3, ak["class"] );

//
// Spread tests
assertEquals( {a:'A'}, {a} )
assertEquals( {a:'a', b:'b'}, {...ab} )
assertEquals( {a:'a', b:'b'}, {a,...ab} )
assertEquals( {a:'a', b:'b'}, {a:'c',...ab} )
assertEquals( {a:'a', b:'B'}, {a:'c',...ab,b:'B'} )
assertEquals( {a:'A', b:'b'}, {a:'c',...ab,a} )

//
// Check all data types
const a1 = {a:1, 2:3, 3.0:4, true:5, false: 6, null: 7}
assertEquals( 1, a1['a'] )
assertEquals( 3, a1['2'] )
assertEquals( 4, a1['3'] )
assertEquals( 5, a1['true'] )
assertEquals( 6, a1['false'] )
assertEquals( 7, a1[null] )

//
// Short hand syntax
const x=3, y=4;
assertEquals( {x: 3, y:4}, {x,y} )
const n = "XY"
assertEquals( {XY: 5}, {[n]: 5} )

//
// Calculated prop name
let prop = 'foo';
let o = {
  [prop]: 'hey',
  ['b' + 'ar']: 'there'
}
assertEquals( {foo: 'hey', 'bar': 'there'}, o )

//
// Should be able to use keywords in property name
let kprop = {
	for: "x",
	break: "y"
} 
assertEquals( {"for": 'x', "break": 'y'}, kprop )

//
// 'null' key
const n1 = "N1", n2=null, n3="N3"
assertEquals( {N1: 1, null: 2, N3: 3}, {[n1]: 1, [n2]: 2, [n3]:3} )
assertEquals( {N1: 1, null: 2, N3: 3}, {[n1]: 1, null: 2, [n3]:3} )


//
// Expressions in spread
function f() {return {b:'M'} }
const f2 = () => { return {b:'M'} };
assertEquals( {a:'N', b: 'M'}, { a: 'N', ...f() } )
assertEquals( {a:'N', b: 'M'}, { a: 'N', ...f2() } )
assertEquals( {a:'N', b: 'M'}, { a: 'N', ...(() => { return {b:'M'} })() } )
assertEquals( {a:'N', b: 'M'}, { a: 'N', ...( function f() { return {b: 'M'} } )() } )
assertEquals( {a:'N', b: 'M', c: 'n'}, { a: 'N', ...(() => { return {b:'M'} })(), c: 'n' } )


//
// Trailing coma
assertParseError("const o21 = {,}")
assertParseError("const o21 = {,,}")
const o22 = {a: 1,}
const o23 = {a: 2, b:3,}
assertEquals({a: 1},o22)
assertEquals({a: 2, b:3},o23)

//
// Literal with methods
const meth = {
	one() {
		return 1;
	},
	inc(v) {
		return v+this.one();
	},
	["de"+'c'](v) {
		return v-this.one();
	}
}
assertEquals(1,meth.one())
assertEquals(7,meth.inc(6))
assertEquals(5,meth.dec(6))
assertEquals("inc",meth.inc.name)
assertEquals("dec",meth.dec.name)


//
// Test nested 'this'
const collection = {
    one: 1,
    two: 2,
    [Symbol.iterator]() {
        const values = Object.keys(this);
        let i = 0;
        return {
            next: () => {
                return {
                    value: this[values[i++]],
                    done: i > values.length
                }
            }
        };
    }
};
const iterator = collection[Symbol.iterator]();
assertEquals(1,iterator.next().value);    		// → {value: 1, done: false}
assertEquals(2,iterator.next().value);    		// → {value: 2, done: false}
assertEquals(undefined,iterator.next().value);  // → {value: undefined, done: true}


//
// Indirection
const cc = { toString() {return "toto"} }
const ac = { [cc]: 44 }
assertEquals(ac,{'toto':44})


//
// Getter & Setter
{
	const o1 = {
		v: 222,
		['w']: 555,
		get f() { return this.v; },
		set f(v) { this.v = v; },
		get ['g']() { return this.w; },
		set ['g'](w) { this.w = w; },
	}
	assertEquals( 222, o1.v )
	assertEquals( 222, o1.f )
	assertEquals( 555, o1.w )
	assertEquals( 555, o1.g )
	o1.f = 333
	o1.g = 666
	assertEquals( 333, o1.v )
	assertEquals( 333, o1.f )
	assertEquals( 666, o1.w )
	assertEquals( 666, o1.g )
	
	const p = {
		get f() { return this.v; },
		set f(v) { this.v = v; },
	}
	const o = {
		v: 222,
	}
	Object.setPrototypeOf(o,p);
	
	o.f = 33
	assertEquals( 33, o.v )
}

// Unicode escapes in a property name are decoded the same way a plain
// identifier's are, even when they spell out a reserved word (e.g. if for
// "if"). This applies to object literal keys, method names, and destructuring
// targets - all go through the same PropertyName() parser production.
{
	const reservedPropObj = { if: 42 };
	assertEquals(42, reservedPropObj.if);

	let reservedDestrTarget;
	({ if: reservedDestrTarget } = { if: 7 });
	assertEquals(7, reservedDestrTarget);

	// Same, spelled with an escape (decodes to "if" too).
	const reservedPropEscObj = { i\u0066: 99 };
	assertEquals(99, reservedPropEscObj.if);
}

// Own-property enumeration order: array-index-like keys ("0","1","2",...)
// always come first, sorted ascending, followed by everything else in
// insertion order - regardless of when the integer keys were added relative
// to the string keys. A `delete` of a string key that happens to sit right
// after an integer key in the internal insertion-order list must not corrupt
// that list and silently drop the integer key from later enumeration (a real
// bug found and fixed this session: the internal doubly-linked list's
// backward pointers weren't updated when an integer key was inserted via its
// own sorted-insertion fast path, so deleting the following entry rewired
// the list's head incorrectly and orphaned the integer-keyed entry).
{
	// Named orderTest* (not "o") - this file's own top-level "let o" (see
	// above) would otherwise be shadowed by a same-named const here, which
	// hits a separate, real bug: a for-in/for-of loop's OWN collection
	// expression resolves a shadowed identifier to the OUTER binding
	// instead of this block's local one, even though a plain statement
	// using the identical expression earlier in the SAME block resolves it
	// correctly. Documented in KnownGaps.md rather than fixed here.
	const orderTestObj = { p1: 'p1', p2: 'p2', p3: 'p3' };
	orderTestObj.p4 = 'p4';
	orderTestObj[2] = '2';
	orderTestObj[0] = '0';
	orderTestObj[1] = '1';
	delete orderTestObj.p1;
	delete orderTestObj.p3;
	orderTestObj.p1 = 'p1'; // re-added after delete - goes to the end of insertion order

	assertEquals(['0', '1', '2', 'p2', 'p4', 'p1'], Object.keys(orderTestObj));
	assertEquals(
		'{"0":"0","1":"1","2":"2","p2":"p2","p4":"p4","p1":"p1"}',
		JSON.stringify(orderTestObj)
	);

	let seen = [];
	for (const k in orderTestObj) { seen.push(k); }
	assertEquals(['0', '1', '2', 'p2', 'p4', 'p1'], seen);
}

// Narrower repro of the same list-corruption bug: a single delete of a
// string key immediately following an integer key must not orphan it.
{
	const orderTestObj2 = {};
	orderTestObj2.a = 'a';
	orderTestObj2[0] = '0';
	orderTestObj2.b = 'b';
	delete orderTestObj2.a;
	assertEquals(['0', 'b'], Object.keys(orderTestObj2));
}


//
// Shorthand methods must survive a decompile + re-parse round trip (the
// AllGaltaJSDecompiledTests pass): a reserved-word name, get/set as plain
// method names, async/generator prefixes, computed and string-literal names.
//
{
  const o = {
    return() { return 1; },
    get() { return 2; },
    set() { return 3; },
    async af() { return 4; },
    *gen() { yield 5; },
    async *agen() { yield 6; },
    ["comp" + "uted"]() { return 7; },
    "str name"() { return 8; },
    if: 9,
  };
  assertEquals(1, o.return());
  assertEquals(2, o.get());
  assertEquals(3, o.set());
  assertEquals(5, o.gen().next().value);
  assertEquals(7, o.computed());
  assertEquals(8, o["str name"]());
  assertEquals(9, o.if);
  assertFalse("prototype" in o.return);
  assertThrows(TypeError, () => new o.return());
}
