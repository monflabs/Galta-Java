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
package org.monflabs.galtajs.optimizer;

import org.monflabs.galtajs.node.ASTNode;

/**
 * Fuses ConstantFoldingOptimizer and UnreachableCodeRemovalOptimizer into a
 * single AST traversal instead of two separate full passes (see
 * ScriptOptimizer.DEFAULT_NODE_OPTIMIZER_NODES).
 *
 * UnreachableCodeRemovalOptimizer.simplifyNode() must run AFTER this node's
 * own children have been constant-folded - e.g. `if(1+1){...}`'s test must
 * already be the literal `2` before its dead-branch check can fire, since
 * that check (like every other literal-detection helper in
 * UnreachableCodeRemovalOptimizer) only ever recognizes an already-folded
 * ASTLiteral, never a foldable-but-not-yet-literal expression. This ordering
 * requirement previously existed only implicitly, via
 * DEFAULT_NODE_OPTIMIZER_NODES's array order running two full separate
 * passes; here it's satisfied by calling simplifyNode() from
 * afterChildrenOptimized(), which ConstantFoldingOptimizer's own optimize()
 * invokes only once ITS per-child loop has finished folding every child of
 * `node`.
 *
 * This is safe because UnreachableCodeRemovalOptimizer never looks outside
 * the node it's currently examining - every one of its helpers
 * (removeUnreachable, calculateTerminatesFlow, processStatement, etc.) only
 * ever inspects that node's own descendants, never siblings or ancestors.
 * So "this node's own subtree is fully folded" (which per-node fusion
 * guarantees) is exactly as sufficient as "the whole tree is folded first"
 * (which running two separate full passes guarantees) - the latter is
 * simply stronger than UnreachableCodeRemovalOptimizer ever needs.
 *
 * ScopeResolutionOptimizer, the third pass in the default pipeline,
 * deliberately stays a separate, unfused pass - its own traversal assigns
 * different scope parameters to different children of the same node
 * (ASTFor_, ASTFunction, ASTTry) using a private Scope model that doesn't
 * fit this generic per-child-context shape, and it's a shared,
 * process-lifetime singleton with no dedicated test coverage - not a good
 * candidate to fold into this same mechanism.
 */
public class ConstantFoldingAndUnreachableCodeOptimizer extends ConstantFoldingOptimizer {

	private final UnreachableCodeRemovalOptimizer unreachable = new UnreachableCodeRemovalOptimizer();

	@Override
	protected void afterChildrenOptimized(JSOptimizerContext context, ASTNode node) {
		unreachable.simplifyNode(context, node);
	}
}
