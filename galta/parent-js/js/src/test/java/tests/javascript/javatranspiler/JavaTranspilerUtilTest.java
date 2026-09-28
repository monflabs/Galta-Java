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
package tests.javascript.javatranspiler;

import org.monflabs.galtajs.transpiler.JSTranspiler;

import tests.javascript.JavaScriptStrictTestCase;

/**
 * @author Philippe Riand
 */
public class JavaTranspilerUtilTest extends JavaScriptStrictTestCase {
	
	public void testClassName() throws Exception {
		assertEquals( "", JSTranspiler.moduleNameToJavaClassName("","") );
		
		assertEquals( "Ab", JSTranspiler.moduleNameToJavaClassName("","ab") );
		assertEquals( "a.B", JSTranspiler.moduleNameToJavaClassName("","a/b") );
		assertEquals( "a.b.C", JSTranspiler.moduleNameToJavaClassName("","a/b/c") );

		assertEquals( "Ab", JSTranspiler.moduleNameToJavaClassName("","ab.js") );
		assertEquals( "a.B", JSTranspiler.moduleNameToJavaClassName("","a/b.js") );
		assertEquals( "a.b.C", JSTranspiler.moduleNameToJavaClassName("","a/b/c.js") );

		assertEquals( "x.Ab", JSTranspiler.moduleNameToJavaClassName("x","ab") );
		assertEquals( "x.a.B", JSTranspiler.moduleNameToJavaClassName("x","a/b") );
		assertEquals( "x.a.b.C", JSTranspiler.moduleNameToJavaClassName("x","a/b/c") );

		assertEquals( "x.y.Ab", JSTranspiler.moduleNameToJavaClassName("x.y","ab") );
		assertEquals( "x.y.a.B", JSTranspiler.moduleNameToJavaClassName("x.y","a/b") );
		assertEquals( "x.y.a.b.C", JSTranspiler.moduleNameToJavaClassName("x.y","a/b/c") );
	}
}
