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
package tests.javascript.builtin.regexp;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.rt.builtins.standard.regexp.joni.RegExpEngineJoni;

import tests.javascript.JavaScriptStrictTestCase;

/**
 * RepeatMatcher capture-group reset (22.2.2.5.1): a capture group nested
 * inside a quantified atom must reset to "not participated" at the START of
 * each new iteration attempt, so a group untouched by the LAST successful
 * iteration reads as undefined even if an earlier iteration captured into
 * it. The patched, vendored Joni fork (org.monflabs.galtajs.external.
 * org_joni) implements this correctly; the JDK engine (java.util.regex)
 * still doesn't - so this is a dedicated test rather than an addition to
 * the shared RegExpTest.js that also runs against the JDK engine.
 *
 * @author Philippe Riand
 */
public class RegExpJoniRepeatCaptureResetTest extends JavaScriptStrictTestCase {

	@Override
	protected JSEnvironment.Builder createEnvironment() {
		JSEnvironment.Builder builder = super.createEnvironment()
				.regexpEngineFactory(RegExpEngineJoni.factory());
		return builder;
	}

	public void testScript() throws Exception {
		executeFile("RegExpJoniRepeatCaptureResetTest");
	}
}
