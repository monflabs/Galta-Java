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

import java.util.List;

import org.monflabs.galtajs.node.ASTNode;
import org.monflabs.galtajs.node.call.ASTBaseCall;
import org.monflabs.galtajs.parser.Token;


/**
 * Abstract new operator
 */
public abstract class ASTNew extends ASTBaseCall {

	private ASTNode typeNode;
	
	protected ASTNew(Token t, ASTNode typeNode,  List<ASTNode> parameters) {
		super(t,parameters);
		this.typeNode = assignParent(typeNode);
	}
	
	@Override
	public int getChildCount() {
		return super.getChildCount()+1;
	}
	@Override
	public ASTNode getChild(int index) {
		switch(index) {
			case 0 ->	{ return typeNode; }
			default ->	{ return super.getChild(index-1); }
		}
	}
	@Override
	protected void _setChild(int index, ASTNode node) {
		switch(index) {
			case 0 ->	{ this.typeNode = node; }
			default ->  { super._setChild(index-1,node); }
		}
	}
	
	public ASTNode getTypeNode() {
		return typeNode;
	}
}