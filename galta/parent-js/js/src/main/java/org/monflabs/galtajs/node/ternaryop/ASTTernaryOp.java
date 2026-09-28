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
package org.monflabs.galtajs.node.ternaryop;

import org.monflabs.galtajs.node.ASTNode;
import org.monflabs.galtajs.optimizer.JSOptimizerContext;
import org.monflabs.galtajs.parser.Token;




/**
 * Abstract ternary operation Node.
 */
public abstract class ASTTernaryOp extends ASTNode {

	private ASTNode op1Node;
	private ASTNode op2Node;
	private ASTNode op3Node;

	protected ASTTernaryOp(Token t, ASTNode op1Node, ASTNode op2Node, ASTNode op3Node) {
		super(t);
		this.op1Node = assignParent(op1Node);
		this.op2Node = assignParent(op2Node);
		this.op3Node = assignParent(op3Node);
	}

	@Override
	public int getChildCount() {
		return super.getChildCount()+3;
	}
	@Override
	public ASTNode getChild(int index) {
		switch(index) {
			case 0 ->	{ return op1Node; }
			case 1 ->	{ return op2Node; }
			case 2 ->	{ return op3Node; }
			default ->	{ return super.getChild(index-3); }
		}
	}
	@Override
	protected void _setChild(int index, ASTNode node) {
		switch(index) {
			case 0 ->	{ this.op1Node = node; }
			case 1 ->	{ this.op2Node  = node; }
			case 2 ->	{ this.op3Node  = node; }
			default ->  { super._setChild(index-3,node); }
		}
	}	
	
	@Override
	public boolean isConstant(JSOptimizerContext context) {
		return (op1Node==null || op1Node.isConstant(context) ) && (op2Node==null || op2Node.isConstant(context)) && (op3Node==null || op3Node.isConstant(context));
	}	

	@Override
	public STATEMENT_TYPE getStatementType() {
		return STATEMENT_TYPE.EXPRESSION;
	}	

	public ASTNode getOp1Node() {
		return op1Node;
	}

	public ASTNode getOp2Node() {
		return op2Node;
	}

	public ASTNode getOp3Node() {
		return op3Node;
	}

}