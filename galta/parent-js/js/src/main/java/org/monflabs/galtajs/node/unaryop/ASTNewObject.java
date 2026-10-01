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

import org.monflabs.galtajs.node.ASTArrayMember;
import org.monflabs.galtajs.node.ASTNode;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.builtins.Constructor;
import org.monflabs.galtajs.parser.Token;
import org.monflabs.galtajs.rt.JSResult;
import org.monflabs.galtajs.rt.interpreter.InterpretedUnitRuntimeContext.Signal;
import org.monflabs.galtajs.rt.interpreter.JSInterpretedRuntimeContext;
import org.monflabs.galtajs.transpiler.JSTranspiler;
import org.monflabs.galtajs.transpiler.context.JSTranspilerGeneratorContext;
import org.monflabs.galtajs.types.JSType;


/**
 * Instantiation operator (new).
 */
public class ASTNewObject extends ASTNew {

	public ASTNewObject(Token t, ASTNode typeNode, List<ASTNode> parameters) {
		super(t,typeNode,parameters);
	}

	@Override
	public Signal evaluate(JSInterpretedRuntimeContext context, JSResult result) {
		try {
			Object value;
			if(getTypeNode() instanceof ASTArrayMember m && m.getPlainSingleIndexNode()!=null) {
				// new X[n]: a Java array when X is a Java class (GaltaJS)
				Object base = m.getNode().evaluateValue(context,result);
				value = base instanceof Constructor c && c.canConstructArray()
						? new RuntimeUtil.JavaArrayAllocation(c,m.getSingleIndex(context,base))
						: m.getSingleValue(context,base);
			} else {
				value = getTypeNode().evaluateValue(context,result);
			}
			Object obj = constructObject(context, value, result);
			result.setValue(obj);
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
    	StringBuilder b = new StringBuilder(64);
    	b.append("newObject(");
    	if(getTypeNode() instanceof ASTArrayMember m && m.getPlainSingleIndexNode()!=null) {
    		b.append("newMemberTarget(");
    		b.append(JSTranspiler.asValue(jsContext, m.getNode()));
    		b.append(",");
    		b.append(JSTranspiler.asValue(jsContext, m.getPlainSingleIndexNode()));
    		b.append(")");
    	} else {
    		b.append(JSTranspiler.asValue(jsContext, getTypeNode()));
    	}

		ASTNode[] parameters = getParameters();
    	if(parameters.length>0) {
    		b.append(",");
        	b.append(transpileParams(jsContext,parameters, hasSpread(),null));
    	}
    	b.append(")");
    	return b.toString();
    }
    
    @Override
	public String decompileExpression() {
    	StringBuilder b = new StringBuilder();
		b.append("new ");
		b.append(getTypeNode().decompileExpression());
		b.append("(");
		ASTNode[] parameters = getParameters();
    	if(parameters!=null && parameters.length>0) {
    		for(int i=0; i<parameters.length; i++) {
    			if(i>0) {
    				b.append(", ");
    			}
				b.append(parameters[i].decompileExpression());
    		}
    	}
		b.append(")");
		return b.toString();
	}
}
