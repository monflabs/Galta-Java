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

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.node.ASTNode;
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
 * synchronized() statement node.
 */
public class ASTSynchronized extends ASTNode {

	private ASTNode syncNode;
	private ASTNode bodyNode;

	public ASTSynchronized(Token t, ASTNode syncNode, ASTNode bodyNode) {
		super(t);
		this.syncNode = assignParent(syncNode);
		this.bodyNode = assignParent(bodyNode);
	}

	@Override
	public int getChildCount() {
		return super.getChildCount()+2;
	}
	@Override
	public ASTNode getChild(int index) {
		switch(index) {
			case 0 ->	{ return syncNode; }
			case 1 ->	{ return bodyNode; }
			default ->	{ return super.getChild(index-2); }
		}
	}
	@Override
	protected void _setChild(int index, ASTNode node) {
		switch(index) {
			case 0 ->	{ this.syncNode = node; }
			case 1 ->	{ this.bodyNode  = node; }
			default ->  { super._setChild(index-2,node); }
		}
	}
	public ASTNode getBodyNode() {
		return bodyNode;
	}

	@Override
	public Signal evaluate(JSInterpretedRuntimeContext context, JSResult result) {
		try {
			context.getGlobalContext().checkInterrupted();

			Object sync = lockTarget(context.getEnvironment(), syncNode.evaluateValue(context, result));
			Signal signal;
			synchronized(sync) {
				signal = bodyNode.evaluate(context, result);
			}
			// A return/break/continue inside the block leaves it, like in transpiled code
			if(signal!=Signal.NONE) {
				return signal;
			}
			result.setUndefined();
			return Signal.NONE;
		} catch(Throwable ex) {
			throw fillInStackTrace(ex);
		}		
	}

	// The object locked by synchronized(): an object, never null/undefined or a
	// primitive value (a shared, interned or boxed-on-the-fly instance)
	public static Object lockTarget(JSEnvironment env, Object value) {
		if(!RuntimeUtil.isObject(env, value)) {
			throw RuntimeUtil.typeError("synchronized() requires an object, not {0}", value==RuntimeUtil.UNDEFINED ? "undefined" : value==null ? "null" : "a primitive value");
		}
		return value;
	}

    @Override
	public JSType getReturnedType() {
    	return JSType.UNKNOWN;
	}
    

    @Override
    public void transpileJavaStatement(JSTranspilerGeneratorContext jsContext, TranspilerJavaBuilder b) {
    	JSTranspilerGeneratorContext syncContext = new TranspilerGeneratorBlockContext(jsContext); 

		b.println("synchronized({0}.lockTarget({1},{2})) {", ASTSynchronized.class.getName(), JSTranspiler.MAIN_ENVIRONMENT, JSTranspiler.asValue(syncContext, syncNode));
    	b.incIndent();
		if(bodyNode instanceof ASTBlock block) {
			block.transpileJavaStatementNoBrace(syncContext,b);
		} else {
			b.debugLocation(bodyNode);
			bodyNode.transpileJavaStatement(syncContext, b);
		}
    	b.decIndent();
		b.println("}");
    }
    
    @Override
	public void decompileStatement(JavaBuilder b) {
    	b.append("synchronized(");
       	b.append(syncNode.decompileExpression());
    	b.append(") {\n");
    	
    	b.incIndent();
    	decompileBlockStatements(b, bodyNode );
    	b.decIndent();

    	b.append("}");
	}

}