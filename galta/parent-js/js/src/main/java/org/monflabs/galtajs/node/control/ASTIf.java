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

import org.monflabs.galtajs.node.ASTNode;
import org.monflabs.galtajs.node.debug.DebuggableNode;
import org.monflabs.galtajs.parser.Token;
import org.monflabs.galtajs.rt.JSResult;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.interpreter.InterpretedUnitRuntimeContext.Signal;
import org.monflabs.galtajs.rt.interpreter.JSInterpretedRuntimeContext;
import org.monflabs.galtajs.transpiler.JSTranspiler;
import org.monflabs.galtajs.transpiler.TranspilerJavaBuilder;
import org.monflabs.galtajs.transpiler.context.JSTranspilerGeneratorContext;
import org.monflabs.galtajs.transpiler.context.TranspilerGeneratorBlockContext;
import org.monflabs.galtajs.types.JSType;
import org.monflabs.galtajs.util.JavaBuilder;




/**
 * If statement node.
 */
public class ASTIf extends ASTNode implements DebuggableNode {

	private ASTNode testNode;
	private ASTNode thenNode;
	private ASTNode elseNode;

	public ASTIf(Token t, ASTNode testNode, ASTNode thenNode, ASTNode elseNode) {
		super(t);
		this.testNode = assignParent(testNode);
		if(thenNode!=null) {
			this.thenNode = assignParent(thenNode);
		}
		if(elseNode!=null) {
			this.elseNode = assignParent(elseNode);
		}
	}
	
	public ASTNode getTestNode() {
		return testNode;
	}
	public ASTNode getThenNode() {
		return thenNode;
	}
	public ASTNode getElseNode() {
		return elseNode;
	}

	public void setTestNode(ASTNode node) {
		this.testNode = assignParent(node);
	}
	public void setThenNode(ASTNode node) {
		this.thenNode = assignParent(node);
	}
	public void setElseNode(ASTNode node) {
		this.elseNode = assignParent(node);
	}
	
	@Override
	public int getChildCount() {
		return super.getChildCount()+3;
	}
	@Override
	public ASTNode getChild(int index) {
		switch(index) {
			case 0 ->	{ return testNode; }
			case 1 ->	{ return thenNode; }
			case 2 ->	{ return elseNode; }
			default ->	{ return super.getChild(index-3); }
		}
	}
	@Override
	protected void _setChild(int index, ASTNode node) {
		switch(index) {
			case 0 ->	{ this.testNode = node; }
			case 1 ->	{ this.thenNode  = node; }
			case 2 ->	{ this.elseNode  = node; }
			default ->  { super._setChild(index-3,node); }
		}
	}

	@Override
	public Signal evaluate(JSInterpretedRuntimeContext context, JSResult result) {
		try {
			// Evaluate the test into its own JSResult (matching how the loop
			// constructs evaluate their test expressions) so it can't pollute
			// the completion value with e.g. the test's own boolean result
			// when the taken branch's own completion turns out to be empty
			// (an abrupt break/continue with no value of its own, which must
			// stay empty for an enclosing loop's UpdateEmpty to fill in).
			JSResult testResult = new JSResult();
			boolean value = RuntimeUtil.toBoolean(context.getEnvironment(),testNode.evaluateValue(context,testResult));
			if(value) {
				// UpdateEmpty(stmtCompletion, undefined) (IfStatement Evaluation,
				// last step): reset to the undefined baseline right before the
				// branch runs, so a branch whose own completion is empty (e.g. a
				// bare break/continue with no value of its own) reads back as
				// undefined instead of leaking whatever `result` held before this
				// if-statement ran (e.g. an enclosing loop's accumulated V).
				result.setUndefined();
				Signal s = thenNode.evaluate(context,result);
				if(s!=Signal.NONE) {
					return s;
				}
			} else {
				if(elseNode!=null) {
					result.setUndefined();
					Signal s = elseNode.evaluate(context,result);
					if(s!=Signal.NONE) {
						return s;
					}
				} else {
					result.setUndefined();
				}
			}
			
			// result contains the last body evaluation
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
		b.println("if({0}) {", JSTranspiler.asBooleanCondition(jsContext,testNode));
		b.incIndent();
		{
			JSTranspilerGeneratorContext ifContext = new TranspilerGeneratorBlockContext(jsContext); 
			if(thenNode instanceof ASTBlock block) {
				block.transpileJavaStatementNoBrace(ifContext,b);
			} else {
				b.debugLocation(thenNode);
				thenNode.transpileJavaStatement(ifContext, b);
			}
		}
		b.decIndent();
		if(elseNode!=null) {
			b.println("} else {", JSTranspiler.asBoolean(jsContext,testNode));
			b.incIndent();
			JSTranspilerGeneratorContext elseContext = new TranspilerGeneratorBlockContext(jsContext); 
			if(elseNode instanceof ASTBlock block) {
				block.transpileJavaStatementNoBrace(elseContext,b);
			} else {
				b.debugLocation(elseNode);
				elseNode.transpileJavaStatement(elseContext, b);
			}
			b.decIndent();
		}
		b.println("}");
	}

    
    @Override
	public void decompileStatement(JavaBuilder b) {
    	b.append("if(");
       	b.append(testNode.decompileExpression());
    	b.append(") {\n");
    	if(thenNode!=null) {
	    	b.incIndent();
	    	decompileBlockStatements(b,thenNode);
	    	b.decIndent();
    	}
    	if(elseNode!=null) {
        	b.append("} else {\n");
        	b.incIndent();
        	decompileBlockStatements(b,elseNode);
        	b.decIndent();
    	}
    	b.append("}");
	}
}
