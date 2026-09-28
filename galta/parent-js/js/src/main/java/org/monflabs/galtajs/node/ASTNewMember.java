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

import java.util.function.Function;

import org.monflabs.galtajs.parser.Token;
import org.monflabs.galtajs.rt.JSFunctionContext;
import org.monflabs.galtajs.rt.JSResult;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.builtins.Constructor;
import org.monflabs.galtajs.rt.interpreter.InterpretedUnitRuntimeContext.Signal;
import org.monflabs.galtajs.rt.interpreter.JSInterpretedRuntimeContext;
import org.monflabs.galtajs.transpiler.JSTranspiler;
import org.monflabs.galtajs.transpiler.context.JSTranspilerGeneratorContext;
import org.monflabs.galtajs.types.JSType;
import org.monflabs.util.StringFormat;
import org.monflabs.util.StringUtil;


/**
 * Object Member Node.
 */
public class ASTNewMember extends ASTNode {
	

	public ASTNewMember(Token t, String memberName) {
		super(t);
		// Only 'target' is allowed as member name for new.
		if(!StringUtil.equals(memberName,  "target")) {
			throw RuntimeUtil.syntaxError("Invalid member name for new.: " + memberName);
		}
	}

	@Override
	public STATEMENT_TYPE getStatementType() {
		return STATEMENT_TYPE.EXPRESSION;
	}	

    @Override
	public JSType getReturnedType() {
    	return JSType.UNKNOWN;
	}
	
	
	@Override
	public Signal evaluate(JSInterpretedRuntimeContext context, JSResult result) {
		try {
			JSFunctionContext functionContext = context.getFunctionContext();
			if(functionContext!=null) {
				Constructor ctor = functionContext.getNewTarget();
				result.setValue(ctor!=null ? ctor : RuntimeUtil.UNDEFINED);
			} else {
				result.setValue(RuntimeUtil.UNDEFINED);
			}
			return Signal.NONE;
		} catch(Throwable ex) {
			throw fillInStackTrace(ex);
		}		
	}
	
	@Override
	public void evaluateAssign(JSInterpretedRuntimeContext context, Object rightValue, Function<Object, Object> assigner, JSResult result, Function<Object, Object> returnOriginalValue) {
		throw RuntimeUtil.syntaxError("Cannot assign to new.target");
	}
	
	// No evaluateDelete()/transpileDeleteExpression() override: new.target
	// is a value-producing MetaProperty, never a Reference, so `delete
	// new.target` must trivially succeed (return true) per spec's delete
	// evaluation - the base ASTNode.evaluateDelete()'s default (evaluate
	// for side effects, return true) already implements this correctly.
	// Unlike assignment (new.target genuinely cannot be assigned to, since
	// it has no Reference to assign through), delete has no such
	// restriction - test262 unary-expr.js: `delete (new.target)` must be
	// `true`.

    @Override
    public String transpileJavaExpression(JSTranspilerGeneratorContext jsContext) {
   		return StringFormat.format("newTarget({0})", 
   				JSTranspiler.MAIN_CONTEXT );
    }

    @Override
	public String transpileJavaAssignment(JSTranspilerGeneratorContext jsContext, ASSIGN_TYPE type, String rightValue, boolean sequence, boolean returnOriginalValue) {
		throw RuntimeUtil.syntaxError("Cannot assign to new.target");
    }
    
    @Override
	public String decompileExpression() {
    	return "new.target";
	}
}
