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
package tests.galtajs.preprocessor;

import org.monflabs.galtajs.preprocessor.ScriptPreProcessor;

public class NoDirectivesTest extends PreProcessorTestCase {

	public void testEmpty() throws Exception {
		String s = "";
		String r = ScriptPreProcessor.preprocess(s,SYMBOLS);
		support.assertNormalizedTextEquals(s,r);
	}

	public void testNoDirectives() throws Exception {
		String s = 
"""
// Here is some code
// #fake
With no directives
Do you belive it #if?				
""";
		String r = ScriptPreProcessor.preprocess(s,SYMBOLS);
		support.assertNormalizedTextEquals(s,r);
	}
}
