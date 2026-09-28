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
 * Yield statement node.
 */
public class ASTYield extends ASTNode {

	private ASTNode node;

	// Set statically in init(), from the IMMEDIATELY-enclosing
	// ASTFunctionDecl only (mirrors ASTAwait's own static check) - true only
	// when that function is an async generator (`async function*`). Per
	// spec (AsyncGeneratorYield step "Set value to ? Await(value)"), a
	// plain `yield expr` inside an async generator must await its own
	// yielded value BEFORE it becomes the IteratorResult's "value" field -
	// confirmed needed via asyncitems-asynciterator-exists.js/
	// mapfn-{a,}sync-iterable-async.js, which yield `Promise.resolve(...)`
	// from an async generator and expect the UNWRAPPED value, not the raw
	// Promise.
	private boolean insideAsyncGeneratorBody;

	public ASTYield(Token t, ASTNode node) {
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
				throw RuntimeUtil.syntaxError("yield must be used in a generator function");
			}
			insideAsyncGeneratorBody = f.isAsync();
		}
		super.init(initContext);
	}

	@Override
	public Signal evaluate(JSInterpretedRuntimeContext context, JSResult result) {
		try {
			Object resumeValue;
			if(node!=null) {
				resumeValue = RuntimeUtil.yield_(context, () -> {
					Object v = node.evaluateValue(context, result);
					return insideAsyncGeneratorBody ? RuntimeUtil.awaitInGenerator_(context, v) : v;
				});
			} else {
				resumeValue = RuntimeUtil.yield_(context);
			}
			// A "yield" expression evaluates to whatever the generator's next(value)
			// call is resumed with.
			result.setValue(resumeValue);
			return Signal.NONE;
		} catch(Throwable ex) {
			throw fillInStackTrace(ex);
		}
	}
	
    @Override
	public String transpileJavaExpression(JSTranspilerGeneratorContext jsContext) {
    	// A yield expression's resume value must be usable like any other value
    	// (assignment, spread, function argument, etc.) - previously only
    	// transpileJavaStatement was implemented, so RuntimeUtil.yield_'s return
    	// value (already used as the resume value in interpreted mode's own
    	// evaluate() below) was reachable only when yield appeared as a bare
    	// statement. ASTNode's default transpileJavaStatement already calls this
    	// method and emits the identical "yield_(...);" for the plain-statement
    	// case, so no separate transpileJavaStatement override is needed anymore.
    	if(node!=null) {
    		String value = JSTranspiler.asValue(jsContext, node);
    		if(insideAsyncGeneratorBody) {
    			return StringFormat.format("yield_({0},() -> RuntimeUtil.awaitInGenerator_({0},{1}))", JSTranspiler.MAIN_CONTEXT, value);
    		}
    		return StringFormat.format("yield_({0},() -> {1})", JSTranspiler.MAIN_CONTEXT, value);
    	}
    	return StringFormat.format("yield_({0})", JSTranspiler.MAIN_CONTEXT);
    }

    @Override
	public void decompileStatement(JavaBuilder b) {
		b.append(decompileExpression());
		b.append(';');
	}

	@Override
	public String decompileExpression() {
		return node!=null ? "yield "+node.decompileExpression() : "yield";
	}
}