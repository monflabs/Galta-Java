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
package org.monflabs.galtajs.node.assignop;

import org.monflabs.galtajs.JSParseException;
import org.monflabs.galtajs.node.ASTIdentifier;
import org.monflabs.galtajs.node.ASTNode;
import org.monflabs.galtajs.parser.Token;
import org.monflabs.galtajs.rt.JSResult;
import org.monflabs.galtajs.rt.interpreter.JSInterpretedRuntimeContext;
import org.monflabs.galtajs.rt.transpiler.VarAccessor;
import org.monflabs.util.StringFormat;




/**
 * Abstract assignment node.
 */
public abstract class ASTAbstractAssign extends ASTNode {

	protected ASTNode leftNode;
	protected ASTNode rightNode;

	protected ASTAbstractAssign(Token t, ASTNode leftNode, ASTNode rightNode) {
		super(t);
		this.leftNode = assignParent(leftNode);
		this.rightNode = assignParent(rightNode);
	}

	@Override
	public int getChildCount() {
		return super.getChildCount()+2;
	}

	@Override
	public ASTNode getChild(int index) {
		switch(index) {
			case 0 ->		{ return leftNode; }
			case 1 ->		{ return rightNode; }
			default ->		{ return super.getChild(index-2); }
		}
	}
	@Override
	protected void _setChild(int index, ASTNode node) {
		switch(index) {
			case 0 ->	{ this.leftNode = node; }
			case 1 ->	{ this.rightNode  = node; }
			default ->  { super._setChild(index-2,node); }
		}
	}

	@Override
	protected void init(InitContext initContext) {
		super.init(initContext);
		
		// For now - we should find a better way to validate the assigned nodes???
		ASTNode leftNode = getLeftNode();
		// ASTAssign drops the parentheses around its target (keeping only the fact)
		if(this instanceof ASTAssign as && as.isLeftParenthesized() && leftNode instanceof org.monflabs.galtajs.node.literal.ASTContainerLiteral) {
			throw new JSParseException(null, this, "Invalid assignment target: a parenthesized destructuring pattern");
		}
		checkAssignmentTarget(leftNode,
				this instanceof ASTAssign ? AssignmentUse.PLAIN
				: (this instanceof ASTAssignAnd || this instanceof ASTAssignOr || this instanceof ASTAssignNullCoalescing) ? AssignmentUse.LOGICAL
				: AssignmentUse.COMPOUND,
				initContext.isGenuinelyStrict());
		if(leftNode instanceof ASTIdentifier id) {
			// AssignmentTargetType early error (static semantics, e.g. spec
			// 13.15.1): assigning to "eval"/"arguments" in strict-mode code is
			// a SyntaxError that must be detected at PARSE time, not just when
			// the assignment actually executes. This matters whenever the
			// assignment is inside a function/branch that's parsed but never
			// reached at runtime (test262 language/statements/function/
			// 13.0-{7,8,15,16}-s.js: the offending `eval = 42` sits in a
			// function body defined by a strict-mode eval(), so the function's
			// own declaration - being strict-eval-local - never even leaks out
			// to be called; only a parse-time check catches it). Distinct
			// from, and in addition to, ASTIdentifier.evaluateAssign()'s
			// existing RUNTIME check of the same restriction (still needed for
			// paths that resolve the target dynamically, e.g. via optimizer
			// fast paths that skip re-running this static check's node type).
			if(initContext.isGenuinelyStrict() && ("eval".equals(id.getId()) || org.monflabs.galtajs.rt.builtins.standard.arguments.Arguments.ARGUMENTS.equals(id.getId()))) {
				throw new JSParseException(null, this, "Cannot assign to '{0}' in strict mode", id.getId());
			}
			// Assigning to a const parses: it is a runtime TypeError, checked by
			// ASTIdentifier.evaluateAssign()
		}
	}
	// Logical assignment (&&=, ||=, ??=) to an identifier, spec 13.15.2: the
	// reference is resolved and read first; the right side is only evaluated,
	// and the binding only written, when the operator does not short-circuit.
	protected void evaluateLogicalAssign(JSInterpretedRuntimeContext context, ASTIdentifier ident, java.util.function.Predicate<Object> assigns, JSResult result) {
		VarAccessor preResolved = ident.getScopeHops()<0 ? context.getVariableEntry(ident.getId()) : null;
		Object oldValue = ident.evaluateValue(context, new JSResult());
		if(!assigns.test(oldValue)) {
			result.setValue(oldValue);
			return;
		}
		Object rightValue = getRightNode().evaluateValue(context, new JSResult());
		ident.evaluateAssign(context, rightValue, null, result, null, preResolved);
	}

	

	public ASTNode getLeftNode() {
		return leftNode;
	}

	public ASTNode getRightNode() {
		return rightNode;
	}
	
	
    @Override
    public String decompileExpression() {
		return StringFormat.format("{0}{1}{2}", leftNode.decompileExpression(), decompileOperator(), rightNode.decompileExpression());
	}
    protected abstract String decompileOperator();
}