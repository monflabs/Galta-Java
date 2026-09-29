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
package org.monflabs.galtajs.node.unaryop;

import org.monflabs.galtajs.JSParseException;
import org.monflabs.galtajs.node.ASTIdentifier;
import org.monflabs.galtajs.node.ASTNode;
import org.monflabs.galtajs.node.ASTVarContainer;
import org.monflabs.galtajs.node.ASTVarContainer.VariableDef;
import org.monflabs.galtajs.node.control.ASTFor;
import org.monflabs.galtajs.node.control.ASTFor_;
import org.monflabs.galtajs.parser.Token;
import org.monflabs.galtajs.rt.interpreter.JSInterpretedRuntimeContext.VAR_TYPE;




/**
 * Abstract unary inc/dec Node.
 */
public abstract class ASTAbstractIncDec extends ASTUnaryOp {

	// Optimization for a variable++
	// Very common in loops
	protected String id;
	
	protected ASTAbstractIncDec(Token t, ASTNode node) {
		super(t,node);
		if(node instanceof ASTIdentifier i) {
			this.id = i.getId();
		}
	}
	
	@Override
	protected void init(InitContext initContext) {
		super.init(initContext);
		
		// For now - we should find a better way to validate the assigned nodes???
		ASTNode leftNode = getNode();
		checkAssignmentTarget(leftNode, AssignmentUse.COMPOUND, initContext.isGenuinelyStrict());
		if(leftNode instanceof ASTIdentifier id) {
			VariableDef v = findParentNodeByClass(ASTVarContainer.class).findVariable(id.getId());
			if(v==null) {
				// Done by ASTIdentifier in evaluateAssign()
				// See: ASTAbstractAssign or each inc/dec node when optimized
//				if(getEnvironment().mustDeclareAllVariables()) {
//					throw new JSParseException(null, this, "Variable {0} does not exist", id.getId());
//				}
//				v = findParentNodeByClass(IContextVarContainer.class).addVarDeclaration(id.getId(),VAR_TYPE.AUTO,null);
			} else {
				// NOT extended to VAR_TYPE.USING - see ASTAbstractAssign's
				// matching check for why: using/await-using reassignment
				// must be a runtime TypeError, not this static early error,
				// even in the statically-resolvable same-scope case.
				if(v.getVarType()==VAR_TYPE.CONST) {
					// Mirrors ASTAbstractAssign's own "only a static early error
					// when statically-obvious" restriction (which that class
					// implements via crossesFunctionBoundary()): a for-loop's own
					// `const` head binding (`for (const i = ...; ...; i++)`,
					// `for (const x in/of ...) { x++ }`) gets a genuinely NEW
					// per-iteration environment copy each iteration (spec's
					// CreatePerIterationEnvironment) - a runtime concept no static
					// analysis can safely account for, exactly like a reference
					// reached only through a nested closure. Confirmed via
					// test262's const-invalid-assignment-{next-expression-for,
					// statement-body-for-in,statement-body-for-of}.js, which all
					// need this to surface as a RUNTIME TypeError (assert.throws
					// wraps the call), not a SyntaxError aborting the whole script
					// before the assertion ever runs. Falls through to
					// ASTIdentifier.evaluateAssign()'s runtime CONST check (the
					// same path ASTAbstractAssign itself already relies on for its
					// own deferred cases).
					if(!declaredByForLoopHead(id.getId())) {
						throw new JSParseException(null, this, "Cannot increment or decrement the constant {0}", id.getId());
					}
				}
			}
		}
	}

	// True when the nearest ASTVarContainer that actually owns `name` (walking
	// up from this node, exactly like ASTNode.findVariable()'s own walk) is a
	// for-loop's own head (ASTFor/ASTFor_) rather than an ordinary block/
	// program/function scope.
	private boolean declaredByForLoopHead(String name) {
		for(ASTNode n = this; n != null; n = n.getParent()) {
			if(n instanceof ASTVarContainer vc && vc.getOwnVariable(name) != null) {
				return n instanceof ASTFor || n instanceof ASTFor_;
			}
		}
		return false;
	}


	@Override
	public STATEMENT_TYPE getStatementType() {
		return STATEMENT_TYPE.STATEMENT;
	}

	@Override
	public boolean isSequence() {
		return getNode().isSequence();
	}

}