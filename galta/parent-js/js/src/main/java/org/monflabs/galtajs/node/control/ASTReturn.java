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
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.interpreter.InterpretedUnitRuntimeContext.Signal;
import org.monflabs.galtajs.rt.interpreter.JSInterpretedRuntimeContext;
import org.monflabs.galtajs.transpiler.JSTranspiler;
import org.monflabs.galtajs.transpiler.TranspilerJavaBuilder;
import org.monflabs.galtajs.transpiler.context.JSTranspilerGeneratorContext;
import org.monflabs.galtajs.types.JSType;
import org.monflabs.galtajs.util.JavaBuilder;




/**
 * return statement node.
 */
public class ASTReturn extends ASTNode {

	private ASTNode node;

	// Set statically in init(), from the IMMEDIATELY-enclosing
	// ASTFunctionDecl only (mirrors ASTAwait's/ASTYield's own static
	// check) - true only when that function is an async generator (`async
	// function*`). Per spec (ReturnStatement : return Expression ; step
	// "If ! GetGeneratorKind() is async, set exprValue to ? Await(
	// exprValue)"), an EXPLICIT `return expr;` inside an async generator
	// must await its value - even `return undefined;`/`return void 0;` -
	// before completing, while a bare `return;` (no expression at all,
	// step 1 of the OTHER ReturnStatement production) or falling off the
	// function body's end never awaits anything. Confirmed via
	// return-undefined-implicit-and-explicit.js, which distinguishes
	// exactly this: g1 (fall off end)/g2 (bare `return;`) settle one
	// microtask tick sooner than g3 (`return undefined;`)/g4 (`return void
	// 0;`).
	private boolean insideAsyncGeneratorBody;

	public ASTReturn(Token t, ASTNode node) {
		super(t);
		this.node = assignParent(node);
	}

	@Override
	public int getChildCount() {
		return super.getChildCount()+1;
	}
	@Override
	public ASTNode getChild(int index) {
		switch(index) {
			case 0 ->		{ return node; }
			default ->		{ return super.getChild(index-1); }
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
	public Signal evaluate(JSInterpretedRuntimeContext context, JSResult result) {
		try {
			if(node!=null) {
				Object v = node.evaluateValue(context, result);
				result.setValue(insideAsyncGeneratorBody ? RuntimeUtil.awaitInGenerator_(context, v) : v);
			} else {
				result.setUndefined();
			}
			return Signal._RETURN;
		} catch(Throwable ex) {
			throw fillInStackTrace(ex);
		}
	}

	@Override
	protected void init(InitContext initContext) {
		if(!initContext.getEnvironment().supportReturnOutsideFunction()) {
			if(findParentNodeByClass(ASTFunction.class)==null) {
				throw RuntimeUtil.syntaxError("return cannot be used outside of a function");
			}
		}
		IContextRootContainer n = findParentNodeByClass(IContextRootContainer.class);
		if(n instanceof ASTFunctionDecl f) {
			insideAsyncGeneratorBody = f.isAsync() && f.isGenerator();
		}
    	super.init(initContext);
	}


    @Override
	public JSType getReturnedType() {
    	return JSType.UNKNOWN;
	}
	
    @Override
	public void transpileJavaStatement(JSTranspilerGeneratorContext jsContext, TranspilerJavaBuilder b) {
    	// Inside a function body, callVoid returns Object directly - emit a
    	// native `return v;`. At program scope (`supportReturnOutsideFunction`),
    	// `_runValue` is still void and communicates its result via the
    	// context's returnValue latch, so keep the setReturnValue path there.
    	ASTFunction enclosingFunction = findParentNodeByClassUnchecked(ASTFunction.class);
    	if(enclosingFunction!=null) {
    		if(enclosingFunction.isDerivedClassConstructor()) {
    			// A derived constructor's return (with or without an explicit
    			// value - a bare `return;` means undefined) needs its own
    			// spec-mandated coercion (10.2.2 [[Construct]] step 13): an
    			// object passes through as-is (even with `this` still
    			// uninitialized); undefined falls back to GetThisBinding()
    			// (checkThisBinding, may throw ReferenceError - test262
    			// derived-class-return-override-with-empty.js's bare `return;`
    			// after super() must still succeed, while an equivalent bare
    			// return with NO super() call must throw); anything else is
    			// always a TypeError - test262 derived-class-return-override-
    			// with-{boolean,null,number,string,symbol}.js. Per spec this
    			// check (and any resulting error) happens at the [[Construct]]
    			// CALLER level, after the whole constructor body - including
    			// its own try/catch/finally - has finished, so it must NOT be
    			// inline here (it would be wrongly catchable by the
    			// constructor's own catch, or wrongly fire before an enclosing
    			// finally's super() call has run - test262 derived-class-
    			// return-override-{catch,finally-super}*.js). Stage the raw
    			// value (UNDEFINED for a bare return) into ASTFunction's per-
    			// constructor local and break past everything (a labeled
    			// break still runs any enclosing finally, but skips catch,
    			// exactly matching Java's own break/return semantics) to the
    			// point right after the body where ASTFunction emits the
    			// actual, now-correctly-timed checkDerivedConstructorReturn()
    			// call. Every derived-constructor return - bare or valued -
    			// must go through this same path: with even one return left
    			// as a plain Java `return`, javac can prove the labeled block
    			// never completes via break and flags ASTFunction's trailing
    			// statement unreachable.
    			b.println("{0} = {1};", enclosingFunction.getDerivedCtorReturnTarget(jsContext), node!=null ? JSTranspiler.asValue(jsContext, node) : "UNDEFINED");
    			if(org.monflabs.galtajs.transpiler.context.TranspilerGeneratorRegionContext.isInRegion(jsContext)) {
    				// Inside a region (see TranspilerMethodSplitter): the label is in the
    				// method that runs it, which breaks to it on this marker
    				b.println("return org.monflabs.galtajs.rt.transpiler.JSTranspiledRegion.DERIVED_RETURN;");
    			} else {
    				b.println("break {0};", enclosingFunction.getDerivedCtorReturnLabel(jsContext));
    			}
    		} else if(node!=null) {
    			String value = JSTranspiler.asValue(jsContext, node);
    			if(insideAsyncGeneratorBody) {
    				b.println("return RuntimeUtil.awaitInGenerator_({0},{1});", JSTranspiler.MAIN_CONTEXT, value);
    			} else {
    				b.println("return {0};", value);
    			}
    		} else {
    			b.println("return UNDEFINED;");
    		}
    	} else {
    		if(node!=null) {
    			b.println("{0}.setReturnValue({1});", JSTranspiler.MAIN_CONTEXT, JSTranspiler.asValue(jsContext, node));
    			b.println("return;");
    		} else {
    			b.println("return;");
    		}
    	}
    }

    @Override
	public void decompileStatement(JavaBuilder b) {
    	if(node!=null) {
    		b.append("return ");
    		b.append(node.decompileExpression());
    		b.append(';');
    	} else {
    		b.append("return;");
    	}
	}
}