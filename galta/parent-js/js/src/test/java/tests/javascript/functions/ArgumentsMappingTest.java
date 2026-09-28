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
package tests.javascript.functions;

import tests.javascript.JavaScriptNoStrictTestCase;

/**
 * Mapped arguments object (spec 9.4.4): for a non-strict function with a simple parameter
 * list, {@code arguments[i]} and the corresponding named parameter refer to the same
 * binding in both directions, until the index is redefined with an accessor or
 * {@code writable:false}.
 *
 * Only implemented in interpreted mode -- the transpiler always creates unmapped arguments.
 *
 * @author Philippe Riand
 */
public class ArgumentsMappingTest extends JavaScriptNoStrictTestCase {

	public void testParameterWriteIsVisibleThroughArguments() throws Exception {
		if(isJavaTranspiler()) {
			return;
		}
		assertEquals(7, executeCode("function f(a) { a = 7; return arguments[0]; } f(1);"));
	}

	public void testArgumentsWriteIsVisibleThroughParameter() throws Exception {
		if(isJavaTranspiler()) {
			return;
		}
		assertEquals(9, executeCode("function f(a) { arguments[0] = 9; return a; } f(1);"));
	}

	public void testMappingSeveredByNonWritableRedefine() throws Exception {
		if(isJavaTranspiler()) {
			return;
		}
		// Once "0" is redefined with writable:false, the mapping is disconnected: further
		// parameter writes no longer show up through arguments[0].
		assertEquals(1, executeCode(
			"function f(a) {" +
			"  Object.defineProperty(arguments, '0', {writable:false});" +
			"  a = 2;" +
			"  return arguments[0];" +
			"}" +
			"f(1);"));
	}

	public void testMappingNotAppliedInStrictFunction() throws Exception {
		if(isJavaTranspiler()) {
			return;
		}
		// A function with its own "use strict" directive never gets mapped arguments,
		// even when the surrounding script/environment is non-strict.
		assertEquals(1, executeCode(
			"function f(a) {" +
			"  'use strict';" +
			"  a = 2;" +
			"  return arguments[0];" +
			"}" +
			"f(1);"));
	}

	public void testMappingNotAppliedForNonSimpleParameterList() throws Exception {
		if(isJavaTranspiler()) {
			return;
		}
		// Default parameters make the parameter list non-simple, so no mapping occurs
		// even in a non-strict function.
		assertEquals(1, executeCode(
			"function f(a = 0) {" +
			"  a = 2;" +
			"  return arguments[0];" +
			"}" +
			"f(1);"));
	}
}
