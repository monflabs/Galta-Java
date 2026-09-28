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

import org.monflabs.galtajs.node.clazz.ASTBaseClass;
import org.monflabs.galtajs.node.control.ASTFunction;
import org.monflabs.galtajs.parser.Token;
import org.monflabs.galtajs.rt.JSResult;
import org.monflabs.galtajs.rt.builtins.standard.StandardLibrary;
import org.monflabs.galtajs.rt.interpreter.InterpretedUnitRuntimeContext.Signal;
import org.monflabs.galtajs.rt.interpreter.JSInterpretedRuntimeContext;
import org.monflabs.galtajs.transpiler.JSTranspiler;
import org.monflabs.galtajs.transpiler.context.JSTranspilerGeneratorContext;
import org.monflabs.galtajs.types.JSType;
import org.monflabs.util.StringFormat;


/**
 * This Node.
 */
public class ASTThis extends ASTNode {

	public ASTThis(Token t) {
		super(t);
	}

	@Override
	public STATEMENT_TYPE getStatementType() {
		return STATEMENT_TYPE.EXPRESSION;
	}	

	@Override
	public Signal evaluate(JSInterpretedRuntimeContext context, JSResult result) {
		try {
			result.setValue(context.getThis());
			return Signal.NONE;
		} catch(Throwable ex) {
			throw fillInStackTrace(ex);
		}		
	}

    @Override
	public JSType getReturnedType() {
		// Advisory, not a soundness guarantee: JS allows detaching a method
		// (`const f = obj.method; f()`) or rebinding it (`.call(other)`), so
		// the actual runtime `this` can differ from the class suggested here.
		// Any future consumer must still guard with a runtime receiver check
		// before relying on this for anything observable - same requirement
		// a real inline cache has.
		ASTFunction fn = findEnclosingNonArrowFunction(this);
		if(fn==null) {
			return JSType.UNKNOWN;
		}
		if(fn.isDerivedClassConstructor()) {
			// Real TDZ hazard, not just imprecision - see
			// transpileJavaExpression()'s own comment below: `this` is the
			// UNDEFINED sentinel until this constructor's own super() call
			// runs. Not attempting after-super() ordering analysis here.
			return JSType.UNKNOWN;
		}
		ASTBaseClass cls = fn.getEnclosingInstanceMethodClass();
		return cls!=null ? cls.getObjectType() : JSType.UNKNOWN;
	}

	// Mirrors StandardLibrary's own private findEnclosingNonArrowFunction()
	// (arrow functions are lexically transparent to `this`, per spec).
	private static ASTFunction findEnclosingNonArrowFunction(ASTNode node) {
		for(ASTNode n=node.getParent(); n!=null; n=n.getParent()) {
			if(n instanceof ASTFunction f && !f.isArrow()) {
				return f;
			}
		}
		return null;
	}

    @Override
	public String transpileJavaExpression(JSTranspilerGeneratorContext jsContext) {
    	// A derived constructor's own `this` starts as the UNDEFINED TDZ
    	// sentinel until its own super() call replaces it - a plain
    	// expression-position read (`this.x`, an arrow closing over `this`,
    	// etc, as opposed to an implicit/explicit RETURN, which already goes
    	// through checkThisBinding/checkDerivedConstructorReturn elsewhere)
    	// must throw ReferenceError if read before that. Only gated on
    	// derived-constructor context (walking up through any enclosing
    	// ARROW functions, lexically transparent to `this`) because the very
    	// same UNDEFINED sentinel is also the legitimate value of a plain
    	// sloppy/strict function's own unbound `this` - unconditionally
    	// checking here would wrongly throw for that ordinary case.
    	if(StandardLibrary.isCallerInDerivedClassConstructor(this)) {
    		// Source from _ctx.getThis() (via thisRef()), not the local `_this`
    		// parameter - which super() reassigns, so it's neither
    		// effectively-final nor updated for an arrow-reached super().
    		return StringFormat.format("checkThisBinding({0})", JSTranspiler.thisRef(this));
    	}
    	return JSTranspiler.THIS_VAR;
    }
    
    @Override
	public String decompileExpression() {
    	return "this";
    }
}