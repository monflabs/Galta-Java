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
import org.monflabs.galtajs.parser.Token;
import org.monflabs.galtajs.rt.JSResult;
import org.monflabs.galtajs.rt.interpreter.InterpretedUnitRuntimeContext.Signal;
import org.monflabs.galtajs.rt.interpreter.JSInterpretedRuntimeContext;
import org.monflabs.galtajs.transpiler.TranspilerJavaBuilder;
import org.monflabs.galtajs.transpiler.context.JSTranspilerGeneratorContext;
import org.monflabs.galtajs.types.JSType;
import org.monflabs.galtajs.util.JavaBuilder;


/**
 * Keep the comment in the dom.
 * This is not used at runtime but helps regenerating the source code from its compiled form.
 */
public class ASTComment extends ASTNode {

	private boolean singleLine;
	private String comment;
	
	public ASTComment(Token t, boolean singleLine, String comment) {
		super(t);
		this.singleLine = singleLine;
		this.comment = comment;
	}
	
	public boolean isSingleLine() {
		return singleLine;
	}
	
	public String getComment() {
		return comment;
	}

	@Override
	public Signal evaluate(JSInterpretedRuntimeContext context, JSResult result) {
		return Signal.NONE;
	}

    @Override
	public JSType getReturnedType() {
    	return JSType.UNKNOWN;
	}
	
    @Override
	public void transpileJavaStatement(JSTranspilerGeneratorContext jsContext, TranspilerJavaBuilder b) {
    	if(singleLine) {
    		b.println("// {0}", comment);
    	} else {
    		b.println("/*");
    		b.println("  {0}", comment);
    		b.println("*/");
    	}
    }

    
    @Override
	public void decompileStatement(JavaBuilder b) {
    	if(singleLine) {
    		b.comment("{0}", comment);
    	} else {
    		b.commentMulti("  {0}", comment);
    	}
	}
}
