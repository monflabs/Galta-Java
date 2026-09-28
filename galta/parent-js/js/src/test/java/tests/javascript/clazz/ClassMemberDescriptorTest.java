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
package tests.javascript.clazz;

import tests.javascript.JavaScriptStrictTestCase;

/**
 * Class methods (instance and static, regardless of computed/literal name)
 * are non-enumerable, same as ordinary method shorthand; a static class
 * element named "prototype" is a TypeError; and function/constructor
 * objects expose "length" before "name" in Object.getOwnPropertyNames().
 *
 * Only implemented in interpreted mode - the transpiler generates its own,
 * separate class-initialization/name-resolution/object-construction code
 * and doesn't go through BuiltinClassConstructor.addClassMethod()/
 * ASTClassMember.evaluateName()/BaseConstructor the same way.
 *
 * @author Philippe Riand
 */
public class ClassMemberDescriptorTest extends JavaScriptStrictTestCase {

	public void testInstanceMethodsNotEnumerable() throws Exception {
		if(isJavaTranspiler()) {
			return;
		}
		assertEquals(true, executeCode(
			"class C {\n" +
			"  method(){}\n" +
			"  ['computed'](){}\n" +
			"}\n" +
			"Object.keys(C.prototype).length === 0;"));
	}

	public void testStaticMethodsNotEnumerable() throws Exception {
		if(isJavaTranspiler()) {
			return;
		}
		assertEquals(true, executeCode(
			"class C {\n" +
			"  static method(){}\n" +
			"  static ['computed'](){}\n" +
			"}\n" +
			"Object.keys(C).length === 0;"));
	}

	public void testStaticPrototypeNameThrows() throws Exception {
		if(isJavaTranspiler()) {
			return;
		}
		assertEquals(true, executeCode(
			"let threw = false;\n" +
			"try { class C { static ['prototype'](){} } } catch(e) { threw = e instanceof TypeError; }\n" +
			"threw;"));
	}

	public void testFunctionLengthBeforeName() throws Exception {
		if(isJavaTranspiler()) {
			return;
		}
		assertEquals(true, executeCode(
			"function f(a,b){}\n" +
			"JSON.stringify(Object.getOwnPropertyNames(f)) === '[\"length\",\"name\",\"prototype\"]';"));
	}
}
