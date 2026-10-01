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
import org.monflabs.galtajs.node.ASTProgram;
import org.monflabs.galtajs.node.control.ASTFunctionDecl;
import org.monflabs.galtajs.node.control.IContextRootContainer;
import org.monflabs.galtajs.optimizer.JSOptimizerContext;
import org.monflabs.galtajs.parser.Token;
import org.monflabs.galtajs.rt.JSResult;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.interpreter.InterpretedUnitRuntimeContext.Signal;
import org.monflabs.galtajs.rt.interpreter.JSInterpretedRuntimeContext;
import org.monflabs.galtajs.transpiler.JSTranspiler;
import org.monflabs.galtajs.transpiler.context.JSTranspilerGeneratorContext;
import org.monflabs.galtajs.types.JSType;
import org.monflabs.util.StringFormat;



/**
 * await unary operation Node.
 */
public class ASTAwait extends ASTUnaryOp {

	// Set statically in init(), from the IMMEDIATELY-enclosing
	// ASTFunctionDecl only (never a runtime "nearest enclosing" search,
	// which would incorrectly find an OUTER generator's Yielder for a
	// plain async function/arrow NESTED inside a generator's own body) -
	// true only when that function is itself an async GENERATOR
	// (`async function*`). See RuntimeUtil.awaitInGenerator_()/
	// AwaitYieldSignal for why this needs a completely different runtime
	// mechanism than a plain async function's await_().
	private boolean insideAsyncGeneratorBody;

	public ASTAwait(Token t, ASTNode node) {
		super(t,node);
	}

	@Override
	public boolean isSequence() {
		return getNode().isSequence();
	}

	// Never foldable, regardless of whether the operand itself is constant
	// (ASTUnaryOp's own default delegates to the operand's isConstant(),
	// which would treat `await <literal>` as constant purely because the
	// LITERAL is - but `await` itself always has an observable side effect
	// independent of the awaited value: spec Await unconditionally yields
	// (at least) one microtask tick before resuming, even for an already-
	// available, non-thenable value. Speculatively evaluating (to try
	// folding) an `await` expression at COMPILE time (ConstantFoldingOptimizer)
	// would invoke the real async/microtask machinery outside of any
	// genuine runtime execution context - nothing would ever be running to
	// drain the microtask queue and resume it, an unconditional deadlock
	// once await() correctly parks instead of returning synchronously.
	@Override
	public boolean isConstant(JSOptimizerContext context) {
		return false;
	}

	@Override
	protected void init(InitContext initContext) {
		IContextRootContainer n = findParentNodeByClass(IContextRootContainer.class);
		if(n instanceof ASTProgram p) {
			p.setAsyncExecution(true);
		} else if(n instanceof ASTFunctionDecl f) {
			if(!f.isAsync()) {
				throw RuntimeUtil.syntaxError("await must be used in an async function");
			}
			insideAsyncGeneratorBody = f.isGenerator();
		}
		super.init(initContext);
	}

	@Override
	public Signal evaluate(JSInterpretedRuntimeContext context, JSResult result) {
		try {
			Object v = getNode().evaluateValue(context,result);
			result.setValue(insideAsyncGeneratorBody ? RuntimeUtil.awaitInGenerator_(context,v) : RuntimeUtil.await_(context,v));
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
    	String fn = insideAsyncGeneratorBody ? "awaitInGenerator_" : "await_";
    	return StringFormat.format("RuntimeUtil.{2}({0},{1})", JSTranspiler.MAIN_CONTEXT,  JSTranspiler.asValue(jsContext,getNode()), fn);
    }

    @Override
	protected String decompileOperator() {
    	return "await ";
    }
}
