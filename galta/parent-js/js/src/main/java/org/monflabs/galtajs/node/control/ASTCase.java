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
import org.monflabs.galtajs.parser.Token;
import org.monflabs.galtajs.rt.JSResult;
import org.monflabs.galtajs.rt.interpreter.InterpretedUnitRuntimeContext.Signal;
import org.monflabs.galtajs.rt.interpreter.JSInterpretedRuntimeContext;
import org.monflabs.galtajs.transpiler.TranspilerJavaBuilder;
import org.monflabs.galtajs.transpiler.context.JSTranspilerGeneratorContext;
import org.monflabs.galtajs.types.JSType;


/**
 * Case statement.
 */
public class ASTCase extends ASTNode {

	private ASTNode exprNode;
	private ASTNode[] statements;

	public ASTCase(Token t, ASTNode exprNode, List<ASTNode> statements) {
		super(t);
		this.exprNode = assignParent(exprNode);
		this.statements = assignParent(statements);
	}

	public ASTNode getExprNode() {
		return exprNode;
	}
	
	public ASTNode[] getStatements() {
		return statements;
	}

	public void setStatements(ASTNode[] statements) {
		this.statements = statements;
	}

	@Override
	public int getChildCount() {
		return super.getChildCount()+statements.length+1;
	}
	@Override
	public ASTNode getChild(int index) {
		if(index<statements.length) {
			return statements[index];
		}
		if(index==statements.length) {
			return exprNode;
		}
		return super.getChild(index-statements.length-1);
	}
	@Override
	protected void _setChild(int index, ASTNode node) {
		if(index<statements.length) {
			statements[index] = node;
			return;
		}
		if(index==statements.length) {
			this.exprNode = node;
			return;
		}
		super._setChild(index-statements.length-1,node);
	}

	@Override
	public Signal evaluate(JSInterpretedRuntimeContext context, JSResult result) {
		try {
			int count = statements.length;
			for(int i=0; i<count; i++) {
				Signal s = statements[i].evaluate(context,result);
				if(s!=Signal.NONE) {
					return s;
				}
			}
			// result contains the last case evaluation
			return Signal.NONE;
		} catch(Throwable ex) {
			throw fillInStackTrace(ex);
		}		
	}

    @Override
	public JSType getReturnedType() {
    	return JSType.UNKNOWN;
    }
    
    @Override
	public void transpileJavaStatement(JSTranspilerGeneratorContext jsContext, TranspilerJavaBuilder b) {
		b.incIndent();
		if(jsContext.getMainContext().getWholeListRegion(this)!=null || jsContext.getMainContext().getSplitRegions(this)!=null) {
			// Statements of a large function moved to regions (see TranspilerMethodSplitter)
			ASTBlock.transpileBlockStatements(jsContext, b, this, statements);
			b.decIndent();
			return;
		}
		int count = statements.length;
		for(int i=0; i<count; i++) {
			ASTNode node = statements[i];
			b.debugLocation(node);
			node.transpileJavaStatement(jsContext, b);
		}
		b.decIndent();
    }
}