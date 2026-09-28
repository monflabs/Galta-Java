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
package tests.javascript.modules;

import static org.junit.Assert.assertThrows;

import org.monflabs.galtajs.JSException;
import org.monflabs.galtajs.modules.ModuleUtil;

import tests.javascript.JavaScriptStrictTestCase;

/**
 * @author Philippe Riand
 */
public class ModuleUtilTest extends JavaScriptStrictTestCase {
	
	public void testresolvePath() throws Exception {
		assertEquals( "", ModuleUtil.resolvePath("", ""));
		assertEquals( "a", ModuleUtil.resolvePath("", "a"));
		assertEquals( "a/b", ModuleUtil.resolvePath("", "a/b"));
		assertEquals( "a/b", ModuleUtil.resolvePath("", "a//b"));
		assertEquals( "a/b", ModuleUtil.resolvePath("", "a/./b"));
		assertEquals( "a/b", ModuleUtil.resolvePath("", "a/b/."));
		assertEquals( "a/b", ModuleUtil.resolvePath("", "a/../a/b"));
		assertEquals( "a/c", ModuleUtil.resolvePath("", "a/b/../c"));

		assertEquals( "", ModuleUtil.resolvePath("x", ""));
		assertEquals( "a", ModuleUtil.resolvePath("x", "a"));
		assertEquals( "a/b", ModuleUtil.resolvePath("x", "a/b"));

		assertEquals( "", ModuleUtil.resolvePath("x", "."));
		assertEquals( "a", ModuleUtil.resolvePath("x", "./a"));
		assertEquals( "a/b", ModuleUtil.resolvePath("x", "./a/b"));

		assertEquals( "x", ModuleUtil.resolvePath("x/y", "."));
		assertEquals( "x/a", ModuleUtil.resolvePath("x/y", "./a"));
		assertEquals( "x/a/b", ModuleUtil.resolvePath("x/y", "./a/b"));

		assertEquals( "x", ModuleUtil.resolvePath("x/y", "."));
		assertEquals( "x/a", ModuleUtil.resolvePath("x/y", "./a"));
		assertEquals( "x/a/b", ModuleUtil.resolvePath("x/y", "./a/b"));
		assertEquals( "a/b", ModuleUtil.resolvePath("x/y", "../a/b"));
		assertEquals( "x/b", ModuleUtil.resolvePath("x/y", "./a/../b"));
		
		assertThrows( JSException.class, () -> ModuleUtil.resolvePath("", "..") );
		assertThrows( JSException.class, () -> ModuleUtil.resolvePath("", "../b") );
		assertThrows( JSException.class, () -> ModuleUtil.resolvePath("", "a/../../b") );
		assertThrows( JSException.class, () -> ModuleUtil.resolvePath("a", "..") );
		assertThrows( JSException.class, () -> ModuleUtil.resolvePath("a/b", "../../b") );
		assertThrows( JSException.class, () -> ModuleUtil.resolvePath("a/b", "../b/../..") );
	}		
}
