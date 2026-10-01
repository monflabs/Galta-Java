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

	// The class name is the compiled-module cache key: distinct module names
	// (other than the optional ".js") must never share a class
	public void testClassNameInjective() throws Exception {
		String[] names = { "ab", "Ab", "aB", "a-b", "a_b", "a__b", "a_db", "ab-", "a.b", "a b", "a+b", "1ab", "_1ab",
				"a/b", "a/B", "A/b", "a-/b", "int/x", "i/x", "x/int", "a//b", "a\u00e9", "a\u00c9", "\u4e2d",
				"beautify-html", "beautifyhtml", "typescript" };
		java.util.Map<String,String> seen = new java.util.HashMap<>();
		for(String n: names) {
			String c = JSTranspiler.moduleNameToJavaClassName("js", n);
			String previous = seen.put(c, n);
			assertNull(n+" and "+previous+" both map to "+c, previous);
			for(String part: c.split("\\.")) {
				assertTrue(c, javax.lang.model.SourceVersion.isIdentifier(part) && !javax.lang.model.SourceVersion.isKeyword(part));
			}
		}
		assertEquals( "js.Beautify_dhtml", JSTranspiler.moduleNameToJavaClassName("js","beautify-html.js") );
		assertEquals( "js.Typescript", JSTranspiler.moduleNameToJavaClassName("js","typescript.js") );
		assertEquals( "js._Ab", JSTranspiler.moduleNameToJavaClassName("js","Ab") );
		assertEquals( "js._1ab", JSTranspiler.moduleNameToJavaClassName("js","1ab") );
	}
}
