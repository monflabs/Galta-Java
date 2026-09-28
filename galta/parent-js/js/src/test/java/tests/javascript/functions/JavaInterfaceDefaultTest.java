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
package tests.javascript.functions;

import org.monflabs.galtajs.JSEnvironment;

import tests.javascript.JavaScriptStrictTestCase;

/**
 * Test simple interface proxy.
 * 
 * @author Philippe Riand
 */
public class JavaInterfaceDefaultTest extends JavaScriptStrictTestCase {

	@FunctionalInterface
	public interface Functional {
		public int value();

		public default int multiplyBy10() {
			return value() * 10;
		}
	}

	public void testScript() throws Exception {
		execute();
	}

	public static class This {
		public void exec(Functional f, int check) {
			assertEquals(check, f.value());
			assertEquals(10 * check, f.multiplyBy10());
		}
	}

	@Override
	protected Object getThis(JSEnvironment environment) {
		return new This();
	}
}
