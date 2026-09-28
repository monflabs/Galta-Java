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

import org.monflabs.galtajs.node.ASTNode;
import org.monflabs.galtajs.optimizer.JSOptimizerContext;
import org.monflabs.galtajs.parser.Token;
import org.monflabs.util.StringFormat;




/**
 * Abstract unary operation Node.
 */
public abstract class ASTUnaryOp extends ASTNode {

	private ASTNode node;

	protected ASTUnaryOp(Token t, ASTNode node) {
		super(t);
		this.node = assignParent(node);
	}

	@Override
	public int getChildCount() {
		return super.getChildCount()+1;
	}
	@Override
	public ASTNode getChild(int index) {
		switch(index) {
			case 0 ->	{ return node; }
			default ->	{ return super.getChild(index-1); }
		}
	}
	@Override
	protected void _setChild(int index, ASTNode node) {
		switch(index) {
			case 0 ->	{ this.node = node; }
			default ->  { super._setChild(index-1,node); }
		}
	}

	@Override
	public boolean isConstant(JSOptimizerContext context) {
		return node.isConstant(context);
	}	

	@Override
	public STATEMENT_TYPE getStatementType() {
		return STATEMENT_TYPE.EXPRESSION;
	}	
	
	public ASTNode getNode() {
		return node;
	}
	
    @Override
	public String decompileExpression() {
		return StringFormat.format("{0}{1}", decompileOperator(), node.decompileExpression());
	}
    protected abstract String decompileOperator();

}