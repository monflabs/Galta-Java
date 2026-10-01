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
package tests.util;

import org.monflabs.util.StringFormat;

import tests.ProjectTestCase;

public class StringFormatTest extends ProjectTestCase {
	
	public void testEmptyMustaches() throws Exception {
//		assertEquals( "A  B", StringFormat.format("A {} B") );
		assertEquals( "A C and {} B", StringFormat.format("A {0} and {} B", "C") );
	}

	public void testMissingParameterKept() throws Exception {
		// A placeholder without a parameter used to vanish
		assertEquals("Hello {1}!", StringFormat.format("Hello {1}!", "a"));
		assertEquals("a {0} {1}", StringFormat.format("a {0} {1}"));
		assertEquals("a {0}", StringFormat.format("a {0}", (Object[])null));
		assertEquals("x {2} y", StringFormat.format("{0} {2} {1}", "x", "y"));
	}

	public void testUnterminatedPlaceholder() throws Exception {
		// Used to substitute the parameter although the '}' was missing
		assertEquals( "a{0", StringFormat.format("a{0", "X") );
		assertEquals( "a{12", StringFormat.format("a{12", "X") );
		assertEquals( "aX", StringFormat.format("a{0}", "X") );
	}

	public void testHugePlaceholderIndex() throws Exception {
		// The index used to wrap around: {4294967296} became {0}, {2147483648} threw
		assertEquals("x {4294967296} y", StringFormat.format("x {4294967296} y", "a"));
		assertEquals("x {2147483648} y", StringFormat.format("x {2147483648} y", "a"));
		assertEquals("x {99999999999999999999} y", StringFormat.format("x {99999999999999999999} y", "a"));
		assertEquals("a b", StringFormat.format("{0} {1}", "a", "b"));
		assertEquals("b", StringFormat.format("{01}", "a", "b"));   // leading zeros are fine
	}
}
