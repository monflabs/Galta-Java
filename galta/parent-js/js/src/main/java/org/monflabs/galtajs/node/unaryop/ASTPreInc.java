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

import org.monflabs.galtajs.node.ASTNode;
import org.monflabs.galtajs.parser.Token;
import org.monflabs.galtajs.rt.JSResult;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.interpreter.InterpretedUnitRuntimeContext.Signal;
import org.monflabs.galtajs.rt.interpreter.JSInterpretedRuntimeContext;
import org.monflabs.galtajs.transpiler.context.JSTranspilerGeneratorContext;
import org.monflabs.galtajs.types.JSType;


/**
 * Post increment.
 */
public class ASTPreInc extends ASTAbstractIncDec {

	public ASTPreInc(Token t, ASTNode node) {
		super(t,node);
	}

	@Override
	public Signal evaluate(JSInterpretedRuntimeContext context, JSResult result) {
		try {
//			// Optimization in case of an identifier
//			ASTNode node;
//			if((node=getNode()) instanceof ASTIdentifier id) {
//				VarAccessor e = context.resolveIdentifierEntry(id.getId());
//				if(e!=null) {
//					Object value = e.getValue();
//					Object newValue = RuntimeUtil.incNumber(context,value);
//					e.setValue(newValue);
//					result.setValue(newValue);
//					return Signal.NONE;
//				}
//			}
			
			getNode().evaluateAssign(context, null, 
					(v) -> RuntimeUtil.incNumber(context.getEnvironment(),v), 
					result, 
					null
			);
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
    	return getNode().transpileJavaAssignment(jsContext, ASSIGN_TYPE.PREINC, null, getNode().isSequence(), false);
    }
    
    @Override
	protected String decompileOperator() {
    	return "++";
    }
}
