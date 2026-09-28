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

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.optimizer.NodeOptimizer;
import org.monflabs.galtajs.optimizer.ScriptOptimizer;
import org.monflabs.galtajs.optimizer.UnreachableCodeRemovalOptimizer;

import util.GlobalTestEnvironment;

/**
 * @author Philippe Riand
 */
public class UnreachableCodeRemovalTest extends BaseOptimizerTestCase {
	
	public void testUnreachableIf() throws Exception {
		ScriptOptimizer optimizer = ScriptOptimizer.newBuilder().optimizers(new NodeOptimizer[] {new UnreachableCodeRemovalOptimizer()} ).build();

		JSEnvironment env = getEnvironment();
		
		support.assertTextResult(asString(env,optimizer,
				"""
					let a = 3;
					if(true) {
					  a = 44
					} else {
					  a = 55
					}
				"""), "if_1.txt");
		support.assertTextResult(asString(env,optimizer,
				"""
					let a = 3;
					if(false) {
					  a = 44
					} else {
					  a = 55
					}
				"""), "if_2.txt");

		support.assertTextResult(asString(env,optimizer,
				"""
					let a = 3;
					if(true) {
					  const b = 18;
					  a = 44
					} else {
					  a = 55
					}
				"""), "if_3.txt");
	}
	
	public void testUnreachableWithReturn() throws Exception {
		JSEnvironment env = GlobalTestEnvironment.newBuilder()
				.supportReturnOutsideFunction(true)
				.build();

		ScriptOptimizer optimizer = ScriptOptimizer.newBuilder().optimizers(new NodeOptimizer[] {new UnreachableCodeRemovalOptimizer()} ).build();

		support.assertTextResult(asString(env,optimizer,
				"""
					let a = 3;
					return 5;
					let b = 6;
					let c = a+b;					
				"""), "ret_1.txt");
		support.assertTextResult(asString(env,optimizer,
				"""
					let a = 3;
					if(a=4) {
						return 1;
					} else {
						return 2;
					}
					let b = 6;
				"""), "ret_2.txt");
	}
}
