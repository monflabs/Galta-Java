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
package tests.javascript.stdlib;

import java.util.Date;

import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.builtins.standard.console.ConsoleToString;
import org.monflabs.galtajs.rt.builtins.standard.date.DateUtil;

import tests.javascript.JavaScriptStrictTestCase;

/**
 * @author Philippe Riand
 */
public class ConsoleToStringTest extends JavaScriptStrictTestCase {

	public void testPrimitive() throws Exception {
		assertEquals("null",ConsoleToString.toString(getEnvironment(),null));
		assertEquals("undefined",ConsoleToString.toString(getEnvironment(),RuntimeUtil.UNDEFINED));
		assertEquals("1",ConsoleToString.toString(getEnvironment(),1));
		assertEquals("1.1",ConsoleToString.toString(getEnvironment(),1.1));
		assertEquals("true",ConsoleToString.toString(getEnvironment(),true));
		assertEquals("false",ConsoleToString.toString(getEnvironment(),false));
		assertEquals("''",ConsoleToString.toString(getEnvironment(),""));
		assertEquals("'abc'",ConsoleToString.toString(getEnvironment(),"abc"));
		assertEquals("'a\\nc'",ConsoleToString.toString(getEnvironment(),"a\nc"));

		Date dt = new Date();
		DateUtil.setUTCFullYear(dt,DateUtil.dateToDouble(dt),2020,9,24);
		DateUtil.setUTCHours(dt,DateUtil.dateToDouble(dt),19,44,17,456);
		assertEquals("2020-10-24T19:44:17.456Z",ConsoleToString.toString(getEnvironment(),dt));
	}
	
// TODO: fix the console to string to match Node	

//	public void testJSObject() throws Exception {
//		assertEquals("{ a: 1, b: 2, c: 3, d: 4, e: 5, f: 6, g: 7 }",ConsoleToString.toString(getEnvironment(),JsonObject.parse("{ a:1, b:2, c:3, d:4, e:5, f:6, g:7 }")));
//		assertEquals("{ a:1, b:2, c:3, d:4, e:5, f:6, g:7, h:8 }",ConsoleToString.toString(getEnvironment(),JsonObject.parse("{ a:1, b:2, c:3, d:4, e:5, f:6, g:7, h:8 }")));
//		assertEquals("{ a:1, b:2, c:3, d:4, e:5, f:6, g:7, h:8, i:9 }",ConsoleToString.toString(getEnvironment(),JsonObject.parse("{ a:1, b:2, c:3, d:4, e:5, f:6, g:7, h:8 }")));
//	}
//
//	public void testJSArray() throws Exception {
//		assertEquals("[]",ConsoleToString.toString(getEnvironment(),JSArray.create()));
//		assertEquals("[ true ]",ConsoleToString.toString(getEnvironment(),JSArray.of(true)));
//		assertEquals("[ 'a', 1 ]",ConsoleToString.toString(getEnvironment(),JSArray.of("a",1)));
//	}
//
//	public void testNested() throws Exception {
//		assertEquals("[ 1, true, 'abc', [ [ 4, 5 ] ], Object {\n  x: 4,\n  y: Object {\n  z: 99,\n  },\n  } ]",
//		ConsoleToString.toString(getEnvironment(),
//				JSArray.of(1,true,"abc",
//					JSArray.of(
//						JSArray.of(4,5)
//				),
//					JSObject.of("x",4,"y",
//							JSObject.of("z",99)
//				)
//			)
//		));
//	}
//
//	public void testSparse() throws Exception {
//		JSArray a = JSArray.create();
//		a.jsAdd(1);
//		a.jsPut(4,99);
//		assertEquals("[ 1, <3 empty items>, 99 ]",ConsoleToString.toString(getEnvironment(),a));
//	}
//
//	public void testCircular() throws Exception {
//		JSArray a1 = JSArray.of(1,2,3);
//		JSArray a2 = JSArray.of(4,a1,5);
//		a1.jsAdd(a2);
//		assertEquals("[ 1, 2, 3, [ 4, [Circular], 5 ] ]",ConsoleToString.toString(getEnvironment(),a1));
//	}
}
