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
 * Engine regressions (second audit round), run in every execution mode
 * through GaltaJSTestSuite.
 */
public class EngineRegression2Test extends JavaScriptStrictTestCase {

	// TypedArray: the byte length is computed in long, an overflowing size is a RangeError
	public void testTypedArrayLengthOverflow() throws Exception {
		executeCode("""
			assertThrows(RangeError, () => new Float64Array(0x20000001));
			assertThrows(RangeError, () => new Int32Array(0x40000001));
			assertEquals(3, new Float64Array(3).length);
			""");
	}

	// Date: ISO years 0-99 are not mapped to 19xx, huge setter values give NaN or the exact value
	public void testDateYearsAndSetters() throws Exception {
		executeCode("""
			assertEquals(50, new Date("0050-06-15T12:00:00").getFullYear());
			assertEquals(1950, new Date(50, 5, 15).getFullYear());
			assertTrue(isNaN(new Date(0).setSeconds(Infinity)));
			assertTrue(isNaN(new Date(0).setUTCMilliseconds(Infinity)));
			var d = new Date(2000,0,1,0,0,0,1e10);
			// 1e10 ms, give or take a daylight saving change - not clamped to 2^31-1
			assertTrue(Math.abs(d.getTime() - new Date(2000,0,1).getTime() - 1e10) <= 3600000);
			var u = new Date(0); u.setUTCMilliseconds(1e10);
			assertEquals(1e10, u.getTime());
			var l = new Date(2020, 1, 29, 10, 20, 30, 400);
			l.setMonth(2); assertEquals(2, l.getMonth()); assertEquals(29, l.getDate());
			l.setHours(25); assertEquals(30, l.getDate()); assertEquals(1, l.getHours());
			l.setFullYear(2021, 0, 31); assertEquals('2021-0-31', l.getFullYear()+'-'+l.getMonth()+'-'+l.getDate());
			var n = new Date(NaN); n.setFullYear(1999); assertEquals(1999, n.getFullYear()); assertEquals(0, n.getHours());
			""");
	}

	// Symbol.for: no fixed cap on the registry
	public void testSymbolRegistry() throws Exception {
		executeCode("""
			var s = [];
			for(var i=0;i<1500;i++) s.push(Symbol.for('k'+i));
			assertTrue(Symbol.for('k7') === s[7]);
			assertEquals('k1499', Symbol.keyFor(s[1499]));
			assertEquals(undefined, Symbol.keyFor(Symbol('x')));
			""");
	}

	// Math precision and overflow
	public void testMath() throws Exception {
		executeCode("""
			assertEquals(2147483648, Math.abs(1<<31));
			assertEquals(-1e300, Math.asinh(-1e300) > 0 ? 0 : -1e300);
			assertTrue(Math.asinh(-1e10) < 0 && isFinite(Math.asinh(-1e10)));
			assertTrue(isFinite(Math.acosh(1e300)));
			assertEquals(1e-20, Math.atanh(1e-20));
			assertEquals(1e-20, Math.asinh(1e-20));
			assertEquals(29, Math.log2(2**29));
			assertEquals(-1074, Math.log2(Number.MIN_VALUE));
			assertEquals(0, Math.asinh(0));
			""");
	}


	// InternalError honours new.target, Number statics reject BigInt, JSON BigInt space
	public void testMiscBuiltins() throws Exception {
		executeCode("""
			if(typeof InternalError === 'function') {
				function O(){}; O.prototype = {};
				assertTrue(Object.getPrototypeOf(Reflect.construct(InternalError,[],O)) === O.prototype);
			}
			assertFalse(Number.isFinite(5n));
			assertFalse(Number.isSafeInteger(5n));
			assertFalse(Number.isInteger(5n));
			assertEquals(1112745, parseInt(null, 36));
			assertEquals('{"a":1}', JSON.stringify({a:1}, null, 3n));
			assertEquals('1', JSON.stringify(JSON.rawJSON({toString(){ return '1'; }})));
			""");
	}

	// Promise: a missing argument is undefined, not null; finally's this is undefined
	public void testPromiseUndefinedArguments() throws Exception {
		executeCode("""
			var log = [];
			new Promise(r => r()).then(v => log.push(typeof v));
			new Promise((r,j) => j()).catch(v => log.push(typeof v));
			Promise.resolve(1).finally(function(){ 'use strict'; log.push(typeof this); });
			Promise.resolve({ then(r){ r(); } }).then(v => log.push(typeof v));
			Promise.resolve().then(()=>0).then(()=>0).then(()=>0).then(()=>0)
				.then(() => assertEquals('undefined,undefined,undefined,undefined', log.join()));
			""");
	}

	// Map/Set: 0n and 0 are different keys, -0 is stored as +0
	public void testKeyedCollections() throws Exception {
		executeCode("""
			assertEquals(2, new Set([0n, 0]).size);
			assertEquals(undefined, new Map([[0n,'a']]).get(0));
			assertEquals('a', new Map([[0n,'a']]).get(0n));
			assertEquals(Infinity, 1/[...new Set().add(-0)][0]);
			var keys = []; new Map().set(-0, 1).forEach((v,k) => keys.push(1/k));
			assertEquals(Infinity, keys[0]);
			var w = new WeakMap(), k = {};
			if(w.getOrInsertComputed) {
				assertEquals(2, w.getOrInsertComputed(k, () => { w.set(k, 1); return 2; }));
				w.delete(k);
				assertFalse(w.has(k));
			}
			""");
	}

	// Proxy descriptors read inherited fields; arguments mapping; unescape; generators
	public void testDescriptorsArgumentsUnescape() throws Exception {
		executeCode("""
			var p = new Proxy({x:5}, { getOwnPropertyDescriptor(t,k){ return Object.create({configurable:true, enumerable:true, writable:true, value:5}); } });
			assertEquals(5, Object.getOwnPropertyDescriptor(p,'x').value);
			// Strict mode: arguments is not mapped to the parameters
			function f(a){ arguments.length = 0; a = 5; return arguments[0]; }
			assertEquals(1, f(1));
			assertEquals('%+1', unescape('%+1'));
			assertEquals('%u-001', unescape('%u-001'));
			assertEquals('A', unescape('%41'));
			async function* ag(){ yield 1; }
			var gp = Object.getPrototypeOf(function*(){}).prototype;
			assertThrows(TypeError, () => gp.next.call(ag()));
			""");
	}

	// Iterator helpers: counts beyond int range don't clamp
	public void testIteratorDropTake() throws Exception {
		executeCode("""
			function* g(){ yield 1; yield 2; yield 3; }
			assertEquals('1,2,3', g().take(Infinity).toArray().join());
			assertEquals('', g().drop(Infinity).toArray().join());
			assertEquals('3', g().drop(2.9).toArray().join());
			assertEquals('', g().drop(2**40).toArray().join());
			""");
	}

	// DisposableStack is emptied once disposed
	public void testDisposableStackDispose() throws Exception {
		executeCode("""
			if(typeof DisposableStack === 'function') {
				var n = 0;
				var s = new DisposableStack();
				s.defer(() => n++);
				s.dispose(); s.dispose();
				assertEquals(1, n);
			}
			""");
	}

	// Spreading null/undefined is a TypeError everywhere
	public void testSpreadNullish() throws Exception {
		executeCode("""
			assertThrows(TypeError, () => Math.max(...null));
			assertThrows(TypeError, () => Math.max(...undefined));
			function F(){}
			assertThrows(TypeError, () => new F(...undefined));
			assertThrows(TypeError, () => [...null]);
			assertThrows(TypeError, () => [1, ...undefined]);
			""");
	}

	// Object literal computed key: ToPropertyKey (a toPrimitive returning a Symbol)
	public void testObjectLiteralSymbolKey() throws Exception {
		executeCode("""
			var s = Symbol('s');
			var k = { [Symbol.toPrimitive](){ return s; } };
			var o = { [k]: 1 };
			assertEquals(1, o[s]);
			var { [k]: v } = o;
			assertEquals(1, v);
			""");
	}

	// Number objects with a valueOf in ++/-- (transpiled fast path)
	public void testIncOnNumberObject() throws Exception {
		executeCode("""
			var n = new Number(1); n.valueOf = () => 10;
			var r = n++;
			assertEquals(10, r);
			assertEquals(11, n);
			var m = new Number(1); m.valueOf = () => 10;
			assertEquals(9, --m);
			""");
	}

	// Exponentiation keeps integers for int/long results
	public void testPower() throws Exception {
		executeCode("""
			assertEquals(1024, 2**10);
			assertEquals(2**40, 1099511627776);
			assertEquals(0.5, 2**-1);
			""");
	}

	// Source text with \n\r line breaks and unicode escapes (transpiled comments)
	public void testUnusualSourceText() throws Exception {
		executeCode("var a=1;\n\rvar b=2;\n\rvar c=3;\nassertEquals(6, a+b+c);\nassertEquals('\\u{1F600}'.length, 2);\n// \\u000a in a comment\n");
	}
}
