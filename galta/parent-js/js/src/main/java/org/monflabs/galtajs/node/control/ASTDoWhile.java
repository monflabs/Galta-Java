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

import org.monflabs.galtajs.node.ASTIdentifier;
import org.monflabs.galtajs.node.ASTNode;
import org.monflabs.galtajs.node.call.ASTCall;
import org.monflabs.galtajs.node.debug.DebuggableNode;
import org.monflabs.galtajs.parser.Token;
import org.monflabs.galtajs.rt.JSGlobalContext;
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
import org.monflabs.util.StringUtil;




/**
 * do...while() statement node.
 */
public class ASTDoWhile extends ASTNode implements ILabeledNode, DebuggableNode {

	private String label;
	private ASTNode testNode;
	private ASTNode bodyNode;

	public ASTDoWhile(Token t, ASTNode testNode, ASTNode bodyNode) {
		super(t);
		this.testNode = assignParent(testNode);
		this.bodyNode = assignParent(bodyNode);
	}
	
	@Override
	public String getLabel() {
		return label;
	}

	@Override
	public void setLabel(String label) {
		this.label = label;
	}

	public ASTNode getTestNode() {
		return testNode;
	}

	public ASTNode getBodyNode() {
		return bodyNode;
	}

	@Override
	public int getChildCount() {
		return super.getChildCount()+2;
	}
	@Override
	public ASTNode getChild(int index) {
		switch(index) {
			case 0 ->		{ return testNode; }
			case 1 ->		{ return bodyNode; }
			default ->		{ return super.getChild(index-2); }
		}
	}
	@Override
	protected void _setChild(int index, ASTNode node) {
		switch(index) {
			case 0 ->	{ this.testNode = node; }
			case 1 ->	{ this.bodyNode  = node; }
			default ->  { super._setChild(index-2,node); }
		}
	}

	@SuppressWarnings("incomplete-switch")
	@Override
	public Signal evaluate(JSInterpretedRuntimeContext context, JSResult result) {
		try {
			JSGlobalContext gc = context.getGlobalContext();

			result.setUndefined();
			JSResult tempResult = new JSResult();

loop:		while(true) {
				if(bodyNode!=null) {
					Signal s = bodyNode.evaluate(context,result);
					if(s!=Signal.NONE) {
						switch(s.getType()) {
							case RETURN -> {
								return s;
							}
							case CONTINUE -> {
								String l = s.getLabel(); 
				            	if(!StringUtil.isEmpty(l)) {
				            		if(!StringUtil.equals(label,l)) {
				            			// Continue up the next level
				            			return s;
				            		}
				            	}
				                // Continue the main loop
							}
							case BREAK -> {
								String l = s.getLabel(); 
				            	if(!StringUtil.isEmpty(l)) {
				            		if(!StringUtil.equals(label,l)) {
				            			// Continue up the next level
				            			return s;
				            		}
				            	}
				                // Break the main loop - result already carries the last
				                // completion value seen from the body (UpdateEmpty semantics).
				                break loop;
							}
						}
					}
				}
				boolean value = RuntimeUtil.toBoolean(context.getEnvironment(),testNode.evaluateValue(context,tempResult));
				if(!value) {
					break;
				}
				
				gc.checkInterrupted();
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

	// See ASTFor's identical helper (and its own doc) for the full
	// rationale - duplicated here (a do-while loop has no loop-head
	// declarations of its own to need a needsPerIterationBinding()-style
	// mechanism, so this is used only to gate the body's own block-array
	// hoisting below) rather than shared, matching this file family's
	// established convention.
	private static boolean mayCaptureAcrossIterations(ASTNode node) {
		if(node==null) {
			return false;
		}
		if(node instanceof ASTFunction) {
			return true;
		}
		if(node instanceof ASTCall call && !call.isNullOp()) {
			ASTNode fn = call.getNode();
			if(fn instanceof ASTIdentifier id && "eval".equals(id.getId())) {
				return true;
			}
		}
		int count = node.getChildCount();
		for(int i=0; i<count; i++) {
			if(mayCaptureAcrossIterations(node.getChild(i))) {
				return true;
			}
		}
		return false;
	}

    @Override
	public void transpileJavaStatement(JSTranspilerGeneratorContext jsContext, TranspilerJavaBuilder b) {
    	JSTranspilerGeneratorContext whileContext = new TranspilerGeneratorBlockContext(jsContext);

		// See ASTFor's identical hoisting logic (and its own doc) for the
		// full rationale: a block-scoped let/const declared in the body
		// would otherwise get a fresh Object[] allocated by ASTBlock's
		// transpilerDeclareStatement() every iteration even when nothing
		// captures it across iterations.
		if(bodyNode instanceof ASTBlock hoistableBlock && hoistableBlock.hasDeclaredVariables() && !mayCaptureAcrossIterations(bodyNode)) {
			hoistableBlock.transpilerDeclareArrayOnly(whileContext, b);
		}

		if(StringUtil.isNotEmpty(label)) {
			b.println("{0}:", label);
		}

		b.println("do {");
    	b.incIndent();
		if(bodyNode instanceof ASTBlock block) {
			block.transpileJavaStatementNoBrace(whileContext,b);
		} else {
			b.debugLocation(bodyNode);
			bodyNode.transpileJavaStatement(whileContext, b);
		}
    	b.decIndent();
		b.println("} while({0});", JSTranspiler.asBoolean(whileContext, testNode));
    }
    
    
    @Override
	public void decompileStatement(JavaBuilder b) {
    	if(StringUtil.isNotEmpty(label)) {
        	b.append("{0}: ", label).nl();
    	}
    	b.append("do {\n");
    	b.incIndent();
    	decompileBlockStatements(b,bodyNode);
    	b.decIndent();
    	b.append("} while(");
       	b.append(testNode.decompileExpression());
    	b.append(");\n");
	}
}