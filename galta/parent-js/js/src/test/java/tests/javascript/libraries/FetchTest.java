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
package tests.javascript.libraries;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.library.platform.FetchLibrary;
import org.monflabs.galtajs.library.platform.HostLibrary;

import tests.javascript.JavaScriptStrictTestCase;

/**
 * Tests for the WHATWG fetch API: Headers, Request, Response.
 * Only exercises the offline-testable surface (constructors, methods,
 * body consumption); the actual fetch() call requires network access
 * and is not exercised here.
 */
public class FetchTest extends JavaScriptStrictTestCase {

	public void testScript() throws Exception {
		execute();
	}

	@Override
	protected JSEnvironment.Builder createEnvironment() {
		JSEnvironment.Builder builder = super.createEnvironment();
		builder.registerLibrary(new HostLibrary());
		builder.registerLibrary(new FetchLibrary());
		return builder;
	}
}
