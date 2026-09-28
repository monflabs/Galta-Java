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
package util;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.environments.JavaScriptEnvironment;
import org.monflabs.galtajs.library.StaticLibrary;
import org.monflabs.galtajs.library.UnitTestLibrary;
import org.monflabs.galtajs.library.java.JavaLibrary;

import tests.classes.ExceptionClass;
import tests.classes.TestClass;


public class GlobalTestEnvironment {
	
	public static final String GLOBAL_OBJECT = "obj";
	public static final Class<?> TEST_CLASS = TestClass.class;
	public static final Class<?> EXCEPTION_CLASS = ExceptionClass.class;
	
	public static JSEnvironment.Builder newBuilder() {
		JSEnvironment.Builder b = JavaScriptEnvironment.newBuilder();
		// >>> not sure we should set that here or it should be tested just for GaltaJS
		b.supportJavaNative(true);
		b.supportBigDecimalLiteral(true);
		b.supportGlobalAlias(true);
		b.registerLibrary(new JavaLibrary());
		// <<<
		b.deprecatedApis(true);
		b.registerLibrary(new UnitTestLibrary());
		
		StaticLibrary sampleLib = new StaticLibrary();
		sampleLib.addStaticGlobal(GLOBAL_OBJECT, new TestClass());
		sampleLib.addStaticGlobal("javaEX", EXCEPTION_CLASS);
		sampleLib.addStaticGlobal("javaTest", TEST_CLASS);
		b.registerLibrary(sampleLib);
		return b;
	}

	public static JSEnvironment create() {
		return newBuilder().build();
	}
	
	private GlobalTestEnvironment() {}
}
