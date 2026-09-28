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
import org.monflabs.galtajs.node.ASTProgram;
import org.monflabs.galtajs.parser.Token;
import org.monflabs.galtajs.rt.JSResult;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.interpreter.InterpretedUnitRuntimeContext.Signal;
import org.monflabs.galtajs.rt.interpreter.JSInterpretedRuntimeContext;
import org.monflabs.galtajs.transpiler.JSTranspiler;
import org.monflabs.galtajs.transpiler.context.JSTranspilerGeneratorContext;
import org.monflabs.galtajs.util.JavaBuilder;
import org.monflabs.util.StringFormat;


/**
 * Yield* statement node.
 */
public class ASTYieldStar extends ASTNode {

	private ASTNode node;

	// Set statically in init(), same pattern as ASTAwait's own flag: true
	// only when the immediately-enclosing function is an async generator
	// (`async function*`). See RuntimeUtil.yieldStar_()'s own doc comment
	// for what this changes (async-preferring iterator lookup, awaiting
	// each delegate step).
	private boolean insideAsyncGeneratorBody;

	public ASTYieldStar(Token t, ASTNode node) {
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
	protected void init(InitContext initContext) {
		IContextRootContainer n = findParentNodeByClass(IContextRootContainer.class);
		if(n instanceof ASTProgram p) {
			p.setAsyncExecution(true);
		} else if(n instanceof ASTFunctionDecl f) {
			if(!f.isGenerator()) {
				throw RuntimeUtil.syntaxError("yield* must be used in a generator function");
			}
			insideAsyncGeneratorBody = f.isAsync();
		}
		super.init(initContext);
	}

	@Override
	public Signal evaluate(JSInterpretedRuntimeContext context, JSResult result) {
		try {
			Object value = RuntimeUtil.yieldStar_(context, () -> node.evaluateValue(context, result), insideAsyncGeneratorBody);
			// A "yield*" expression evaluates to the delegate iterator's final
			// (done) value.
			result.setValue(value);
			return Signal.NONE;
		} catch(Throwable ex) {
			throw fillInStackTrace(ex);
		}
	}

    @Override
	public String transpileJavaExpression(JSTranspilerGeneratorContext jsContext) {
    	// See ASTYield's transpileJavaExpression - same gap, same fix: only the
    	// bare-statement form existed before, so yield*'s delegate-iterator final
    	// value (already returned by RuntimeUtil.yieldStar_, used in interpreted
    	// mode's evaluate() below) was unreachable as a sub-expression value.
    	return StringFormat.format("yieldStar_({0},() -> {1},{2})", JSTranspiler.MAIN_CONTEXT, JSTranspiler.asValue(jsContext, node), insideAsyncGeneratorBody);
    }

    @Override
	public void decompileStatement(JavaBuilder b) {
		b.append(decompileExpression());
		b.append(';');
	}

    @Override
	public String decompileExpression() {
		return "yield* "+node.decompileExpression();
	}
}