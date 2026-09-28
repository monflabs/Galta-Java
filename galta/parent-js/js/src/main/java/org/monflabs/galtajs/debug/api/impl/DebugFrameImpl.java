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
package org.monflabs.galtajs.debug.api.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import org.monflabs.galtajs.debug.api.DebugException;
import org.monflabs.galtajs.debug.api.DebugFrame;
import org.monflabs.galtajs.debug.api.DebugScope;
import org.monflabs.galtajs.debug.api.DebugScope.ScopeType;
import org.monflabs.galtajs.debug.api.Location;
import org.monflabs.galtajs.node.ASTNode;
import org.monflabs.galtajs.rt.JSFunctionContext;
import org.monflabs.galtajs.rt.JSRuntimeContext;
import org.monflabs.galtajs.rt.interpreter.InterpretedFunctionRuntimeContext;
import org.monflabs.galtajs.rt.interpreter.InterpretedGlobalRuntimeContext;
import org.monflabs.galtajs.rt.interpreter.InterpretedWithRuntimeContext;
import org.monflabs.galtajs.rt.interpreter.JSInterpretedRuntimeContext;
import org.monflabs.galtajs.rt.transpiler.TranspiledFunctionRuntimeContext;
import org.monflabs.util.StringUtil;

/**
 * One call frame of a pause. Mode-neutral: {@code context} is whatever the
 * paused thread was actually executing in - an interpreted context (backed
 * by a real {@code ASTNode}-derived location/scope chain) or a transpiled
 * one (backed only by the literal line/col baked into generated Java, plus
 * whatever the transpiled runtime context classes expose).
 *
 * <p>The innermost (index 0) frame's own {@code location} always comes
 * explicitly from the {@link DebugLocation} that triggered the pause -
 * NOT from {@code Callstack}, which has no notion of "the exact statement
 * currently executing" independent of an AST node (transpiled code has
 * none). Any further (caller) frames come from {@code Callstack}'s existing
 * walk, unchanged - see {@code DebuggerImpl.firePause()}'s own comment.
 */
public class DebugFrameImpl implements DebugFrame {

	private final int index;
	private final JSRuntimeContext context;
	private final ASTNode callerNode;
	private final Location explicitLocation;
	private final DebugScriptImpl script;
	// Set after construction (see DebuggerImpl.firePause()'s own comment) -
	// a frame is built before the PausedEvent wrapping it can exist, since
	// the event's own frame list needs the frames first.
	private PausedEventImpl pausedEvent;
	private List<DebugScope> scopes;

	public DebugFrameImpl(int index, JSRuntimeContext context, ASTNode callerNode, Location explicitLocation, DebugScriptImpl script) {
		this.index = index;
		this.context = context;
		this.callerNode = callerNode;
		this.explicitLocation = explicitLocation;
		this.script = script;
	}

	void setPausedEvent(PausedEventImpl pausedEvent) {
		this.pausedEvent = pausedEvent;
	}

	public JSRuntimeContext getContext() {
		return context;
	}

	@Override
	public String id() {
		return Integer.toString(index);
	}

	@Override
	public String functionName() {
		JSFunctionContext funcCtx = context.getFunctionContext();
		if (funcCtx instanceof InterpretedFunctionRuntimeContext fc) {
			String name = fc.getFunctionNode().getFunctionName();
			return StringUtil.isNotEmpty(name) ? name : "";
		}
		if (funcCtx instanceof TranspiledFunctionRuntimeContext tfc) {
			String name = tfc.getFunction().getFunctionName();
			return StringUtil.isNotEmpty(name) ? name : "";
		}
		return "<program>";
	}

	@Override
	public Location location() {
		if (explicitLocation != null) {
			return explicitLocation;
		}
		if (callerNode == null) {
			return null;
		}
		return new Location(script, callerNode.getBeginLine(), callerNode.getBeginCol());
	}

	@Override
	public Location functionLocation() {
		JSFunctionContext funcCtx = context.getFunctionContext();
		if (funcCtx instanceof InterpretedFunctionRuntimeContext fc) {
			ASTNode fn = fc.getFunctionNode();
			return new Location(script, fn.getBeginLine(), fn.getBeginCol());
		}
		// v1: no AST node available at runtime for a transpiled function -
		// a documented gap, same family as the call-stack approximation in
		// TranspiledRuntimeContext.getDebugCallParent()'s own doc.
		return null;
	}

	@Override
	public List<DebugScope> scopes() {
		if (scopes == null) {
			scopes = buildScopes();
		}
		return scopes;
	}

	private List<DebugScope> buildScopes() {
		List<DebugScope> result = new ArrayList<>();
		boolean sawFunction = false;
		for (JSRuntimeContext c = context; c != null; c = c.getParent()) {
			ScopeType type;
			if (c instanceof InterpretedGlobalRuntimeContext || c instanceof org.monflabs.galtajs.rt.transpiler.TranspiledGlobalRuntimeContext) {
				type = ScopeType.GLOBAL;
			} else if (c instanceof InterpretedWithRuntimeContext) {
				type = ScopeType.WITH;
			} else if (c instanceof InterpretedFunctionRuntimeContext || c instanceof TranspiledFunctionRuntimeContext) {
				type = sawFunction ? ScopeType.CLOSURE : ScopeType.LOCAL;
				sawFunction = true;
			} else {
				type = ScopeType.BLOCK;
			}
			String name = null;
			if (type == ScopeType.LOCAL || type == ScopeType.CLOSURE) {
				JSFunctionContext funcCtx = c.getFunctionContext();
				if (funcCtx instanceof InterpretedFunctionRuntimeContext fc) {
					name = fc.getFunctionNode().getFunctionName();
				} else if (funcCtx instanceof TranspiledFunctionRuntimeContext tfc) {
					name = tfc.getFunction().getFunctionName();
				}
			}
			// Interpreted mode's global scope bindings (including hoisted
			// top-level var/function declarations) live on globalThis
			// itself, a real JSObject - NOT in InterpretedGlobalRuntimeContext's
			// own VariableMap (which only ever holds its lexical let/const/
			// class bindings). Transpiled mode's globals, by contrast,
			// already live directly in the context's own VariableMap (see
			// TranspiledRuntimeContext.initGlobalVariables()) - no
			// globalThis routing needed there.
			Object scopeObject = (type == ScopeType.GLOBAL && c instanceof InterpretedGlobalRuntimeContext gc)
					? gc.getGlobalThis()
					: c;
			result.add(new DebugScope(type, scopeObject, name));
		}
		return result;
	}

	@Override
	public Object thisValue() {
		return context.getThis();
	}

	@Override
	public Object evaluate(String expression) throws DebugException {
		if (!(context instanceof JSInterpretedRuntimeContext interpretedContext)) {
			// v1 scope: expression evaluation on a paused frame always runs
			// through the interpreter (there is no separate "transpiled
			// expression evaluator") - a transpiled frame's own context
			// isn't interpreter-shaped, so this isn't supported yet. See
			// the plan's own documented v1 restriction.
			throw new DebugException("Expression evaluation is not yet supported for a paused transpiled frame", null, null);
		}
		Callable<Object> operation = () -> context.getEnvironment().evaluate(interpretedContext, expression);
		try {
			return pausedEvent.call(operation);
		} catch (DebugException de) {
			throw de;
		} catch (Exception e) {
			throw new DebugException(e.getMessage(), null, e);
		}
	}
}
