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

import org.monflabs.galtajs.node.ASTIdentifier;
import org.monflabs.galtajs.node.ASTNode;
import org.monflabs.galtajs.node.ASTVarContainer;
import org.monflabs.galtajs.parser.Token;




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
		// Incrementing a const parses: it is a runtime TypeError, checked by
		// ASTIdentifier.evaluateAssign()
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