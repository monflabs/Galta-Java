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
package tests.javascript.compiler;

import static org.junit.Assert.assertThrows;

import org.monflabs.galtajs.JSParseException;
import org.monflabs.galtajs.modules.JSInterpretedUnit;
import org.monflabs.galtajs.util.JavaBuilder;

import tests.javascript.JavaScriptStrictTestCase;

/**
 * @author Philippe Riand
 */
public class ShebangTest extends JavaScriptStrictTestCase {

	public void testShebang() throws Exception {
		assertEquals("", processedCode("#!") );
		assertEquals("", processedCode("#!/usr/bin/node") );
		assertEquals("", processedCode("#!/usr/bin/node\n") );
		assertEquals("", processedCode("#!/usr/bin/node option") );

		assertEquals("1 ;", processedCode("#!/usr/bin/node\n1") );
		assertEquals("2 ;", processedCode("#!/usr/bin/node\r2") );
		assertEquals("3 ;", processedCode("#!/usr/bin/node\r\n3") );
		assertEquals("4 ;", processedCode("#!/usr/bin/node\n\r4") );
		// Line separator (U+2028) and paragraph separator (U+2029) are also
		// LineTerminators, so they end the hashbang comment too.
		assertEquals("5 ;", processedCode("#!/usr/bin/node\u20285") );
		assertEquals("6 ;", processedCode("#!/usr/bin/node\u20296") );

		assertThrows(JSParseException.class, () -> processedCode("\n#!/usr/bin/node\n0") );
		assertThrows(JSParseException.class, () -> processedCode("1\n#!/usr/bin/node\n0") );
	}		
	
	private String processedCode(String code) {
		JSInterpretedUnit unit = getEnvironment().createScript(code, "memory.js");
		JavaBuilder b = new JavaBuilder() {
			@Override
			public void comment(String comment, Object... params) {}
			@Override
			public void commentMulti(String comment, Object... params) {}
		};
		return unit.getProgram().decompile(b).trim();
	}
}
