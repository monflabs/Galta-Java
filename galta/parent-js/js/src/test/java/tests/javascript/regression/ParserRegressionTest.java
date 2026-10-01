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

import tests.javascript.JavaScriptNoStrictTestCase;

/**
 * Parser and AST node regressions, run in every execution mode through
 * GaltaJSTestSuite (plain ECMAScript environment).
 */
public class ParserRegressionTest extends JavaScriptNoStrictTestCase {

	// A regular expression literal right after the ")" of an if/while/for/with head
	public void testRegExpAfterStatementHead() throws Exception {
		executeCode("""
			var s = "xa", n = 0;
			if (true) /a/.test(s) && n++;
			if (false) ; else /a/.test(s) && n++;
			while (n < 3) /a/g.test(s) && n++;
			for (var i = 0; i < 1; i++) /a/.test(s) && n++;
			for (var k in {p:1}) /a/.test(s) && n++;
			for (var v of [1]) /a/.test(s) && n++;
			with ({}) /a/.test(s) && n++;
			assertEquals(7, n);
			var x = 6, y = (x) / 2 / 3;
			assertEquals(1, y);
			""");
	}

	// A comment does not change how a following "/" is read
	public void testCommentBeforeSlash() throws Exception {
		executeCode("""
			var x = 4;
			var y = x /* c */ / 2;
			assertEquals(2, y);
			var z = x // c
			  / 2;
			assertEquals(2, z);
			var w = (x) /**/ / 2;
			assertEquals(2, w);
			var n = 0;
			if (x) /* c */ /a/.test("a") && n++;
			{} // c
			/a/.test("a") && n++;
			assertEquals(2, n);
			""");
	}

	// A class static block followed by another element on the same line
	public void testStaticBlockOnOneLine() throws Exception {
		executeCode("""
			class C { static { this.s = 1 } m(){ return 5 } static {} ; n(){ return 6 } }
			assertEquals(5, new C().m());
			assertEquals(6, new C().n());
			assertEquals(1, C.s);
			""");
	}

	// Private methods/accessors belong to each evaluation of the class
	public void testPrivateMethodsPerClassEvaluation() throws Exception {
		executeCode("""
			function mk(x) {
				return class {
					#m() { return x }
					get #a() { return x * 10 }
					set #a(v) { this.v = v + x }
					g() { this.#a = 100; return this.#m() + this.#a + this.v }
				}
			}
			var A = mk(1);
			var B = mk(2);
			assertEquals(1 + 10 + 101, new A().g());
			assertEquals(2 + 20 + 102, new B().g());
			assertEquals(1 + 10 + 101, new A().g());
			""");
	}

	// A pattern parameter's default applies to an explicit undefined argument
	public void testPatternParameterDefault() throws Exception {
		executeCode("""
			assertEquals(2, (function({a} = {a:2}) { return a })(undefined));
			assertEquals(3, (function([a] = [3]) { return a })(undefined));
			assertEquals(4, (function({a} = {a:2}) { return a })({a:4}));
			assertEquals(5, (function(x, {a} = {a:5}) { return a })(1, undefined));
			""");
	}

	// Logical assignment only writes when it does not short-circuit
	public void testLogicalAssignment() throws Exception {
		executeCode("""
			var y = 0; y &&= 1; assertEquals(0, y);
			var z = 1; z ||= 2; assertEquals(1, z);
			const c = 0; assertEquals(0, c &&= 1);
			const d = 1; assertEquals(1, d ||= 2);
			const e = 1; assertEquals(1, e ??= 3);
			let n = null; n ??= 4; assertEquals(4, n);
			let u; u ||= 5; assertEquals(5, u);
			var w = 1; w &&= 6; assertEquals(6, w);
			assertThrows(TypeError, () => { const f = 1; f &&= 2; });
			assertThrows(TypeError, () => { const f = null; f ??= 2; });
			var log = [];
			var o = { get p() { log.push("get"); return 1 }, set p(v) { log.push("set") } };
			with (o) { p ||= 2; }
			assertEquals("get", log.join());
			assertThrows(ReferenceError, () => { undeclaredLogical &&= 1 });
			""");
	}

	// Assigning to a const is a runtime TypeError, not a parse-time error
	public void testConstAssignment() throws Exception {
		executeCode("""
			const a = 1;
			assertThrows(TypeError, () => { a = 2 });
			var r; try { a = 2 } catch(e) { r = e instanceof TypeError } assertTrue(r);
			assertThrows(TypeError, () => { a += 1 });
			assertThrows(TypeError, () => { a++ });
			assertThrows(TypeError, () => { --a });
			assertEquals(1, a);
			var evaluated = false;
			assertThrows(TypeError, () => { a = (evaluated = true) });
			assertTrue(evaluated);
			""");
	}

	// super/new.target in a class element evaluated by eval
	public void testEvalSuperInClassElements() throws Exception {
		executeCode("""
			var C = eval("(class extends Object { x = super.toString; static y = new.target; static { this.z = super.constructor } })");
			assertEquals(Object.prototype.toString, new C().x);
			assertEquals(undefined, C.y);
			assertEquals(Function, C.z);
			assertThrows(SyntaxError, () => eval("super.x"));
			assertThrows(SyntaxError, () => eval("new.target"));
			function f() { return eval("new.target") }
			assertEquals(undefined, f());
			class D { m() { return eval("super.toString") } }
			assertEquals(Object.prototype.toString, new D().m());
			class E { x = eval("arguments") }
			assertThrows(SyntaxError, () => new E());
			""");
	}

	// Error messages quote the name once
	public void testMessageQuotes() throws Exception {
		executeCode("""
			var m; try { eval("class C { m() { this.#y } }") } catch(e) { m = e.message }
			assertTrue(m.startsWith("Private field '#y' must"));
			let zz = 1;
			var m2; try { (0,eval)("var zz") } catch(e) { m2 = e.message }
			assertEquals("Identifier 'zz' has already been declared", m2);
			""");
	}

	// A multi-line comment containing a line terminator is a line terminator
	public void testMultiLineCommentLineTerminator() throws Exception {
		executeCode("""
			function f() { return /*
			*/ 1 }
			assertEquals(undefined, f());
			function g() { return /* */ 1 }
			assertEquals(1, g());
			var i = 0;
			l: for(;;) { i++; break /*
			*/ l; }
			assertEquals(1, i);
			""");
	}

	// "#x in o" is a RelationalExpression; "static" is a valid label
	public void testPrivateInAndIdentifierLikeLabels() throws Exception {
		executeCode("""
			class C { #x; static t(o, p) { return #x in o in p } static u(o) { return #\\u0078 in o } }
			assertTrue(C.t(new C(), {"true": 1}));
			assertFalse(C.t({}, {"true": 1}));
			assertTrue(C.u(new C()));
			var n = 0;
			static: for(;;) { n++; break static; }
			let: for(;;) { n++; break let; }
			int: for(;;) { n++; break int; }
			assertEquals(3, n);
			""");
	}

	// A rest element whose target is a computed member
	public void testObjectRestToComputedMember() throws Exception {
		executeCode("""
			var t = {}, k = "p";
			({...t[k]} = {a:1});
			assertEquals(1, t.p.a);
			var log = [], o = {};
			function key() { log.push("key"); return "q" }
			var src = { get a() { log.push("src"); return 1 } };
			({...o[key()]} = src);
			assertEquals("key,src", log.join());
			assertEquals(1, o.q.a);
			""");
	}

	// A generator's return() closes a for-of iterator: an error from its return() propagates
	public void testForOfReturnCompletion() throws Exception {
		executeCode("""
			var log = [];
			var iter = { [Symbol.iterator]() { return { next() { return {done:false, value:1} }, return() { log.push("ret"); throw new Error("from return") } } } };
			function* g() { for (var x of iter) { yield x; } }
			var it = g(); it.next();
			var caught; try { it.return(5) } catch(e) { caught = e.message }
			assertEquals("from return", caught);
			var iter2 = { [Symbol.iterator]() { return { next() { return {done:false, value:1} }, return() { log.push("ret2"); return {} } } } };
			function* g2() { for (var x of iter2) { yield x; } }
			var it2 = g2(); it2.next();
			var r = it2.return(7);
			assertEquals(7, r.value);
			assertTrue(r.done);
			assertEquals("ret,ret2", log.join());
			""");
	}

	// Array destructuring: a member target is evaluated before the iterator step and the default
	public void testArrayDestructuringTargetOrder() throws Exception {
		executeCode("""
			var log = [], tgt = {};
			function key() { log.push("key"); return "k" }
			var it = { [Symbol.iterator]() { return { next() { log.push("next"); return {done:false, value:undefined} }, return() { return {} } } } };
			[tgt[key()] = (log.push("init"), 5)] = it;
			assertEquals("key,next,init", log.join());
			assertEquals(5, tgt.k);
			log = [];
			var obj = { get o() { log.push("obj"); return tgt } };
			[obj.o.m = (log.push("init"), 6)] = it;
			assertEquals("obj,next,init", log.join());
			assertEquals(6, tgt.m);
			""");
	}

	// Lexer: "\\v" in the source is not white space; the members' positions
	public void testLexer() throws Exception {
		executeCode("""
			var a = 1, b = 2;
			assertThrows(SyntaxError, () => eval("a \\\\v + b"));
			assertEquals(3, eval("a \\u000b+ b"));
			assertEquals(3, eval("var a\\u200c\\u200d = 3; a\\u200c\\u200d"));
			var o = { b: { c: 7 } };
			function F() { this.c = 8 }
			o.F = F;
			assertEquals(8, new o.F().c);
			assertEquals(8, new o.\\u0046().c);
			""");
	}

	// synchronized() requires an object
	public void testSynchronized() throws Exception {
		executeCode("""
			assertThrows(TypeError, () => { synchronized(null) {} });
			assertThrows(TypeError, () => { synchronized(undefined) {} });
			assertThrows(TypeError, () => { synchronized("s") {} });
			assertThrows(TypeError, () => { synchronized(1) {} });
			var o = {}, v = 0;
			synchronized(o) { v = 1 }
			assertEquals(1, v);
			""");
	}
}
