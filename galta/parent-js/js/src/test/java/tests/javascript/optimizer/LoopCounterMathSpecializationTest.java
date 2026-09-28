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
package tests.javascript.optimizer;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import org.monflabs.galtajs.transpiler.JSTranspilerOptions;

import tests.javascript.JavaScriptStrictTestCase;

/**
 * Exercises ASTFor's loop-counter math specialization
 * (JSTranspilerOptions.isSpecializeLoopCounterMath(), off by default) -
 * forces transpiled execution with the flag on and checks actual runtime
 * behavior, not just generated-code shape, since the whole point of the
 * design is runtime correctness under cases a naive implementation would
 * get wrong (a mutated bound, a counter reassigned to a non-integer).
 *
 * Verifies via captured console.log output rather than executeCode()'s
 * return value - a transpiled top-level script's trailing expression
 * statement doesn't populate the completion value the way interpreted mode
 * does (confirmed by inspecting the generated Java: the last statement is
 * wrapped in the value-discarding statement() helper), so console.log is
 * the reliable cross-mode signal here.
 */
public class LoopCounterMathSpecializationTest extends JavaScriptStrictTestCase {

	@Override
	protected JSTranspilerOptions getTranspilerOptions() {
		return JSTranspilerOptions.newBuilder()
				.debugInformation(true)
				.sourceInCode(true)
				.sourceMap(true)
				.sourceCode(true)
				.specializeLoopCounterMath(true)
				.build();
	}

	private String executeTranspiledAndCapture(String text) throws Exception {
		boolean prev = _EXECUTE_JAVATRANSPILER;
		_EXECUTE_JAVATRANSPILER = true;
		PrintStream originalOut = System.out;
		ByteArrayOutputStream captured = new ByteArrayOutputStream();
		try {
			System.setOut(new PrintStream(captured, true));
			executeCode(text);
		} finally {
			System.setOut(originalOut);
			_EXECUTE_JAVATRANSPILER = prev;
		}
		return captured.toString().trim();
	}

	// Tier A: the counter is used in the body (sum += i), so it stays boxed -
	// exercises the instanceof-guarded fast test/update, not the native-int path.
	public void testCounterUsedInBodySumsCorrectly() throws Exception {
		String out = executeTranspiledAndCapture("""
			var sum = 0;
			for(let i=0;i<10;i++) { sum += i; }
			console.log(sum);
			""");
		assertEquals("45", out);
	}

	// The motivating counter-example from this session: a bound mutated by a
	// function call inside the body must be re-observed every iteration, not
	// snapshotted once - a=6 initially, but f() sets it to 5 on the first
	// iteration, so the loop must run 5 times, not 6.
	public void testMutatedBoundIsReevaluatedEachIteration() throws Exception {
		String out = executeTranspiledAndCapture("""
			var a = 6;
			var count = 0;
			function f() { a = 5; }
			for(let i=0;i<a;i++) {
				count++;
				f();
			}
			console.log(count);
			""");
		assertEquals("5", out);
	}

	// The body reassigns the counter to a non-integer value mid-iteration -
	// the update clause's instanceof check must fall back to the generic
	// incNumber() path gracefully (no ClassCastException), matching exactly
	// what unspecialized codegen would do: "10"++ coerces to the Number 11,
	// which then fails the loop's own test (11<5) and stops.
	public void testCounterReassignedToNonIntegerFallsBackGracefully() throws Exception {
		String out = executeTranspiledAndCapture("""
			var out = [];
			for(let i=0;i<5;i++) {
				out.push(i);
				if(i===2) { i = "10"; }
			}
			console.log(out.length);
			""");
		assertEquals("3", out);
	}

	public void testDecrementingCounter() throws Exception {
		String out = executeTranspiledAndCapture("""
			var count = 0;
			for(let i=10;i>0;i--) { count++; }
			console.log(count);
			""");
		assertEquals("10", out);
	}

	public void testSteppedCounter() throws Exception {
		String out = executeTranspiledAndCapture("""
			var count = 0;
			for(let i=0;i<10;i+=3) { count++; }
			console.log(count);
			""");
		assertEquals("4", out);
	}

	// Tier B: the counter is never referenced anywhere in the body - eligible
	// for a genuine native int with no boxed representation involved at all.
	public void testCounterNeverReferencedInBodyTierB() throws Exception {
		String out = executeTranspiledAndCapture("""
			var count = 0;
			for(let i=0;i<1000;i++) { count++; }
			console.log(count);
			""");
		assertEquals("1000", out);
	}

	// A boxed `new Number(3)` is ALSO `instanceof Integer` at the Java level
	// (RuntimeUtil.primitiveAsObject boxes via `new Integer(...)` for a
	// distinguishable identity) but is a genuine JS OBJECT, not a plain
	// number - if its own valueOf is overridden, comparisons/arithmetic must
	// go through it (ToPrimitive), not shortcut straight to the boxed
	// int - `instanceof Integer` alone can't tell the two apart, only
	// env.getNumberProperties()==null (absent here) can. Without that
	// check, this bound would incorrectly extract 3 (the boxed value)
	// instead of calling the overridden valueOf (99), stopping the loop
	// after 3 iterations instead of 99.
	public void testBoxedNumberBoundWithOverriddenValueOf() throws Exception {
		String out = executeTranspiledAndCapture("""
			var count = 0;
			var bound = new Number(3);
			bound.valueOf = function() { return 99; };
			for(let i=0;i<bound;i++) { count++; }
			console.log(count);
			""");
		assertEquals("99", out);
	}
}
