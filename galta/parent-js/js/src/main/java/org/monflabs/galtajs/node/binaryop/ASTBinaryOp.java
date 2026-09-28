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
package org.monflabs.galtajs.node.binaryop;

import org.monflabs.galtajs.node.ASTNode;
import org.monflabs.galtajs.optimizer.JSOptimizerContext;
import org.monflabs.galtajs.parser.Token;
import org.monflabs.util.StringFormat;


/**
 * Abstract binary operation Node.
 */
public abstract class ASTBinaryOp extends ASTNode {

	protected ASTNode leftNode;
	protected ASTNode rightNode;

	protected ASTBinaryOp(Token t, ASTNode leftNode, ASTNode rightNode) {
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
	public boolean isSequence() {
		return leftNode.isSequence() || rightNode.isSequence();
	}	

	@Override
	public boolean isConstant(JSOptimizerContext context) {
		return leftNode.isConstant(context) && rightNode.isConstant(context);
	}	

	@Override
	public STATEMENT_TYPE getStatementType() {
		return STATEMENT_TYPE.EXPRESSION;
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