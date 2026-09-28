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
package doc_examples;

import java.util.List;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.JSException;
import org.monflabs.galtajs.environments.GaltaJSEnvironment;
import org.monflabs.galtajs.environments.JavaScriptEnvironment;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.tests.__BaseTestCase;

import static doc_examples.DocExampleSupport.*;

/**
 * Samples for docs/GaltaJS/Extensions/Sequences.md
 */
public class SequencesExamples extends __BaseTestCase {

	private final JSEnvironment env = GaltaJSEnvironment.create();

	public void testFlattenAndCollapse() {
		// Without a sequence, + on an array is string concatenation
		assertEquals("2,310", env.evaluateExpression("[2,3] + 10"));
		// .* turns the array into a sequence; operators then apply to every item
		assertEquals(List.of(12, 13), list(env.evaluateExpression("[2,3].* + 10")));
		assertEquals(List.of(12, 13), list(env.evaluateExpression("[2,3][*] + 10")));

		// A sequence collapses when it leaves the expression: 0 items -> undefined, 1 -> the item, more -> an Array
		assertEquals(List.of(1, 3), list(env.evaluateExpression("[{a:1,b:2},{a:3}].*.a")));
		assertEquals(2, (Object)env.evaluateExpression("[{a:1,b:2},{a:3}].*.b"));
		assertSame(RuntimeUtil.UNDEFINED, env.evaluateExpression("[{a:1,b:2},{a:3}].*.c"));
	}

	public void testMemberAccessIsForgiving() {
		// null / undefined items are skipped instead of throwing
		assertEquals(List.of(1, 2), list(env.evaluateExpression("[{a:1}, null, {a:2}].*.a")));
		// A method call applies to each item
		assertEquals(List.of("A", "B"), list(env.evaluateExpression("['a','b'].*.toUpperCase()")));
	}

	public void testMaterializingWithBrackets() {
		// [] always produces an Array with exactly the sequence items
		assertEquals(List.of(), list(env.evaluateExpression("[{a:1}].*.c[]")));
		assertEquals(List.of(1), list(env.evaluateExpression("[{a:1}].*.a[]")));
		assertEquals(1, (Object)env.evaluateExpression("[{a:1},{a:2}].*.a[][0]"));
		// On a plain value it wraps the value
		assertEquals(List.of(1), list(env.evaluateExpression("1[]")));
		assertEquals(List.of("xyz"), list(env.evaluateExpression("'xyz'[]")));
	}

	public void testOperatorBroadcasting() {
		// sequence (op) scalar
		assertEquals(List.of(3, 6, 9), list(env.evaluateExpression("[1,2,3].* * 3")));
		// sequence (op) sequence: pairwise, the last item of the shorter one is reused
		assertEquals(List.of(51, 62, 63, 64), list(env.evaluateExpression("[1,2,3,4].* + [50,60].*")));
		// unary operators
		assertEquals(List.of("number", "string", "boolean"), list(env.evaluateExpression("typeof [1,'s',true].*")));
		// an empty sequence yields undefined
		assertSame(RuntimeUtil.UNDEFINED, env.evaluateExpression("[].* + 1"));
		// a plain Array operand is a scalar
		assertEquals(List.of("11", "21"), list(env.evaluateExpression("[1,2].* + [1]")));
	}

	public void testAnyVersusAllComparisons() {
		// == on a sequence is true if ANY item matches; *== requires ALL of them
		assertEquals(true, (Object)env.evaluateExpression("[10,12].* == 10"));
		assertEquals(false, (Object)env.evaluateExpression("[10,12].* *== 10"));
		assertEquals(true, (Object)env.evaluateExpression("[10,10].* *== 10"));
		assertEquals(true, (Object)env.evaluateExpression("[1,5].* > 4"));
		assertEquals(false, (Object)env.evaluateExpression("[1,5].* *> 4"));
		assertEquals(true, (Object)env.evaluateExpression("'a' in [{a:1},{b:2}].*"));
		assertEquals(false, (Object)env.evaluateExpression("'a' *in [{a:1},{b:2}].*"));
	}

	public void testAssignmentThroughSequences() {
		Object r = env.evaluateScript("""
			const items = [{a: 1}, {a: 1}, {b: 2}];
			items.*.a = 20;       // only the items that have `a`... plus the ones that don't
			items.map(i => i.a)
			""");
		assertEquals(List.of(20, 20, 20), list(r));
		Object r2 = env.evaluateScript("""
			const items = [{a: 1}, {a: 5}];
			items.*.a += 10;
			items.*.a
			""");
		assertEquals(List.of(11, 15), list(r2));
	}

	public void testSequencesMustBeEnabled() {
		try {
			JavaScriptEnvironment.create().evaluateExpression("[{a:1}].*.a");
			fail();
		} catch(JSException e) {
			assertTrue(e.getMessage().contains("requires Galta extensions to be enabled"));
		}
		JSEnvironment seq = JavaScriptEnvironment.newBuilder().supportSequenceExtensions(true).build();
		assertEquals(List.of(2, 3), list(seq.evaluateExpression("[1,2].* + 1")));
	}
}
