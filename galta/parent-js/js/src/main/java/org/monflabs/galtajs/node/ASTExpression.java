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
package org.monflabs.galtajs.node;

import java.util.List;

import org.monflabs.galtajs.rt.JSResult;
import org.monflabs.galtajs.rt.interpreter.InterpretedUnitRuntimeContext.Signal;
import org.monflabs.galtajs.rt.interpreter.JSInterpretedRuntimeContext;
import org.monflabs.galtajs.transpiler.context.JSTranspilerGeneratorContext;
import org.monflabs.galtajs.types.JSType;


/**
 * Expression.
 */
public class ASTExpression extends ASTNode {

	private ASTNode[] nodes;

	public ASTExpression(List<ASTNode> nodes) {
		super(null);
		this.nodes = assignParent(nodes);
	}

	@Override
	public int getChildCount() {
		return super.getChildCount()+nodes.length;
	}
	@Override
	public ASTNode getChild(int index) {
		if(index<nodes.length) {
			return nodes[index];
		}
		return super.getChild(index-nodes.length);
	}
	@Override
	protected void _setChild(int index, ASTNode node) {
		if(index<nodes.length) {
			nodes[index] = node;
			return;
		}
		super._setChild(index-nodes.length, node);
	}
	
	public ASTNode[] getNodes() {
		return nodes;
	}
	
	@Override
	public Signal evaluate(JSInterpretedRuntimeContext context, JSResult result) {
		try {
			int count = nodes.length;
			for(int i=0; i<count; i++) {
				ASTNode node = nodes[i];
				Signal s = node.evaluate(context,result);
				if(s!=Signal.NONE) {
					return s;
				}
			}
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
	public String transpileJavaExpression(JSTranspilerGeneratorContext jsContext) {
    	StringBuilder b = new StringBuilder();
    	b.append("comma(");
		int count = nodes.length;
		for(int i=0; i<count; i++) {
			if(i>0) {
				b.append(',');
			}
			ASTNode node = nodes[i];
			String n = node.transpileJavaExpression(jsContext);
			b.append(n);
		}
    	b.append(")");
		return b.toString();
    }
    
    @Override
	public String decompileExpression() {
    	StringBuilder b = new StringBuilder();
		int count = nodes.length;
		for(int i=0; i<count; i++) {
			if(i>0) {
				b.append(',');
			}
			b.append(nodes[i].decompileExpression());
		}
		return b.toString();
	}
}
