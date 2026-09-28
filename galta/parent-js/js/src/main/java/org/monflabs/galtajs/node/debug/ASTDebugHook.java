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
package org.monflabs.galtajs.node.debug;

import org.monflabs.galtajs.debug.api.impl.DebugHook;
import org.monflabs.galtajs.debug.api.impl.DebugLocation;
import org.monflabs.galtajs.debug.api.impl.DebugRuntime;
import org.monflabs.galtajs.node.ASTNode;
import org.monflabs.galtajs.node.NoopNode;
import org.monflabs.galtajs.node.debug.ASTDebugger;
import org.monflabs.galtajs.optimizer.JSOptimizerContext;
import org.monflabs.galtajs.rt.JSGlobalContext;
import org.monflabs.galtajs.rt.JSResult;
import org.monflabs.galtajs.rt.JSRuntimeException;
import org.monflabs.galtajs.rt.JSRuntimeInterruptException;
import org.monflabs.galtajs.rt.JSRuntimeUncatchableException;
import org.monflabs.galtajs.rt.interpreter.InterpretedUnitRuntimeContext.Signal;
import org.monflabs.galtajs.rt.interpreter.JSInterpretedRuntimeContext;
import org.monflabs.galtajs.transpiler.TranspilerJavaBuilder;
import org.monflabs.galtajs.transpiler.context.JSTranspilerGeneratorContext;
import org.monflabs.galtajs.util.JavaBuilder;

/**
 * CDP-oriented debugging instrumentation. This node only appears when a
 * script is compiled with {@code JSEnvironment.Builder.debug(true)}.
 *
 * <p>Deliberately NOT added to the AST/script cache exclusion list: no state
 * is ever stored on this node itself (a {@code debugger;} statement is
 * recognized structurally, and breakpoints live in a session-scoped table -
 * see {@code debug.api.impl}), so a compiled, debug-wrapped AST is just as
 * safely cacheable/reusable across executions and sessions as an
 * uninstrumented one.
 */
public class ASTDebugHook extends ASTNode implements NoopNode {

	private ASTNode node;

	public ASTDebugHook(ASTNode node) {
		super(null);
		this.node = assignParent(node);
	}

	@Override
	public String toString() {
		return "ASTDebugHook: " + getNode().toString();
	}

	@Override
	public int getChildCount() {
		return super.getChildCount() + 1;
	}
	@Override
	public ASTNode getChild(int index) {
		switch (index) {
			case 0 -> { return node; }
			default -> { return super.getChild(index - 1); }
		}
	}
	@Override
	protected void _setChild(int index, ASTNode node) {
		switch (index) {
			case 0 -> { this.node = node; }
			default -> { super._setChild(index - 1, node); }
		}
	}

	@Override
	public ASTNode getNode() {
		return node;
	}

	@Override
	public boolean isConstant(JSOptimizerContext context) {
		return node.isConstant(context);
	}

	@Override
	public boolean isSequence() {
		return node.isSequence();
	}

	/**
	 * True when the wrapped node is the literal {@code debugger;} statement -
	 * structural, not stateful, so this stays true no matter how many
	 * sessions/breakpoint-sets have used this cached AST.
	 */
	public boolean isDebuggerStatement() {
		return node instanceof ASTDebugger;
	}

	// True only for the wraps inserted at a genuine statement boundary
	// (Statement()'s own trailing wrap, and the MainSourceElements()/
	// SourceElements() alternatives that bypass it) - false for the finer-
	// grained wraps at sub-expression sites (a call argument, an if/while/
	// for test or update clause, a comma-expression element). CDP-style
	// line breakpoints and step semantics must only ever consider
	// statement-level hits: a sub-expression on the SAME source line as its
	// enclosing statement (e.g. `out.push(i)`'s argument `i`) gets its own
	// wrap too, and counting both would fire a single logical breakpoint
	// twice per hit - not what any CDP client expects. Structural (set once
	// at construction via NodeFactory.createDebugHookStatement()), not
	// stateful, so cacheability is unaffected.
	private boolean statementLevel;

	public boolean isStatementLevel() {
		return statementLevel;
	}
	public void markStatementLevel() {
		this.statementLevel = true;
	}

	@Override
	public Signal evaluate(JSInterpretedRuntimeContext context, JSResult result) {
		DebugHook hook = DebugRuntime.hookFor(context);
		if (hook != null) {
			JSGlobalContext gctx = context.getGlobalContext();
			DebugLocation location = new DebugLocation(getBeginLine(), getBeginCol(), statementLevel, isDebuggerStatement());
			DebugRuntime.checkPause(gctx, hook, () -> hook.onStatement(context, location));
			try {
				return node.evaluate(context, result);
			} catch (JSRuntimeException rtex) {
				if (shouldPauseOnException(rtex) && DebugRuntime.shouldReportException(rtex)) {
					ASTNode sourceNode = rtex.getSourceNode();
					DebugLocation exceptionLocation = sourceNode != null
							? new DebugLocation(sourceNode.getBeginLine(), sourceNode.getBeginCol(), false, false)
							: location;
					DebugRuntime.checkPause(gctx, hook, () -> hook.onExceptionThrown(context, rtex, exceptionLocation));
				}
				throw rtex;
			} finally {
				DebugRuntime.checkPause(gctx, hook, () -> hook.onExit(context, location));
			}
		}
		return node.evaluate(context, result);
	}

	// Uncatchable/interrupt exceptions must terminate the script - not offer
	// the debugger a chance to pause execution mid-teardown.
	private boolean shouldPauseOnException(Throwable t) {
		return !(t instanceof JSRuntimeUncatchableException) && !(t instanceof JSRuntimeInterruptException);
	}


	//
	// Decompiler
	//

	@Override
	public void decompileStatement(JavaBuilder b) {
		getNode().decompileStatement(b);
	}
	@Override
	public String decompileExpression() {
		return getNode().decompileExpression();
	}


	//
	// Transpiler - Phase 2 will give this real codegen; for now (interpreted-
	// mode-only Phase 1) it is a pass-through.
	//

	@Override
	public void transpileJavaStatement(JSTranspilerGeneratorContext jsContext, TranspilerJavaBuilder b) {
		getNode().transpileJavaStatement(jsContext, b);
	}

	@Override
	public String transpileJavaExpression(JSTranspilerGeneratorContext jsContext) {
		return getNode().transpileJavaExpression(jsContext);
	}

	@Override
	public String transpileTypeofExpression(JSTranspilerGeneratorContext jsContext) {
		return getNode().transpileTypeofExpression(jsContext);
	}

	@Override
	public String transpileDeleteExpression(JSTranspilerGeneratorContext jsContext) {
		return getNode().transpileDeleteExpression(jsContext);
	}

	@Override
	public String transpileJavaAssignment(JSTranspilerGeneratorContext jsContext, ASSIGN_TYPE type, String rightValue, boolean sequence, boolean returnOriginalValue) {
		return getNode().transpileJavaAssignment(jsContext, type, rightValue, sequence, returnOriginalValue);
	}
}
