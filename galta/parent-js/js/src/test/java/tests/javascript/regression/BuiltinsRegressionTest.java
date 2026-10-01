/*
 * Copyright (c) 2019-2026 Philippe Riand
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package tests.javascript.regression;

import tests.javascript.JavaScriptStrictTestCase;

/**
 * Built-in objects regressions (rt/builtins review), run in every execution
 * mode through GaltaJSTestSuite.
 */
public class BuiltinsRegressionTest extends JavaScriptStrictTestCase {

	// sort with an inconsistent comparator: implementation-defined order, never
	// "Comparison method violates its general contract!"
	public void testSortInconsistentComparator() throws Exception {
		executeCode("""
			for(var k=0; k<40; k++) {
				var a = []; for(var i=0; i<2000; i++) a.push(i);
				a.sort(() => Math.random()-0.5);
				assertEquals(2000, a.length);
				assertEquals(1999000, a.reduce((x,y) => x+y, 0));
				var s = a.toSorted(() => Math.random()-0.5);
				assertEquals(2000, s.length);
				var t = new Float64Array(2000); for(var i=0; i<2000; i++) t[i] = i;
				t.sort(() => Math.random()-0.5);
				assertEquals(1999000, t.reduce((x,y) => x+y, 0));
			}
			// Still stable and correct with a consistent comparator
			var r = [{k:2,v:'a'},{k:1,v:'b'},{k:2,v:'c'},{k:1,v:'d'}].sort((x,y) => x.k-y.k);
			assertEquals('b,d,a,c', r.map(o => o.v).join());
			var big = []; for(var i=0; i<1000; i++) big.push((i*7919)%1000);
			big.sort((x,y) => x-y);
			for(var i=0; i<1000; i++) assertEquals(i, big[i]);
			assertEquals('1,10,2,,', [2,,10,1,undefined].sort().join());
			""");
	}

	// IsCallable: a Proxy over a non-callable target is not callable
	public void testNonCallableProxy() throws Exception {
		executeCode("""
			var P = new Proxy({}, {});
			assertThrows(TypeError, () => [].forEach(P));
			assertThrows(TypeError, () => [].map(P));
			assertThrows(TypeError, () => new Map().forEach(P));
			assertThrows(TypeError, () => [].values().map(P));
			assertThrows(TypeError, () => Function.prototype.bind.call(P));
			assertEquals('2,4', [1,2].map(new Proxy(x => x*2, {})).join());
			var log = [];
			Promise.resolve(5).then(P).then(v => log.push('pass ' + v));
			Promise.resolve({then: P}).then(v => log.push('fulfilled ' + typeof v.then));
			Promise.resolve().then(()=>0).then(()=>0).then(()=>0).then(()=>0)
				.then(() => assertEquals('pass 5,fulfilled object', log.sort().reverse().join()));
			""");
	}

	// Map/Set: a boxed primitive is an object key, distinct from the primitive
	public void testBoxedKeys() throws Exception {
		executeCode("""
			var m = new Map();
			m.set(1, 'a'); m.set(new Number(1), 'b');
			assertEquals(2, m.size);
			assertEquals('a', m.get(1));
			var n = Object(1);
			m.set(n, 'c');
			assertEquals('c', m.get(n));
			assertEquals(undefined, m.get(Object(1)));
			var s = new Set(['x', new String('x'), true, new Boolean(true), 0, -0, new Number(-0)]);
			assertEquals(6, s.size);
			assertFalse(s.has(new String('x')));
			assertTrue(s.has('x') && s.has(-0) && s.has(0));
			var z = new Number(-0);
			var m2 = new Map([[z, 1], [-0, 2]]);
			assertEquals(2, m2.size);
			assertTrue([...m2.keys()][0] === z);
			assertTrue(Object.is([...m2.keys()][1], 0));
			""");
	}

	// String.prototype.split: a missing separator is undefined (not "null"); a
	// RegExp without @@split is an ordinary object (ToString)
	public void testSplitSeparator() throws Exception {
		executeCode("""
			assertEquals('["anullb"]', JSON.stringify("anullb".split()));
			assertEquals('["anullb"]', JSON.stringify("anullb".split(undefined)));
			assertEquals('[]', JSON.stringify("anullb".split(undefined, 0)));
			assertEquals('["a","b"]', JSON.stringify("anullb".split(null)));
			var re = /1/;
			Object.defineProperty(re, Symbol.split, {value: undefined});
			assertEquals('["a1b"]', JSON.stringify("a1b".split(re)));
			assertEquals('["a","b"]', JSON.stringify("a/1/b".split(re)));
			assertEquals('["a","b"]', JSON.stringify("a1b".split(/1/)));
			""");
	}

	// getTimezoneOffset is the offset in effect at the date's own time value
	public void testTimezoneOffset() throws Exception {
		executeCode("""
			for (var d of [new Date(2020,0,15,12), new Date(2020,6,15,12), new Date(1950,6,1), new Date(2012,5,1), new Date(0)]) {
				var local = Date.UTC(d.getFullYear(), d.getMonth(), d.getDate(), d.getHours(), d.getMinutes(), d.getSeconds(), d.getMilliseconds());
				assertEquals((d.getTime() - local) / 60000, d.getTimezoneOffset());
				assertEquals((new Date(local).getUTCDay()), d.getDay());
			}
			assertTrue(isNaN(new Date(NaN).getTimezoneOffset()));
			assertTrue(isNaN(new Date(NaN).getDay()));
			assertEquals(4, new Date(0).getUTCDay());
			assertEquals(3, new Date(-1).getUTCDay());
			assertEquals(-271821, new Date(-8.64e15).getUTCFullYear());
			""");
	}

	// A rejection without a handler passes the reason through unchanged
	public void testPromiseRejectionReason() throws Exception {
		executeCode("""
			var ISE = Java.type('java.lang.IllegalStateException');
			var ex = new ISE('boom');
			var seen;
			Promise.reject(ex).then(() => 0).catch(r => { seen = r; });
			var thrown = {};
			var seen2;
			Promise.resolve().then(() => { throw thrown; }).then(() => 0).catch(r => { seen2 = r; });
			Promise.resolve().then(()=>0).then(()=>0).then(()=>0).then(()=>0).then(() => {
				assertTrue(seen === ex);
				assertTrue(seen2 === thrown);
			});
			""");
	}

	// map/filter: IsCallable(callbackfn) is checked before ArraySpeciesCreate
	public void testSpeciesAfterCallableCheck() throws Exception {
		executeCode("""
			var called = 0;
			var a = [1];
			a.constructor = {};
			a.constructor[Symbol.species] = function() { called++; return []; };
			assertThrows(TypeError, () => a.map(null));
			assertThrows(TypeError, () => a.filter(null));
			assertEquals(0, called);
			assertEquals('2', a.map(x => x*2).join());
			assertEquals(1, called);
			""");
	}

	// at() with infinite and huge relative indexes on a huge array-like
	public void testAtRelativeIndex() throws Exception {
		executeCode("""
			var o = {length: 2**53-1, 0: 'first'};
			o[2**53-2] = 'last';
			var at = Array.prototype.at;
			assertEquals(undefined, at.call(o, -Infinity));
			assertEquals(undefined, at.call(o, Infinity));
			assertEquals(undefined, at.call(o, -(2**53)));
			assertEquals('last', at.call(o, -1));
			assertEquals('first', at.call(o, -(2**53-1)));
			assertEquals(undefined, new Int8Array(3).at(-Infinity));
			assertEquals(undefined, "abc".at(Infinity));
			assertEquals('a', "abc".at(-3));
			""");
	}

	// RegExp source: "/" after an escaped backslash is escaped
	public void testRegExpSourceEscaping() throws Exception {
		executeCode("""
			assertEquals('\\\\\\\\\\\\/', new RegExp('\\\\\\\\/').source);
			assertEquals('\\\\\\\\\\\\/', new RegExp('\\\\\\\\\\\\/').source);
			assertEquals('a\\\\/b', new RegExp('a/b').source);
			assertEquals('\\\\n', new RegExp('\\n').source);
			assertEquals('(?:)', new RegExp('').source);
			var re = new RegExp('\\\\\\\\/');
			assertTrue(eval('/' + re.source + '/').test('\\\\/'));
			""");
	}

	// join/toString/toLocaleString on a cyclic array
	public void testCyclicJoin() throws Exception {
		executeCode("""
			var a = [1]; a.push(a);
			assertEquals('1,', a.join());
			assertEquals('1,', String(a));
			assertEquals('1,', a.toLocaleString());
			var b = [2, [3, a]];
			a.push(b);
			assertEquals('1,,2,3,', a.join());
			assertEquals('1-', [1, a].join('-').substring(0, 2));
			""");
	}

	// RegExp.prototype.test fast path keeps lastIndex and the legacy statics
	public void testRegExpTest() throws Exception {
		executeCode("""
			var re = /b(c)/g;
			assertTrue(re.test('abcbc'));
			assertEquals(3, re.lastIndex);
			assertEquals('c', RegExp.$1);
			assertTrue(re.test('abcbc'));
			assertEquals(5, re.lastIndex);
			assertFalse(re.test('abcbc'));
			assertEquals(0, re.lastIndex);
			var r2 = /x/;
			r2.exec = function() { return {}; };
			assertTrue(r2.test('nothing'));
			""");
	}

	// Callbacks are called with the spec's this value and arguments
	public void testCallbackArguments() throws Exception {
		executeCode("""
			var self = [];
			Object.groupBy([1], function() { 'use strict'; self.push(this); return 'k'; });
			Map.groupBy([1], function() { 'use strict'; self.push(this); return 'k'; });
			new Int8Array([1,2]).reduce(function() { 'use strict'; self.push(this); return 0; });
			[1,2].reduce(function() { 'use strict'; self.push(this); return 0; });
			assertEquals(4, self.length);
			assertTrue(self.every(t => t === undefined));
			var seen = [];
			[5].forEach(function(v, i, a) { seen.push(v, i, a.length, arguments.length); });
			new Map([[1,2]]).forEach(function(v, k, m) { seen.push(v, k, m.size); });
			new Set([7]).forEach(function(v, k) { seen.push(v, k); });
			Array.from([9], function(v, i) { seen.push(v, i, arguments.length); });
			assertEquals('5,0,1,3,2,1,1,7,7,9,0,2', seen.join());
			""");
	}

	// The NativeError constructors share one implementation
	public void testNativeErrors() throws Exception {
		executeCode("""
			for (var E of [EvalError, RangeError, ReferenceError, SyntaxError, TypeError, URIError]) {
				var e = new E('m', {cause: 'c'});
				assertEquals(E.name, e.name);
				assertEquals('m', e.message);
				assertEquals('c', e.cause);
				assertTrue(e instanceof E && e instanceof Error);
				assertTrue(Object.getPrototypeOf(E) === Error);
				assertTrue(Object.getPrototypeOf(E.prototype) === Error.prototype);
				assertEquals('', E.prototype.message);
				assertFalse(Object.hasOwn(E(), 'message'));
				assertEquals(E.name + ': x', String(E('x')));
				class Sub extends E {}
				assertTrue(new Sub('s') instanceof Sub);
				assertEquals(1, E.length);
			}
			try { null.x; } catch(e) { assertTrue(e instanceof TypeError); }
			try { undefinedVariable; } catch(e) { assertTrue(e instanceof ReferenceError); }
			""");
	}

	// JSON.stringify: the values JSON cannot represent
	public void testJsonNonJsonValues() throws Exception {
		executeCode("""
			assertEquals('{"a":1}', JSON.stringify({a:1, u:undefined, f(){}, s:Symbol(), n:undefined}));
			assertEquals('[null,null,null,null,null,1.5]', JSON.stringify([undefined, ()=>0, Symbol(), NaN, -Infinity, 1.5]));
			assertEquals(undefined, JSON.stringify(undefined));
			assertEquals(undefined, JSON.stringify(() => 0));
			assertEquals('null', JSON.stringify(NaN));
			assertEquals('{}', JSON.stringify(Object(Symbol())));
			assertEquals('[1]', JSON.stringify(new Proxy([1], {})));
			assertEquals('{"a":2}', JSON.stringify(new Proxy({a:2}, {})));
			assertEquals('{"x":"y"}', JSON.stringify({x: {toJSON() { return 'y'; }}}));
			assertEquals('[1,null]', JSON.stringify([1, 2], (k, v) => v === 2 ? undefined : v));
			Number.prototype.toJSON = function() { return 'boxed'; };
			try {
				// toJSON is looked up on objects and BigInts only, not on a number
				assertEquals('[1,1.5,"boxed"]', JSON.stringify([1, 1.5, new Number(2)]));
			} finally {
				delete Number.prototype.toJSON;
			}
			""");
	}

	// The compiled-regexp cache stays correct past its bound
	public void testManyRegExps() throws Exception {
		executeCode("""
			for (var i = 0; i < 1200; i++) {
				var re = new RegExp('^a' + i + '$');
				assertTrue(re.test('a' + i));
				assertFalse(re.test('a' + i + 'x'));
			}
			assertTrue(/^a7$/.test('a7'));
			""");
	}

	// TypedArray set/slice of the same element type copy the bytes; default sort
	public void testTypedArrayCopies() throws Exception {
		executeCode("""
			var f = new Float64Array([1, -0, NaN, -Infinity, 0, 3]);
			f.sort();
			assertEquals('-Infinity,0,0,1,3,NaN', Array.from(f).join());
			assertTrue(Object.is(f[1], -0) && Object.is(f[2], 0));
			var u = new Uint32Array([4294967295, 0, 7]); u.sort();
			assertEquals('0,7,4294967295', u.join());
			var b = new BigInt64Array([3n, -1n, 2n]); b.sort();
			assertEquals('-1,2,3', b.join());
			// Overlapping views of the same buffer
			var buf = new ArrayBuffer(16);
			var all = new Int16Array(buf);
			for (var i = 0; i < 8; i++) all[i] = i;
			all.set(all.subarray(0, 6), 2);
			assertEquals('0,1,0,1,2,3,4,5', all.join());
			all.set(all.subarray(2, 8), 0);
			assertEquals('0,1,2,3,4,5,4,5', all.join());
			// Different element types convert
			var i8 = new Int8Array(3); i8.set(new Float32Array([1.5, -2.5, 300]));
			assertEquals('1,-2,44', i8.join());
			var s = new Int16Array([1,2,3,4,5]).slice(1, 4);
			assertEquals('2,3,4', s.join());
			assertEquals('', new Int16Array([1]).slice(1).join());
			assertEquals('2,3', new Uint8Array([1,2,3]).slice(-2).join());
			assertEquals('1,2', Array.from(new Float32Array([1,2,3]).slice(0, 2)).join());
			""");
	}
}
