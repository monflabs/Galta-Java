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
package tests.galtajs.typehints;

import tests.BaseProjectTestCase;

/**
 * With JSConfiguration.supportTypeHints() left at its default (false, the
 * standard - non-GaltaJS-extensions - environment this test runs under),
 * TypeScript type-hint syntax must be rejected exactly as it always was:
 * zero behavior change for callers who don't opt in. See TypeHintsTest
 * (tests.galtajs.typehints, GaltaJSTestCase/enableGaltaJSExtensions) for the
 * flag turned on.
 *
 * @author Philippe Riand
 */
public class TypeHintsDisabledTest extends BaseProjectTestCase {

	public void testScript() throws Exception {
		execute();
	}
}
