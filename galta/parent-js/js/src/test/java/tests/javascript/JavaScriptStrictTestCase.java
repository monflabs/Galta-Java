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
package tests.javascript;

import org.monflabs.galtajs.JSEnvironment;

import tests.BaseProjectTestCase;
import util.GlobalTestEnvironment;

/**
 * Abstract execution test.
 * 
 * @author Philippe Riand
 */
public abstract class JavaScriptStrictTestCase extends BaseProjectTestCase {

	protected JavaScriptStrictTestCase() {
	}

	@Override
	protected JSEnvironment.Builder createEnvironment() {
		JSEnvironment.Builder builder = GlobalTestEnvironment.newBuilder()
				.strictMode(true)
				.deprecatedApis(true); // despite strict mode...
		return builder;
	}
}
