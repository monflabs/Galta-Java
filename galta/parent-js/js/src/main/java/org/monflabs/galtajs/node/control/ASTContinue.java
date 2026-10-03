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
import org.monflabs.galtajs.rt.interpreter.InterpretedUnitRuntimeContext.Signal.Type;
import org.monflabs.galtajs.rt.interpreter.JSInterpretedRuntimeContext;
import org.monflabs.galtajs.transpiler.TranspilerJavaBuilder;
import org.monflabs.galtajs.transpiler.context.JSTranspilerGeneratorContext;
import org.monflabs.galtajs.types.JSType;
import org.monflabs.galtajs.util.JavaBuilder;
import org.monflabs.util.StringUtil;




/**
 * continue statement node.
 */
public class ASTContinue extends ASTNode {

	private String label;

	public ASTContinue(Token t, String label) {
		super(t);
		this.label = label;
	}

	@Override
	public String getNodeString() {
		return label!=null ? label : "";
	}

	public String getLabel() {
		return label;
	}

	@Override
	public Signal evaluate(JSInterpretedRuntimeContext context, JSResult result) {
		try {
			if(label!=null) {
				return new Signal(Type.CONTINUE,label);
			}
			return Signal._CONTINUE;
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
    	// Leaving a region (a method split out of a large function): its caller
    	// performs the continue - see TranspilerMethodSplitter
    	int jump = org.monflabs.galtajs.transpiler.context.TranspilerGeneratorRegionContext.findRegionJump(jsContext, this);
    	if(jump>=0) {
    		b.println("return org.monflabs.galtajs.rt.transpiler.JSTranspiledRegion.JUMPS[{0}];", Integer.toString(jump));
    		return;
    	}
    	if(StringUtil.isEmpty(label)) {
    		b.println("continue;");
    	} else {
    		b.println("continue {0};", ILabeledNode.javaLabel(label));
    	}
    }

    @Override
	public void decompileStatement(JavaBuilder b) {
    	if(StringUtil.isEmpty(label)) {
        	b.append("continue;");
    	} else {
    		b.append("continue {0};", label);
    	}
    }
}