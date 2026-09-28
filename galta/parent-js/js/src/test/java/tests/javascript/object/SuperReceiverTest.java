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
package tests.javascript.object;

import tests.javascript.JavaScriptStrictTestCase;

/**
 * SuperProperty's receiver semantics: an inherited accessor invoked via
 * super.prop (or super[expr]) must run with `this` bound to the current
 * `this`, not the lookup base; a plain assignment to a property that doesn't
 * exist anywhere on the prototype chain creates it on the current `this`.
 *
 * Only implemented in interpreted mode -- the transpiler's super-property
 * runtime helpers don't yet thread a separate receiver through.
 *
 * @author Philippe Riand
 */
public class SuperReceiverTest extends JavaScriptStrictTestCase {

	public void testAccessorThisBinding() throws Exception {
		if(isJavaTranspiler()) {
			return;
		}
		assertEquals(true, executeCode(
			"const parent = {" +
			"  get viaDot() { return this; }," +
			"  get ['viaExpr']() { return this; }," +
			"  set viaDotSet(v) { this.captured = v; }," +
			"  set ['viaExprSet'](v) { this.capturedExpr = v; }," +
			"};" +
			"const child = {" +
			"  method() { return [super.viaDot, super['viaExpr']]; }," +
			"  setMethod(v) { super.viaDotSet = v; super['viaExprSet'] = v + 1; }," +
			"};" +
			"Object.setPrototypeOf(child, parent);" +
			"const [a, b] = child.method();" +
			"child.setMethod(10);" +
			"a === child && b === child && child.captured === 10 && child.capturedExpr === 11 && parent.captured === undefined;"));
	}

	public void testAssignmentCreatesOnReceiver() throws Exception {
		if(isJavaTranspiler()) {
			return;
		}
		assertEquals(true, executeCode(
			"const parent = {};" +
			"const child = {" +
			"  method() { super.freshDot = 1; super['freshExpr'] = 2; }," +
			"};" +
			"Object.setPrototypeOf(child, parent);" +
			"child.method();" +
			"Object.prototype.hasOwnProperty.call(child,'freshDot') && Object.prototype.hasOwnProperty.call(child,'freshExpr') " +
			"&& child.freshDot === 1 && child.freshExpr === 2 " +
			"&& !Object.prototype.hasOwnProperty.call(parent,'freshDot') && !Object.prototype.hasOwnProperty.call(parent,'freshExpr');"));
	}

	// Arrow functions have no [[HomeObject]] of their own: super.prop inside an
	// arrow must resolve against the nearest enclosing non-arrow function's home
	// object, not throw.
	//
	// (super() through an arrow at the top of a constructor is a separate,
	// still-open gap: BuiltinFunctionInterpreter.call() eagerly resolves an
	// arrow's `this` from its enclosing context before the arrow's body runs,
	// which fails when that enclosing constructor hasn't called super() yet -
	// see language/expressions/arrow-function/lexical-supercall-from-immediately-invoked-arrow.js
	// in the test262 known-gaps list.)
	public void testSuperPropertyThroughArrow() throws Exception {
		if(isJavaTranspiler()) {
			return;
		}
		assertEquals(true, executeCode(
			"class A { increment() { return 1; } }\n" +
			"class B extends A {" +
			"  incrementer() { return (_ => super.increment())(); }" +
			"}\n" +
			"new B().incrementer() === 1;"));
	}
}
