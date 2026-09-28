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

import tests.javascript.JavaScriptStrictTestCase;

/**
 * @author Philippe Riand
 */
public class ObjectAsExpressionTest extends JavaScriptStrictTestCase {

	// The bare top-level object literal is a GaltaJS extension behind
	// JSConfiguration.supportTopLevelObjectLiteral() (off by default, on with
	// enableGaltaJSExtensions()) - opt in explicitly here, the environment is
	// otherwise standard. See tests.galtajs.TopLevelObjectLiteralTest for the
	// flag's on/off/opt-in matrix.
	@Override
	protected org.monflabs.galtajs.JSEnvironment.Builder createEnvironment() {
		return super.createEnvironment().supportTopLevelObjectLiteral(true);
	}

	public void testObjectLiteratl() throws Exception {
		// This only works in interpreted mode
		// as the transpiler does not generate a return value
		// Transpiler can only get the value of a single expression, see the API
		// Is it worth find a solution?
		if(!isJavaTranspiler()) {
			support.assertJsonEquals("{}", executeCode("{ }") );
			support.assertJsonEquals("{b:98}", executeCode("{ b:  98}") );
			support.assertJsonEquals("{a:34}", executeCode("{'a':34}") );
			// The GaltaJS "bare object literal" extension only applies when the
			// "{...}" is the SOLE top-level statement (see MainSourceElements()
			// in JSParser.jj) - test262 requires a non-sole trailing "{ident: expr}"
			// to be a real (possibly labeled) Block/Statement instead, per
			// ECMA-262's StatementList grammar (fixed test262 language/statementList
			// gaps). So a preceding statement makes "{a:v}" a LabeledStatement
			// (label "a", body expression "v") whose completion value is just v,
			// not an object.
			assertEquals(34, executeCode("const v=34; {a:v}") );
	
			support.assertJsonEquals("[]", executeCode("[ ]") );
			support.assertJsonEquals("[2,3]", executeCode("[2,3]") );
			support.assertJsonEquals("[4,3]", executeCode("const v=2; [v*2,3]") );
	
			support.assertJsonEquals(3, executeCode("let v=0;for(let i=0; i<3; i++) { loop: for(let j=0; j<0; j++) {} v+=i; }; v") );
		}
	}		
}
