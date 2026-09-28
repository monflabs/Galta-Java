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
package org.monflabs.galtajs.node.control;

import java.util.List;

import org.monflabs.galtajs.node.ASTNode;
import org.monflabs.galtajs.node.ASTVarContainer;
import org.monflabs.galtajs.parser.Token;
import org.monflabs.galtajs.types.JSType;

/**
 * Synthetic container inserted between {@link ASTSwitch} and its {@code
 * cases[]} array, purely to give the CaseBlock (spec 12.11: "the CaseBlock's
 * lexical environment") its own {@link IContextBlockContainer} boundary,
 * separate from {@link ASTSwitch}'s discriminant expression.
 *
 * Per spec, SwitchStatement evaluation resolves the discriminant Expression
 * in the OUTER environment (before the CaseBlock's own declarative
 * environment is even created), while every case selector/body runs inside
 * ONE shared CaseBlock environment (a `let`/`const` declared in any case is
 * visible - TDZ-respecting - to every other case, not just its own). Before
 * this class existed, `ASTSwitch` itself played BOTH roles (it directly
 * implemented `IContextBlockContainer` via extending `ASTVarContainer`, and
 * both the discriminant AND every case were its direct AST children) - so
 * the closure-hoisting walk (`ASTFunction.init()`'s
 * `findParentNodeByClassUnchecked(IContextBlockContainer.class)`) and the
 * free-variable-resolution walk (`ASTNode.findVariable()`, which checks
 * `instanceof ASTVarContainer`) could not tell a closure written directly in
 * the discriminant from one in a case selector/body - both resolved through
 * the exact same single container, even though the discriminant's closure
 * must see the OUTER scope (its own `[[Environment]]` is captured before the
 * CaseBlock environment exists) while a case closure must see the CaseBlock's
 * own scope. See KnownGaps.md's switch-statement entry for the full history.
 *
 * This class carries the CaseBlock's own declared variables/nested function
 * classes (via `ASTVarContainer`) and is the parent of `cases[]` - `exprNode`
 * (the discriminant) stays a direct child of `ASTSwitch` itself, NOT a
 * descendant of this wrapper, so `ASTSwitch` no longer needs to (and no
 * longer does) implement `IContextBlockContainer` at all: a closure in the
 * discriminant now walks straight past the whole switch statement to
 * whatever genuinely encloses it, while a closure/declaration anywhere under
 * `cases[]` resolves through THIS node - one single, shared container for
 * the entire CaseBlock (deliberately NOT one per `ASTCase` - that would be
 * spec-incorrect, splitting the CaseBlock's single shared environment).
 *
 * This node has no runtime behavior of its own - `ASTSwitch` still drives
 * every actual evaluate()/transpileJavaStatement() decision (case matching,
 * fallthrough, the shared block context), reading declared
 * variables/functions from this wrapper instead of from itself. Nothing
 * calls evaluate()/transpileJavaStatement()/decompileStatement() directly on
 * this node - it exists purely to be the correct parent-chain link for
 * `cases[]`.
 */
public class ASTSwitchCaseBlock extends ASTVarContainer {

	private ASTNode[] cases;

	public ASTSwitchCaseBlock(Token t, List<ASTNode> cases) {
		super(t);
		this.cases = assignParent(cases);
	}

	public ASTNode[] getCases() {
		return cases;
	}

	@Override
	public int getChildCount() {
		return super.getChildCount()+cases.length;
	}
	@Override
	public ASTNode getChild(int index) {
		if(index<cases.length) {
			return cases[index];
		}
		return super.getChild(index-cases.length);
	}
	@Override
	protected void _setChild(int index, ASTNode node) {
		if(index<cases.length) {
			cases[index] = node;
			return;
		}
		super._setChild(index-cases.length,node);
	}

	@Override
	public JSType getReturnedType() {
		return JSType.UNKNOWN;
	}
}
