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

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.jsonfactory.JSArray;
import org.monflabs.galtajs.jsonfactory.JSObject;

import tests.javascript.JavaScriptStrictTestCase;
import util.GlobalTestEnvironment;

/**
 * @author Philippe Riand
 */
public class JavaTranspilerTest extends JavaScriptStrictTestCase {
	
	@Override
	protected JSEnvironment.Builder createEnvironment() {
		JSEnvironment.Builder env = GlobalTestEnvironment.newBuilder()
				//.enableGaltaJSExtensions(false)
				.supportReturnOutsideFunction(true)
				;
		return env;
	}


	public void testVarDeclaration() throws Exception {
		String js = 
"""
// A comment
const s = 'Hello, World!';
return s;
""";
		Object res = transpilerExecute(getEnvironment(),JAVA_CLASSNAME,null,getEnvironment().createScript(js,"TranspilerTest")).value();
		assertEquals("Hello, World!", res );
	}

	public void testAssignment() throws Exception {
		String js = 
"""
// A comment
let s;
s = 'Hello, World!';
return s;
""";
		Object res = transpilerExecute(getEnvironment(),JAVA_CLASSNAME,null,getEnvironment().createScript(js,"TranspilerTest")).value();
		assertEquals("Hello, World!", res );
	}
	
	public void testJavaCompilerFunction() throws Exception {
		String js = 
"""
function f(n) {
  return n+"A";
}
return f("Z");			
""";
		Object res = transpilerExecute(getEnvironment(),JAVA_CLASSNAME,null,getEnvironment().createScript(js,"TranspilerTest")).value();
		assertEquals("ZA", res );
	}

	public void testJavaCompilerCallStandard() throws Exception {
		String js = 
"""
const a = parseInt('1234')
return a;				
""";
		Object res = transpilerExecute(getEnvironment(),JAVA_CLASSNAME,null,getEnvironment().createScript(js,"TranspilerTest")).value();
		assertEquals(1234, res );
	}

	public void testJavaCompilerMethodCallStandard() throws Exception {
		String js = 
"""
const s = "AbCd"
return s.toLowerCase();				
""";
		Object res = transpilerExecute(getEnvironment(),JAVA_CLASSNAME,null,getEnvironment().createScript(js,"TranspilerTest")).value();
		assertEquals("abcd", res );
	}

	public void testJavaCompilerObjectLiteral() throws Exception {
		String js = 
"""
const c = 5
const s = { a: 1, b: 4, c, d: {e: 5} }
return s;				
""";
		Object res = transpilerExecute(getEnvironment(),JAVA_CLASSNAME,null,getEnvironment().createScript(js,"TranspilerTest")).value();
		assertEquals(JSObject.of(getEnvironment(),"a",1,"b",4,"c",5,"d",JSObject.of(getEnvironment(),"e",5)), res );
	}

	public void testJavaCompilerArrayLiteral() throws Exception {
		String js = 
"""
const c = 5
const s = [ 1, 4, c, [6, 7] ]
return s;				
""";
		Object res = transpilerExecute(getEnvironment(),JAVA_CLASSNAME,null,getEnvironment().createScript(js,"TranspilerTest")).value();
		assertEquals(JSArray.of(getEnvironment(),1,4,5,JSArray.of(getEnvironment(),6,7)), res );
	}

	public void testJavaCompilerGetterSetter() throws Exception {
		String js = 
"""
const o = {}
o.a = 3
o.b = {}
o.b.c = 8
return o;				
""";
		Object res = transpilerExecute(getEnvironment(),JAVA_CLASSNAME,null,getEnvironment().createScript(js,"TranspilerTest")).value();
		assertEquals(JSObject.of(getEnvironment(),"a",3,"b",JSObject.of(getEnvironment(),"c",8)), res );
	}
}
