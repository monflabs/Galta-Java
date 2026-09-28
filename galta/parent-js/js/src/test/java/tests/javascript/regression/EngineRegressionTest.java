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
 * Engine regressions, run in every execution mode (interpreted, optimized,
 * transpiled, decompiled) through GaltaJSTestSuite.
 */
public class EngineRegressionTest extends JavaScriptStrictTestCase {

	// The completion value of a script is not returned in every execution mode,
	// so the checks are done in JavaScript with assertEquals()

	// UnreachableCodeRemovalOptimizer: a labeled break aimed at the switch itself, or at
	// a labeled block inside the case, does not leave the enclosing function
	public void testLabeledBreakInsideSwitch() throws Exception {
		executeCode("""
			assertEquals('after', (function(){ switch(1){default: L:{break L;}} return 'after'; })());
			assertEquals('after', (function(){ S: switch(1){default: break S;} return 'after'; })());
			assertEquals('after', (function(){ S: switch(1){case 1: if(true) break S; else break S; default: return 'no';} return 'after'; })());
			// A break to a label outside the switch still leaves the flow
			assertEquals('out', (function(){ O: { switch(1){default: break O;} return 'no'; } return 'out'; })());
			""");
	}

	// int/long fast paths: 0 * negative and 0 / negative are -0
	public void testNegativeZeroArithmetic() throws Exception {
		executeCode("""
			var a=0, b=-1;
			assertEquals('-Infinity,-Infinity,-Infinity,-Infinity,Infinity', [1/(a*b), 1/(b*a), 1/(a/b), 1/(-1*0), 1/(a*a)].join());
			var z=0, n=-7;
			assertEquals(-Infinity, 1/(z/n));
			assertEquals(Infinity, 1/(z/7));
			var p=6, q=-3;
			assertEquals(-2, p/q);
			assertEquals(-18, p*q);
			""");
	}

	// ASTStringTemplate: children exist before init() (unbraced for-family body)
	public void testTemplateInUnbracedLoopBody() throws Exception {
		executeCode("""
			var a=[]; for(var i=0;i<2;i++) a.push(`q${i}`);
			assertEquals('q0,q1', a.join());
			var b=[]; for(var v of [1,2]) b.push(`q${v}`);
			assertEquals('q1,q2', b.join());
			var c=[]; for(var k in {x:1}) c.push(`q${k}`);
			assertEquals('qx', c.join());
			var f=[]; for(let j=0;j<2;j++) f.push(() => `${j}`);
			assertEquals('0,1', f.map(g => g()).join());
			""");
	}

	// ASTClassMember: a computed key is part of the tree (parameter use, arguments)
	public void testComputedClassKeys() throws Exception {
		executeCode("""
			function f(a){ class C{ [a](){ return 1; } } return Object.getOwnPropertyNames(C.prototype).join(); }
			assertEquals('constructor,hello', f('hello'));
			function g(){ class C{ static [arguments[0]] = 5 } return C.x; }
			assertEquals(5, g('x'));
			function h(k){ class C{ get [k](){ return 'v'; } } return new C().p; }
			assertEquals('v', h('p'));
			""");
	}

	// Map entries deleted while an iterator is alive must not come back
	public void testMapDeletedKeysStayDeleted() throws Exception {
		executeCode("""
			var m=new Map([['a',1],['b',2],['c',3]]);
			for(var x of m) break;
			m.delete('a');
			for(var i=0;i<100;i++) m.set('k'+i,i);
			assertEquals(false, m.has('a'));
			assertEquals(undefined, m.get('a'));
			assertEquals(102, m.size);
			var m2=new Map([['a',1],['b',2]]);
			var it=m2.keys(); it.next();
			m2.clear(); m2.set('z',1);
			assertEquals(false, m2.has('a'));
			assertEquals(false, m2.has('b'));
			assertEquals(1, m2.size);
			assertEquals('z', [...m2.keys()].join());
			""");
	}

	// The engine uses the realm intrinsics, not the (writable) global bindings
	public void testIntrinsicsNotReadFromGlobal() throws Exception {
		executeCode("""
			var E=Error, T=TypeError, r;
			globalThis.Error=5; globalThis.TypeError=6;
			try { null.x } catch(e) { r = e instanceof T }
			globalThis.Error=E; globalThis.TypeError=T;
			assertEquals(true, r);
			var P=Promise, t;
			delete globalThis.Promise;
			try { t = typeof P.resolve(1).then(x=>x) } finally { globalThis.Promise=P }
			assertEquals('object', t);
			""");
	}
}
