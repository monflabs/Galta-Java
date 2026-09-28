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
package tests.javascript.optimizer;

import org.monflabs.galtajs.optimizer.ConstantFoldingOptimizer;
import org.monflabs.galtajs.optimizer.NodeOptimizer;
import org.monflabs.galtajs.optimizer.ScriptOptimizer;

/**
 * @author Philippe Riand
 */
public class ConstantFoldingTest extends BaseOptimizerTestCase {
	

	public void testNumberConstant() throws Exception {
		ScriptOptimizer optimizer = ScriptOptimizer.newBuilder().optimizers(new NodeOptimizer[] {new ConstantFoldingOptimizer()} ).build();

		support.assertTextResult(asString(getEnvironment(),optimizer,"1"), "number_literal.txt");
		support.assertTextResult(asString(getEnvironment(),optimizer,"1+2"), "number_addtion-2.txt");
		support.assertTextResult(asString(getEnvironment(),optimizer,"1+2+3"), "number_addtion-3.txt");
	}
	
	public void testStringConstant() throws Exception {
		ScriptOptimizer optimizer = ScriptOptimizer.newBuilder().optimizers(new NodeOptimizer[] {new ConstantFoldingOptimizer()} ).build();

		support.assertTextResult(asString(getEnvironment(),optimizer,"'1'"), "string_literal.txt");
		support.assertTextResult(asString(getEnvironment(),optimizer,"'1'+'2'"), "string_addtion-2.txt");
		support.assertTextResult(asString(getEnvironment(),optimizer,"'1'+'2'+'3'"), "string_addtion-3.txt");

		support.assertTextResult(asString(getEnvironment(),optimizer,"``"), "string_template-1.txt");
		support.assertTextResult(asString(getEnvironment(),optimizer,"`this is me`"), "string_template-2.txt");
		support.assertTextResult(asString(getEnvironment(),optimizer,"`this is me ${44} and ${23}`"), "string_template-3.txt");
	}
	
	public void testMixedOptimizer() throws Exception {
		ScriptOptimizer optimizer = ScriptOptimizer.newBuilder().optimizers(new NodeOptimizer[] {new ConstantFoldingOptimizer()} ).build();
		
		support.assertTextResult(asString(getEnvironment(),optimizer,"1+2+'A'"), "mix1.txt");
		support.assertTextResult(asString(getEnvironment(),optimizer,"'A'+1+2"), "mix2.txt");
	}
	
	public void testNegative() throws Exception {
		ScriptOptimizer optimizer = ScriptOptimizer.newBuilder().optimizers(new NodeOptimizer[] {new ConstantFoldingOptimizer()} ).build();

		support.assertTextResult(asString(getEnvironment(),optimizer,"1+Math.PI"), "negative1.txt");
	}

	public void testConstantDeclaration() throws Exception {
		ScriptOptimizer optimizer = ScriptOptimizer.newBuilder().optimizers(new NodeOptimizer[] {new ConstantFoldingOptimizer()} ).build();

		support.assertTextResult(asString(getEnvironment(),optimizer,"const a='1'; console.log(a)"), "constant_def_1.txt");
		support.assertTextResult(asString(getEnvironment(),optimizer,"const a='1'; const b=a+'2'; console.log(b)"), "constant_def_2.txt");
		support.assertTextResult(asString(getEnvironment(),optimizer,"let bb=1; bb<<=3"), "constant_def_3.txt");

		support.assertTextResult(asString(getEnvironment(),optimizer,"const x=3; o={x:4}; o={x}"), "constant_def_4.txt");
		support.assertTextResult(asString(getEnvironment(),optimizer,"const a=3; const b=5; { const a=b; }"), "constant_def_5.txt");
	}

	// Test decompiled
	public void testConstantDeclarationText() throws Exception {
		ScriptOptimizer optimizer = ScriptOptimizer.newBuilder().optimizers(new NodeOptimizer[] {new ConstantFoldingOptimizer()} ).build();

		support.assertTextResult( asCodeString(getEnvironment(),optimizer, "const a='1'; const b=a+'2'; console.log(b)"), "constant_def_decomp_1.txt");
	}
}
