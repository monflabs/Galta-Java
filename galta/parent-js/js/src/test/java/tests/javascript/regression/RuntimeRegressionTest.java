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
 * Runtime regressions (conversions, operators, collections), run in every
 * execution mode through GaltaJSTestSuite.
 */
public class RuntimeRegressionTest extends JavaScriptStrictTestCase {

	// Map/Set iterators: abandoned or concurrent iterators don't interfere,
	// entries added after a removal are still visited
	public void testCollectionIterators() throws Exception {
		executeCode("""
			var m = new Map([[1,'a'],[2,'b'],[3,'c']]);
			var abandoned = m.keys(); abandoned.next();
			m.delete(2);
			var it1 = m.keys(), it2 = m.keys();
			assertEquals(1, it1.next().value);
			assertEquals('1,3', [...it2].join());
			m.delete(3); m.set(4,'d');
			assertEquals(4, it1.next().value);
			assertTrue(it1.next().done);
			m.set(5,'e');
			assertTrue(it1.next().done); // done is final

			var s = new Set([1,2,3]); var si = s.values(); si.next();
			s.delete(1); s.delete(2); s.add(5);
			assertEquals('3,5', [...si].join());

			var c = new Map([[1,1],[2,2]]); var ci = c.keys(); ci.next();
			c.clear(); c.set(9,9);
			assertEquals(9, ci.next().value);

			// Deleting while iterating, and many abandoned iterators
			var d = new Map(); for(var i=0;i<100;i++) d.set(i,i);
			for(var i=0;i<50;i++) { var x = d.keys(); x.next(); }
			var seen = [];
			for(var k of d.keys()) { seen.push(k); d.delete(k+1); }
			assertEquals(50, seen.length);
			assertEquals(98, seen[49]);
			assertEquals(50, d.size);
			""");
	}

	// splice()/shift() over leading holes move the stored elements down
	public void testRemoveLeadingHoles() throws Exception {
		executeCode("""
			var a = []; a[5] = 'x';
			a.splice(0, 2);
			assertEquals(4, a.length);
			assertEquals('x', a[3]);
			assertTrue(3 in a);
			assertFalse(5 in a);
			var b = []; b[3] = 'y'; b.shift();
			assertEquals(3, b.length);
			assertEquals('y', b[2]);
			""");
	}

	// Boxed primitives with an overridden valueOf/toString returning another type
	public void testBoxedPrimitiveConversions() throws Exception {
		executeCode("""
			var n = new Number(5); n.valueOf = () => "7";
			assertEquals(7, +n);
			assertEquals(14, n * 2);
			var s = new String("ab"); s.toString = () => 42;
			assertEquals('42', `${s}`);
			assertEquals('zz', String(new String("zz")));
			var sym = Object(Symbol('q'));
			assertEquals('object', typeof sym);
			assertEquals('Symbol(q)', sym.toString());
			assertEquals('symbol', typeof sym.valueOf());
			assertThrows(TypeError, () => String(sym)); // ToPrimitive gives the symbol
			""");
	}

	// A boxed NaN is an object, equal to itself; string to number conversions
	public void testEqualityAndStringToNumber() throws Exception {
		executeCode("""
			var n = new Number(NaN);
			assertTrue(n == n);
			assertTrue(n === n);
			var nan = NaN;
			assertFalse(nan == nan);
			assertTrue(isNaN('abc' * 1));
			assertTrue(isNaN('-x' * 1));
			assertTrue(isNaN('+' * 1));
			assertEquals(12, ' 12 ' * 1);
			assertEquals(-0.5, '-.5' * 1);
			assertEquals(Infinity, 'Infinity' * 1);
			assertEquals(255, '0xff' * 1);
			assertEquals(0, '  ' * 1);
			""");
	}

	// Shifts by NaN: the count is 0; BigInt and Number cannot be mixed
	public void testShiftByNaN() throws Exception {
		executeCode("""
			var nan = NaN;
			assertEquals(4294967295, -1 >>> nan);
			assertEquals(5, 5 >> nan);
			assertEquals(5, 5 << nan);
			assertEquals(0, nan >>> 1);
			var big = 5n;
			assertThrows(TypeError, () => big >>> nan);
			assertThrows(TypeError, () => big >> nan);
			assertThrows(TypeError, () => big << nan);
			""");
	}

	// Private field postfix ++/-- on a BigInt
	public void testPrivateBigIntUpdate() throws Exception {
		executeCode("""
			class C { #x = 5n; inc() { return this.#x++; } dec() { return this.#x--; } get x() { return this.#x; } }
			var c = new C();
			assertTrue(c.inc() === 5n);
			assertTrue(c.x === 6n);
			assertTrue(c.dec() === 6n);
			assertTrue(c.x === 5n);
			""");
	}

	// A compound assignment to a private field reads the field before the
	// right side runs; ??= assigns over undefined (both execution modes)
	public void testPrivateCompoundOrderAndNullishAssign() throws Exception {
		executeCode("""
			class P {
				#x = 5;
				run() { const r = (this.#x += (this.#x = 10, 1)); return [r, this.#x].join(); }
			}
			assertEquals('6,6', new P().run());
			globalThis.nullishTarget = undefined;
			nullishTarget ??= 5;
			assertEquals(5, globalThis.nullishTarget);
			""");
	}
}
