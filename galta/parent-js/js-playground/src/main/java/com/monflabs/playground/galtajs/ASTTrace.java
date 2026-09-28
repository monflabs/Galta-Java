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
package com.monflabs.playground.galtajs;

import org.monflabs.galtajs.node.ASTNode;
import org.monflabs.galtajs.node.NoopNode;
import org.monflabs.galtajs.optimizer.JSOptimizerContext;
import org.monflabs.galtajs.rt.JSResult;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.builtins.standard.console.ConsoleToString;
import org.monflabs.galtajs.rt.interpreter.InterpretedUnitRuntimeContext.Signal;
import org.monflabs.galtajs.rt.interpreter.JSInterpretedRuntimeContext;
import org.monflabs.galtajs.transpiler.TranspilerJavaBuilder;
import org.monflabs.galtajs.transpiler.context.JSTranspilerGeneratorContext;
import org.monflabs.galtajs.util.JavaBuilder;
import org.monflabs.playground.ExecutionContext;


/**
 * Tracing node.
 * 
 */
public class ASTTrace extends ASTNode implements NoopNode {
	
	private ExecutionContext executionContext;
	private ASTNode node;
	
	public ASTTrace(ExecutionContext executionContext, ASTNode node) {
		super(null);
		this.executionContext = executionContext;
		this.node = assignParent(node);
	}
	
	@Override
	public String toString() {
		return "ASTTrace: "+getNode().toString();
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
	public ASTNode getNode() {
		return node;
	}

	@Override
	public boolean isConstant(JSOptimizerContext context) {
		return node.isConstant(context);
	}	

	@Override
	public boolean isSequence() {
		return node.isSequence();
	}	
	
	@Override
	public Signal evaluate(JSInterpretedRuntimeContext context, JSResult result) {
		Signal s = node.evaluate(context,result);
		if(s!=Signal.NONE) {
			return s;
		}
		Object v = result.deref();
		if(!RuntimeUtil.isUndefined(v)) {
			String msg = ConsoleToString.toString(context.getEnvironment(), v);
			executionContext.printlnAtLine(node.getBeginLine(), msg);
		}
		return s;
	}

	
	//
	// Decompiler
	//
	
    @Override
	public void decompileStatement(JavaBuilder b) {
    	getNode().decompileStatement(b);
	}
    @Override
	public String decompileExpression() {
    	return getNode().decompileExpression();
	}

	
	//
	// Transpiler
	//
	
	@Override
    public void transpileJavaStatement(JSTranspilerGeneratorContext jsContext, TranspilerJavaBuilder b) {
		getNode().transpileJavaStatement(jsContext,b);
	}
    
    @Override
	public String transpileJavaExpression(JSTranspilerGeneratorContext jsContext) {
    	return getNode().transpileJavaExpression(jsContext);
    }

    @Override
	public String transpileTypeofExpression(JSTranspilerGeneratorContext jsContext) {
    	return getNode().transpileTypeofExpression(jsContext);
    }

    @Override
    public String transpileDeleteExpression(JSTranspilerGeneratorContext jsContext) {
    	return getNode().transpileDeleteExpression(jsContext);
    }
    
    @Override
	public String transpileJavaAssignment(JSTranspilerGeneratorContext jsContext, ASSIGN_TYPE type, String rightValue, boolean sequence, boolean returnOriginalValue) {
    	return getNode().transpileJavaAssignment(jsContext,type,rightValue,sequence,returnOriginalValue);
    }
}
