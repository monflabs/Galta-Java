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
package org.monflabs.galtajs.node.call;

import org.monflabs.galtajs.node.ASTNode;
import org.monflabs.galtajs.parser.Token;
import org.monflabs.galtajs.rt.JSResult;
import org.monflabs.galtajs.rt.interpreter.InterpretedUnitRuntimeContext.Signal;
import org.monflabs.galtajs.rt.interpreter.JSInterpretedRuntimeContext;
import org.monflabs.galtajs.transpiler.context.JSTranspilerGeneratorContext;


/**
 * Spread argument.
 */
public class ASTCallSpread extends ASTNode {
	
	private ASTNode node;

	public ASTCallSpread(Token t, ASTNode node) {
		super(t);
		this.node = assignParent(node);
	}
	
	public ASTNode getNode() {
		return node;
	}

	@Override
	public String getNodeString() {
		return "..."+node.getNodeString();
	}

	@Override
	public int getChildCount() {
		return super.getChildCount()+1;
	}
	@Override
	public ASTNode getChild(int index) {
		switch(index) {
			case 0 ->		{ return node; }
			default ->		{ return super.getChild(index-1); }
		}
	}
	@Override
	protected void _setChild(int index, ASTNode node) {
		switch(index) {
			case 0 ->	{ this.node = node; }
			default ->  { super._setChild(index-1,node); }
		}
	}
	
	//
	// Delegate to the wrapped node
	//
	
	@Override
	public Signal evaluate(JSInterpretedRuntimeContext context, JSResult result) {
    	return getNode().evaluate(context,result);
	}

    @Override
	public String transpileJavaExpression(JSTranspilerGeneratorContext jsContext) {
    	return getNode().transpileJavaExpression(jsContext);
    }
    
    @Override
	public String decompileExpression() {
    	StringBuilder b = new StringBuilder();
		b.append("...");
		b.append(node.decompileExpression());
		return b.toString();
	}
}
