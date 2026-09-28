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
import org.monflabs.galtajs.node.ASTVarContainer;
import org.monflabs.galtajs.node.ASTVarContainer.VariableDef;
import org.monflabs.galtajs.node.control.ASTFunction;
import org.monflabs.galtajs.node.literal.ASTArrayLiteral;
import org.monflabs.galtajs.node.literal.ASTObjectLiteral;
import org.monflabs.galtajs.parser.Token;
import org.monflabs.galtajs.rt.interpreter.JSInterpretedRuntimeContext.VAR_TYPE;
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
			VariableDef v = findVariable(id.getId());
			if(v==null) {
				// Done by ASTIdentifier in evaluateAssign()
//				if(getEnvironment().mustDeclareAllVariables()) {
//					throw new JSParseException(null, this, "Variable {0} does not exist", id.getId());
//				}
//				v = findParentNodeByClass(IContextVarContainer.class).addVarDeclaration(id.getId(),VAR_TYPE.AUTO,null);
			} else {
				// NOT extended to VAR_TYPE.USING: unlike CONST's deliberate
				// same-scope-obvious static early error, using/await-using
				// reassignment must surface as a RUNTIME TypeError even in
				// this statically-resolvable case (test262's
				// using-invalid-assignment-next-expression-for.js reassigns
				// a using-declared for-loop variable from within the SAME
				// function and still expects assert.throws(TypeError, ...)
				// to catch it at call time, not a parse-time SyntaxError
				// aborting the whole script before the assertion ever runs)
				// - the runtime check in ASTIdentifier.evaluateAssign() /
				// RuntimeUtil already covers it correctly.
				if(v.getVarType()==VAR_TYPE.CONST) {
					// Note that an assign statement should still be valid.
					// Also: only flag this as a static/parse-time error when
					// the assignment is in the SAME function scope as the
					// const declaration (GaltaJS's deliberate "same-scope-
					// obvious" early error, see ConstAssignTest.js). An
					// assignment reached only by crossing an intervening
					// function boundary (e.g. inside a nested function
					// expression not yet called) must NOT abort parsing -
					// per spec this is always a runtime-only TypeError
					// (SetMutableBinding on an immutable binding), and the
					// nested function may never even run, or may run inside
					// a try/catch expecting to observe that TypeError
					// (test262 language/global-code/decl-lex.js). Falls
					// through to ASTIdentifier.evaluateAssign()'s runtime
					// CONST check in that case.
					if(!isVarDecl() && !crossesFunctionBoundary(id.getId())) {
						throw new JSParseException(null, this, "Cannot assign a value to constant {0}", id.getId());
					}
				}
				// If this true?
//				if(initContext.isStrictMode()) {
//					if(v.getVarType()==VAR_TYPE.FUNCTION) {
//						throw new JSParseException(null, this, "Cannot assign a value to function {0}", id.getId());
//					}
//				}
			}
		}
	}
	// True if resolving `name` from this node walks through an intervening
	// ASTFunction (i.e. the declaring scope is an ENCLOSING function, not
	// this one) before reaching the ASTVarContainer that actually owns it -
	// mirrors ASTNode.findVariable()'s own walk, just also tracking function
	// boundaries crossed along the way.
	private boolean crossesFunctionBoundary(String name) {
		for(ASTNode n = this; n != null; n = n.getParent()) {
			if(n instanceof ASTVarContainer vc && vc.getOwnVariable(name) != null) {
				return false;
			}
			if(n instanceof ASTFunction) {
				return true;
			}
		}
		return false;
	}
	private boolean isVarDecl() {
		ASTNode parent = getParent();
		if(parent instanceof ASTObjectLiteral.Initializer) {
			return true;
		}
		if(parent instanceof ASTArrayLiteral.Initializer) {
			return true;
		}
		return false;
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