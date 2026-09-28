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

import org.monflabs.galtajs.parser.Token;
import org.monflabs.galtajs.rt.JSResult;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.builtins.standard.StandardLibrary;
import org.monflabs.galtajs.rt.interpreter.InterpretedUnitRuntimeContext.Signal;
import org.monflabs.galtajs.rt.interpreter.JSInterpretedRuntimeContext;
import org.monflabs.galtajs.transpiler.JSTranspiler;
import org.monflabs.galtajs.transpiler.context.JSTranspilerGeneratorContext;
import org.monflabs.galtajs.types.JSType;
import org.monflabs.util.StringFormat;


/**
 * Super Node.
 */
public class ASTSuperMember extends ASTNode {

	public ASTSuperMember(Token t) {
		super(t);
	}

	@Override
	public STATEMENT_TYPE getStatementType() {
		return STATEMENT_TYPE.EXPRESSION;
	}	

	@Override
	public Signal evaluate(JSInterpretedRuntimeContext context, JSResult result) {
		try {
			result.setValue(RuntimeUtil.getSuper(context));
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
    	// SuperProperty (spec 13.3.7.1 MakeSuperPropertyReference step 1-2):
    	// GetThisBinding() must run - throwing ReferenceError if `this` is
    	// still TDZ - BEFORE the super base (home object's prototype) is even
    	// resolved, let alone a computed property key evaluated. Since this
    	// node IS the base of any super.prop/super[expr] expression (see
    	// ChainingNode.transpileChain: this is always evaluated first,
    	// textually, ahead of the member name/computed key), checking here
    	// covers both syntactic forms in the correct order. Gated on derived-
    	// constructor context for the same reason as ASTThis's own check -
    	// the same UNDEFINED sentinel is also `this`'s legitimate value for
    	// an ordinary function/method called with no receiver.
    	if(StandardLibrary.isCallerInDerivedClassConstructor(this)) {
    		// thisRef() (not the local `_this`): super() reassigns that
    		// parameter - see JSTranspiler.thisRef()/ASTSuperCtor.
    		return StringFormat.format("getSuper({0},{1})",JSTranspiler.MAIN_CONTEXT,JSTranspiler.thisRef(this));
    	}
    	return StringFormat.format("getSuper({0})",JSTranspiler.MAIN_CONTEXT);
    }
    
    @Override
	public String decompileExpression() {
    	return "super";
    }
}