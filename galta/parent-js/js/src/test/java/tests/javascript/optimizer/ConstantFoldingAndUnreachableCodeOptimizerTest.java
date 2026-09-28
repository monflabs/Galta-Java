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

import org.monflabs.galtajs.optimizer.ConstantFoldingAndUnreachableCodeOptimizer;
import org.monflabs.galtajs.optimizer.NodeOptimizer;
import org.monflabs.galtajs.optimizer.ScriptOptimizer;

/**
 * Exercises the fold-then-eliminate ordering dependency the fused optimizer
 * relies on - a dead branch behind a FOLDABLE (not yet literal) condition
 * must still be removed, which only happens if constant folding of this
 * node's own children completes before its own dead-code check runs. Every
 * existing UnreachableCodeRemovalOptimizer test uses an already-literal
 * `if(true)`/`if(false)` condition and so never exercises this ordering at
 * all.
 */
public class ConstantFoldingAndUnreachableCodeOptimizerTest extends BaseOptimizerTestCase {

	public void testFoldableIfCondition() throws Exception {
		ScriptOptimizer optimizer = ScriptOptimizer.newBuilder().optimizers(new NodeOptimizer[] {new ConstantFoldingAndUnreachableCodeOptimizer()} ).build();

		support.assertTextResult(asString(getEnvironment(),optimizer,
				"""
					let a = 3;
					if(1+1) {
					  a = 44
					} else {
					  a = 55
					}
				"""), "foldable_if_truthy.txt");
		support.assertTextResult(asString(getEnvironment(),optimizer,
				"""
					let a = 3;
					if(1-1) {
					  a = 44
					} else {
					  a = 55
					}
				"""), "foldable_if_falsy.txt");
	}

	// A while(cond){} loop whose condition folds to a constant TRUTHY value
	// (and has no break) never falls through - making whatever follows it
	// dead code. Only fires if `2-1` is already the literal `1` by the time
	// UnreachableCodeRemovalOptimizer's own ASTWhile terminatesFlow check
	// runs (calculateTerminatesFlow only recognizes an already-folded
	// ASTLiteral, per isConstantTrue()).
	public void testFoldableWhileCondition() throws Exception {
		ScriptOptimizer optimizer = ScriptOptimizer.newBuilder().optimizers(new NodeOptimizer[] {new ConstantFoldingAndUnreachableCodeOptimizer()} ).build();

		support.assertTextResult(asString(getEnvironment(),optimizer,
				"""
					function f() {
						while(2-1) {
						}
						return 5;
					}
				"""), "foldable_while_true.txt");
	}
}
