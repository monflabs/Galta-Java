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

public class DirectivesTest extends PreProcessorTestCase {

	public void testIfTrue() throws Exception {
		String source = 
"""
before
  // #if PRIVATE
	Private part
  // #endif
After				
""";
		String expected = 
"""
before
  // #if PRIVATE
	Private part
  // #endif
After				
""";
		String result = ScriptPreProcessor.preprocess(source,SYMBOLS);
		support.assertNormalizedTextEquals(expected,result);
	}
	
	public void testIfTrueElse() throws Exception {
		String source = 
"""
before
  // #if PRIVATE
	Private part
  // #else
	Public part
  // #endif
After				
""";
		String expected = 
"""
before
  // #if PRIVATE
	Private part
  // #else
//	Public part
  // #endif
After				
""";
		String result = ScriptPreProcessor.preprocess(source,SYMBOLS);
		support.assertNormalizedTextEquals(expected,result);
	}

	public void testElifTrue() throws Exception {
		String source = 
"""
before
  // #if PRIVATE
	Private part
  // #elif PUBLIC
	Fake part
  // #else FAKE
    Else part
  // #endif
After				
""";
		String expected = 
"""
before
  // #if PRIVATE
	Private part
  // #elif PUBLIC
//	Fake part
  // #else FAKE
//    Else part
  // #endif
After				
""";
		String result = ScriptPreProcessor.preprocess(source,SYMBOLS);
		support.assertNormalizedTextEquals(expected,result);
	}

	public void testElifFalse() throws Exception {
		String source = 
"""
before
  // #if PUBLIC
	Public part
  // #elif PRIVATE
	Private part
  // #else FAKE
    Else part
  // #endif
After				
""";
		String expected = 
"""
before
  // #if PUBLIC
//	Public part
  // #elif PRIVATE
	Private part
  // #else FAKE
//    Else part
  // #endif
After				
""";
		String result = ScriptPreProcessor.preprocess(source,SYMBOLS);
		support.assertNormalizedTextEquals(expected,result);
	}

	public void testElifElse() throws Exception {
		String source = 
"""
before
  // #if PUBLIC
	Public part
  // #elif FAKE
	Private part
  // #else FAKE
    Else part
  // #endif
After				
""";
		String expected = 
"""
before
  // #if PUBLIC
//	Public part
  // #elif FAKE
//	Private part
  // #else FAKE
    Else part
  // #endif
After				
""";
		String result = ScriptPreProcessor.preprocess(source,SYMBOLS);
		support.assertNormalizedTextEquals(expected,result);
	}
	
	public void testIfFalse() throws Exception {
		String source = 
"""
before
  // #if PUBLIC
	Public part
  // #endif
After				
""";
		String expected = 
"""
before
  // #if PUBLIC
//	Public part
  // #endif
After				
""";
		String result = ScriptPreProcessor.preprocess(source,SYMBOLS);
		support.assertNormalizedTextEquals(expected,result);
	}
	
	public void testIfFalseElse() throws Exception {
		String source = 
"""
before
  // #if PUBLIC
	Public part
  // #else
	Private part
  // #endif
After				
""";
		String expected = 
"""
before
  // #if PUBLIC
//	Public part
  // #else
	Private part
  // #endif
After				
""";
		String result = ScriptPreProcessor.preprocess(source,SYMBOLS);
		support.assertNormalizedTextEquals(expected,result);
	}
}
