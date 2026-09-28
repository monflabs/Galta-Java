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
package tests.javascript.op.assign;

import tests.javascript.JavaScriptStrictTestCase;

/**
 * Array (and nested object-property-value) destructuring must follow the spec's
 * iterator protocol (GetIterator / IteratorStep / IteratorClose) rather than
 * array-like/positional access: elisions still advance the iterator, a pattern
 * that doesn't fully consume the iterator must close it, and a rest element
 * that drains it must not.
 *
 * Only implemented in interpreted mode -- the transpiler's destructuring runtime
 * helper (VarAccessor.destruct) still uses positional access.
 *
 * @author Philippe Riand
 */
public class DestructuredIteratorProtocolTest extends JavaScriptStrictTestCase {

	private static final String ITERABLE =
		"function makeIterable(values) {" +
		"  let nextCalls = 0, returnCalls = 0;" +
		"  const it = {" +
		"    [Symbol.iterator]() { return it; }," +
		"    next() {" +
		"      nextCalls++;" +
		"      if(values.length===0) { return {done:true, value:undefined}; }" +
		"      return {done:false, value:values.shift()};" +
		"    }," +
		"    \"return\": function(v) {" +
		"      returnCalls++;" +
		"      return {done:true, value:v};" +
		"    }," +
		"    calls() { return {next:nextCalls, ret:returnCalls}; }" +
		"  };" +
		"  return it;" +
		"}";

	public void testElisionAdvancesIterator() throws Exception {
		if(isJavaTranspiler()) { return; }
		assertEquals(true, executeCode(ITERABLE +
			"const it = makeIterable([1,2,3]);" +
			"const [,b] = it;" +
			"b===2 && it.calls().next===2;"));
	}

	public void testPartialDestructuringClosesIterator() throws Exception {
		if(isJavaTranspiler()) { return; }
		assertEquals(true, executeCode(ITERABLE +
			"const it = makeIterable([1,2,3]);" +
			"const [a] = it;" +
			"a===1 && it.calls().ret===1;"));
	}

	public void testRestElementDoesNotCloseIterator() throws Exception {
		if(isJavaTranspiler()) { return; }
		assertEquals(true, executeCode(ITERABLE +
			"const it = makeIterable([1,2,3]);" +
			"const [a,...rest] = it;" +
			"a===1 && rest.length===2 && rest[0]===2 && rest[1]===3 && it.calls().ret===0;"));
	}

	public void testEmptyPatternNeverTouchesIterator() throws Exception {
		if(isJavaTranspiler()) { return; }
		assertEquals(true, executeCode(ITERABLE +
			"const it = makeIterable([1,2,3]);" +
			"const [] = it;" +
			"it.calls().next===0 && it.calls().ret===0;"));
	}

	public void testParameterDestructuringClosesIterator() throws Exception {
		if(isJavaTranspiler()) { return; }
		assertEquals(true, executeCode(ITERABLE +
			"const it = makeIterable([1,2,3]);" +
			"function f([a]) { return a; }" +
			"f(it)===1 && it.calls().ret===1;"));
	}

	public void testNestedPatternGetsFreshIterator() throws Exception {
		if(isJavaTranspiler()) { return; }
		assertEquals(true, executeCode(ITERABLE +
			"const it = makeIterable([1,2,3]);" +
			"const [[a]] = [it];" +
			"a===1 && it.calls().ret===1;"));
	}

	public void testNestedPatternWithDefaultInsideObjectPattern() throws Exception {
		if(isJavaTranspiler()) { return; }
		assertEquals(true, executeCode(
			"const {w: [x,y,z] = [4,5,6]} = {};" +
			"x===4 && y===5 && z===6;"));
	}

	public void testNestedObjectPatternWithDefaultTriggeredByExplicitUndefined() throws Exception {
		if(isJavaTranspiler()) { return; }
		assertEquals(true, executeCode(
			"const {w: {x,y,z} = {x:4,y:5,z:6}} = {w: undefined};" +
			"x===4 && y===5 && z===6;"));
	}

	public void testObjectPatternRequiresObjectCoercibleSource() throws Exception {
		if(isJavaTranspiler()) { return; }
		assertEquals(true, executeCode(
			"let threw1=false, threw2=false;" +
			"try { const {} = null; } catch(e) { threw1 = e instanceof TypeError; }" +
			"try { const {a} = undefined; } catch(e) { threw2 = e instanceof TypeError; }" +
			"threw1 && threw2;"));
	}
}
