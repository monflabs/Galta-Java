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
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.monflabs.galtajs.debug.api.Breakpoint;
import org.monflabs.galtajs.debug.api.BreakpointRequest;
import org.monflabs.galtajs.debug.api.DebugException;
import org.monflabs.galtajs.debug.api.DebugFrame;
import org.monflabs.galtajs.debug.api.DebugListener;
import org.monflabs.galtajs.debug.api.DebugScript;
import org.monflabs.galtajs.debug.api.DebugValues;
import org.monflabs.galtajs.debug.api.Debugger;
import org.monflabs.galtajs.debug.api.ExecutionContext;
import org.monflabs.galtajs.debug.api.Location;
import org.monflabs.galtajs.debug.api.PauseOnExceptions;
import org.monflabs.galtajs.debug.api.PauseReason;
import org.monflabs.galtajs.debug.api.PausedEvent;
import org.monflabs.galtajs.modules.JSScriptUnit;
import org.monflabs.galtajs.rt.JSBoundaryContext;
import org.monflabs.galtajs.rt.JSGlobalContext;
import org.monflabs.galtajs.rt.JSGlobalContext.RunningState;
import org.monflabs.galtajs.rt.JSRuntimeContext;
import org.monflabs.galtajs.rt.JSRuntimeException;
import org.monflabs.galtajs.rt.JSRuntimeInterruptException;
import org.monflabs.galtajs.rt.JSRuntimeUncatchableException;
import org.monflabs.galtajs.rt.JSUnitContext;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.interpreter.JSInterpretedRuntimeContext;
import org.monflabs.galtajs.rt.util.Callstack;

/**
 * The debugger of one GaltaJS script execution - v1 scope: exactly one root
 * script, one execution context, one attached client at a time (matching
 * this plan's own documented v1 restrictions). Implements both the
 * embedder-facing {@link Debugger} facade and the engine-facing
 * {@link DebugHook} callback - same "one class implements the whole
 * mechanism" shape as the untouched {@code DebugControllerLocal}, but built
 * fresh against the new hook/facade rather than the old one.
 *
 * <p>Mode-neutral: {@code rootUnit}/{@code contextFactory} work equally for
 * an interpreted ({@code JSInterpretedUnit}/{@code InterpretedGlobalRuntimeContext})
 * or transpiled ({@code JSTranspiledUnit}/{@code TranspiledGlobalRuntimeContext})
 * execution - the only requirement is that the produced {@code JSGlobalContext}
 * also implements {@link Debuggable}, which both concrete classes do.
 */
public class DebuggerImpl implements Debugger, DebugHook {

	public enum ResumeMode {
		RESUME, STEP_INTO, STEP_OVER, STEP_OUT
	}

	private final JSScriptUnit rootUnit;
	private final Supplier<? extends JSGlobalContext> contextFactory;
	private final DebugValuesImpl values;

	private final List<DebugListener> listeners = new CopyOnWriteArrayList<>();
	private final List<BreakpointImpl> breakpoints = new CopyOnWriteArrayList<>();
	private final AtomicInteger breakpointIdGen = new AtomicInteger();

	private volatile boolean breakpointsActive = true;
	private volatile boolean skipAllPauses = false;
	// NONE, matching CDP's own convention (a client must explicitly call
	// Debugger.setPauseOnExceptions before ANY exception pauses execution) -
	// see onExceptionThrown()'s own doc for why UNCAUGHT can't safely mean
	// "pause like ALL" as a default: plenty of ordinary, correctly-caught-
	// by-the-script exceptions (and internal engine control-flow use of
	// exceptions) would otherwise pause every run before a client asks for
	// anything, confirmed the hard way - this was UNCAUGHT here originally
	// and it broke breakpoint/stepping tests that never touch this setting
	// at all, by pausing on an unrelated internal exception first.
	private volatile PauseOnExceptions pauseOnExceptions = PauseOnExceptions.NONE;

	private volatile boolean pauseRequested;
	private volatile boolean pauseOnStartRequested;
	private volatile boolean breakOnNextStatement;
	private volatile JSRuntimeContext breakOnStepOverContext;
	private volatile JSBoundaryContext breakOnReturnBoundary;

	// Set when the debugger is closed: no more pauses of any kind
	private volatile boolean closed;
	// Guarded by `this`: true while the paused thread runs an operation for
	// a client (PausedEvent.call()) or evaluates a breakpoint condition -
	// code run on the client's behalf must never pause the thread again
	// (it would wait on itself: the client is blocked waiting for it)
	private boolean suppressPauses;

	private JSGlobalContext globalContext;
	private ExecutionContextImpl executionContext;
	private DebugScriptImpl script;
	private Thread executionThread;
	private volatile PausedEventImpl currentPause;

	// Rendezvous state for onWoken()/runOnPausedThread() - guarded by `this`,
	// the same monitor ASTDebugHook.checkPause() synchronizes on (this
	// DebuggerImpl instance IS the `hook` object it is given).
	private Callable<?> pendingCallable;
	private Object pendingResult;
	private Throwable pendingFailure;
	private boolean pendingDone;
	private ResumeMode resumeModeConsumed;

	public DebuggerImpl(JSScriptUnit rootUnit, Supplier<? extends JSGlobalContext> contextFactory) {
		this.rootUnit = rootUnit;
		this.contextFactory = contextFactory;
		this.values = new DebugValuesImpl(rootUnit.getEnvironment());
	}

	/**
	 * Starts the target script's execution on a dedicated thread, attaching
	 * this debugger before the first statement runs. Not part of the
	 * {@link Debugger} facade itself (GaltaJS scripts, unlike a persistent
	 * engine, have no independent "already running" state to attach to
	 * later) - the natural analogue of the untouched
	 * {@code DebugControllerLocal.start()}.
	 */
	public synchronized void start() {
		if (globalContext != null) {
			throw new IllegalStateException("Debugger already started");
		}
		globalContext = contextFactory.get();
		if (!(globalContext instanceof Debuggable debuggable)) {
			throw new IllegalStateException("This JSGlobalContext does not support debugging: " + globalContext.getClass());
		}
		executionContext = new ExecutionContextImpl(1, rootUnit.getDescriptor().getName(), globalContext);
		script = new DebugScriptImpl("script1", rootUnit, executionContext);
		debuggable.setDebugHook(this);
		fireListeners(l -> l.executionContextCreated(executionContext));
		fireListeners(l -> l.scriptParsed(script));
		// A breakpoint set before start() (the normal CDP flow: the client
		// sets breakpoints, THEN asks the script to run) had no script to
		// resolve against yet - resolve any pending ones against the script
		// now that it exists, so DevTools' "breakpoint took effect" signal
		// (Debugger.breakpointResolved) still fires for them.
		for (BreakpointImpl bp : breakpoints) {
			if (bp.locations().isEmpty() && bp.matches(script, bp.request().line())) {
				int column = bp.request().column() >= 0 ? bp.request().column() : 1;
				Location location = new Location(script, bp.request().line(), column);
				bp.addLocation(location);
				fireListeners(l -> l.breakpointResolved(bp, location));
			}
		}

		DebugRuntime.sessionStarted();
		JSGlobalContext gctxForThread = globalContext;
		executionThread = new Thread(() -> {
			try {
				rootUnit.executeWithContext(gctxForThread);
			} catch (JSRuntimeInterruptException e) {
				// normal stop request
			} finally {
				synchronized (DebuggerImpl.this) {
					debuggable.setDebugHook(null);
				}
				DebugRuntime.sessionEnded();
				fireListeners(DebugListener::executionFinished);
			}
		}, "GaltaJS-Debug-" + rootUnit.getDescriptor().getName());
		executionThread.start();
	}

	public Thread getExecutionThread() {
		return executionThread;
	}

	@Override
	public boolean isRunning() {
		Thread t = executionThread;
		return t != null && t.isAlive() && currentPause == null;
	}


	//
	// Debugger facade
	//

	@Override
	public void addListener(DebugListener listener) {
		listeners.add(listener);
	}

	@Override
	public void removeListener(DebugListener listener) {
		listeners.remove(listener);
		if (listeners.isEmpty()) {
			clientGone();
		}
	}

	// The last client went away: forget its stepping requests and let a
	// paused thread go - nobody is left to resume it.
	private void clientGone() {
		PausedEventImpl pause;
		synchronized (this) {
			clearStepping();
			pause = currentPause;
		}
		if (pause != null && !pause.isResumed()) {
			pause.resume();
		}
	}

	private synchronized void clearStepping() {
		pauseRequested = false;
		breakOnNextStatement = false;
		breakOnStepOverContext = null;
		breakOnReturnBoundary = null;
	}

	// Whether the calling thread may pause at all. Only the script's own
	// execution thread pauses: evaluations a client runs from another thread
	// (Runtime.evaluate while nothing is paused) execute instrumented code
	// too, and pausing that thread would block the client's own connection.
	private boolean mayPause() {
		return !closed && !suppressPauses && !skipAllPauses && Thread.currentThread() == executionThread;
	}

	@Override
	public List<ExecutionContext> executionContexts() {
		return executionContext == null ? List.of() : List.of(executionContext);
	}

	@Override
	public List<DebugScript> scripts() {
		return script == null ? List.of() : List.of(script);
	}

	@Override
	public PausedEvent currentPause() {
		return currentPause;
	}

	@Override
	public synchronized Breakpoint setBreakpoint(BreakpointRequest request) {
		String id = "bp" + breakpointIdGen.incrementAndGet();
		BreakpointImpl bp = new BreakpointImpl(id, request);
		if (script != null && bp.matches(script, request.line())) {
			int column = request.column() >= 0 ? request.column() : 1;
			Location location = new Location(script, request.line(), column);
			bp.addLocation(location);
			fireListeners(l -> l.breakpointResolved(bp, location));
		}
		breakpoints.add(bp);
		return bp;
	}

	@Override
	public void removeBreakpoint(String breakpointId) {
		breakpoints.removeIf(bp -> bp.id().equals(breakpointId));
	}

	@Override
	public void setBreakpointsActive(boolean active) {
		breakpointsActive = active;
	}

	@Override
	public void setSkipAllPauses(boolean skip) {
		skipAllPauses = skip;
	}

	@Override
	public void setPauseOnExceptions(PauseOnExceptions mode) {
		pauseOnExceptions = mode;
	}

	@Override
	public void pause() {
		pauseRequested = true;
	}

	@Override
	public void pauseOnStart() {
		pauseOnStartRequested = true;
	}

	@Override
	public Object evaluate(ExecutionContext context, String expression) throws DebugException {
		return values.evaluateWith(context, expression, null);
	}

	// Bug found via a real CDP session: JSContext.get() (a scoped-value
	// thread-local read from all over the runtime, e.g. resolving
	// getVariableValue()'s own fast path) throws "JSRuntimeContext is not
	// currently available" unless SOME context's own with() has been
	// entered on the calling thread. A WebSocket connection's own thread has
	// never run any GaltaJS code, so simply calling `operation.call()` here
	// - as this originally did - fails immediately for the very first
	// unpaused Runtime.evaluate/callFunctionOn a client sends. Binding the
	// realm via with() is exactly what this method's own contract promises
	// ("with the context's realm bound") - this was a real gap, not merely
	// unused ceremony.
	@Override
	public <T> T call(ExecutionContext context, Callable<T> operation) throws Exception {
		if (!(context instanceof ExecutionContextImpl impl)) {
			return operation.call();
		}
		JSGlobalContext gctx = impl.getGlobalContext();
		try {
			return gctx.with(() -> {
				try {
					return operation.call();
				} catch (Exception e) {
					throw new CallableFailure(e);
				}
			});
		} catch (CallableFailure f) {
			throw (Exception) f.getCause();
		}
	}

	// Smuggles a checked Exception through JSContext.with()'s Supplier<R>
	// (which cannot declare one) back out to this method's own `throws Exception`.
	private static final class CallableFailure extends RuntimeException {
		private static final long serialVersionUID = 1L;
		CallableFailure(Exception cause) {
			super(cause);
		}
	}

	@Override
	public DebugValues values() {
		return values;
	}

	@Override
	public synchronized void close() {
		closed = true;
		clearStepping();
		pauseOnStartRequested = false;
		if (globalContext instanceof Debuggable debuggable) {
			debuggable.setDebugHook(null);
		}
		if (resumeModeConsumed == null) {
			// wake a currently-paused thread, if any, so close() doesn't
			// leave it hanging forever with no listener left to resume it
			resumeModeConsumed = ResumeMode.RESUME;
			notifyAll();
		}
		currentPause = null;
		breakpoints.clear();
		listeners.clear();
	}


	//
	// DebugHook - engine-facing
	//

	@Override
	public synchronized boolean onStatement(JSRuntimeContext context, DebugLocation location) {
		if (!mayPause()) {
			return false;
		}
		if (listeners.isEmpty()) {
			// Nobody could resume a pause: `debugger;`, breakpoints and steps
			// are ignored, as in Node without an inspector attached. Only an
			// explicit pause-on-start waits, for a client to attach.
			if (pauseOnStartRequested && location.statementLevel()) {
				pauseOnStartRequested = false;
				return firePause(context, location, PauseReason.START, null, List.of());
			}
			return false;
		}
		if (location.debuggerStatement()) {
			return firePause(context, location, PauseReason.DEBUGGER_STATEMENT, null, List.of());
		}
		if (breakpointsActive && location.statementLevel()) {
			List<String> hit = matchBreakpoints(context, location);
			if (!hit.isEmpty()) {
				return firePause(context, location, PauseReason.BREAKPOINT, null, hit);
			}
		}
		// Everything below only ever pauses at a genuine statement boundary,
		// matching Node/V8's own stepping granularity - a sub-expression
		// wrap (a call argument, a for-loop test/update clause, a
		// comma-expression element, ...) is never a valid step/pause
		// target. The flags below all stay set on a non-statement-level
		// call and are simply re-checked the next time this fires - which,
		// for a step request raised while paused ON a statement, is that
		// same statement's own sub-expression wraps first, then the next
		// statement's own wrap.
		if (!location.statementLevel()) {
			return false;
		}
		if (pauseOnStartRequested) {
			pauseOnStartRequested = false;
			return firePause(context, location, PauseReason.START, null, List.of());
		}
		if (pauseRequested) {
			pauseRequested = false;
			return firePause(context, location, PauseReason.DEBUG_COMMAND, null, List.of());
		}
		if (breakOnNextStatement) {
			breakOnNextStatement = false;
			return firePause(context, location, PauseReason.STEP, null, List.of());
		}
		if (breakOnStepOverContext != null) {
			boolean shouldBreak = context.getDebugCallBoundaryContext() == breakOnStepOverContext.getDebugCallBoundaryContext();
			if (!shouldBreak) {
				for (JSRuntimeContext c = breakOnStepOverContext; c != null; c = c.getDebugCallParent()) {
					if (c == context) {
						shouldBreak = true;
						break;
					}
				}
			}
			if (shouldBreak) {
				breakOnStepOverContext = null;
				return firePause(context, location, PauseReason.STEP, null, List.of());
			}
		}
		if (breakOnReturnBoundary != null) {
			for (JSRuntimeContext c = context; c != null; c = c.getDebugCallParent()) {
				if (c == breakOnReturnBoundary) {
					return false;
				}
			}
			breakOnReturnBoundary = null;
			return firePause(context, location, PauseReason.STEP, null, List.of());
		}
		return false;
	}

	@Override
	public synchronized boolean onExit(JSRuntimeContext context, DebugLocation location) {
		if (!mayPause() || listeners.isEmpty() || breakOnReturnBoundary == null || !location.statementLevel()) {
			return false;
		}
		for (JSRuntimeContext c = context; c != null; c = c.getDebugCallParent()) {
			if (c == breakOnReturnBoundary) {
				return false;
			}
		}
		breakOnReturnBoundary = null;
		return firePause(context, location, PauseReason.STEP, null, List.of());
	}

	@Override
	public synchronized boolean onExceptionThrown(JSRuntimeContext context, JSRuntimeException t, DebugLocation location) {
		if (!mayPause() || listeners.isEmpty()) {
			return false;
		}
		// v1 scope: only ALL is actually implemented - it means exactly what
		// it says, pause at every throw site regardless of what happens
		// next. UNCAUGHT and CAUGHT would need to know, at the THROW site,
		// whether an enclosing try/catch will handle it - that needs deeper
		// try/catch AST awareness this session doesn't attempt, so both are
		// treated as NONE (never pause) rather than guessed at: confirmed
		// the hard way that approximating UNCAUGHT as "pause like ALL"
		// causes false pauses on ordinary, correctly-caught exceptions (and
		// on exceptions the engine itself uses internally for control flow)
		// in scripts that never even asked for exception pausing.
		if (pauseOnExceptions != PauseOnExceptions.ALL) {
			return false;
		}
		return firePause(context, location, PauseReason.EXCEPTION, t.getJavascriptException(), List.of());
	}

	@Override
	public void onStateChanged(JSGlobalContext context, RunningState state) {
		// v1: no dedicated listener event for this yet - CDP has no direct
		// equivalent (a paused/resumed event already reflects RUNNING vs
		// SUSPENDED transitions); revisit if a future need surfaces.
	}

	@Override
	public synchronized boolean onWoken() {
		if (pendingCallable != null) {
			Callable<?> op = pendingCallable;
			suppressPauses = true;
			try {
				pendingResult = op.call();
			} catch (Throwable t) {
				pendingFailure = t;
			} finally {
				suppressPauses = false;
			}
			pendingDone = true;
			notifyAll();
			return true;
		}
		if (resumeModeConsumed != null) {
			resumeModeConsumed = null;
			return false;
		}
		return true;
	}

	/**
	 * Called by {@link PausedEventImpl} from a thread OTHER than the paused
	 * one, to run an operation on the paused thread itself (see
	 * {@code DebugHook.onWoken()}'s own doc for why touching the live
	 * paused context from a different thread directly would be unsafe).
	 */
	<T> T runOnPausedThread(Callable<T> operation) throws Exception {
		synchronized (this) {
			pendingCallable = operation;
			pendingDone = false;
			notifyAll();
			while (!pendingDone) {
				wait();
			}
			pendingCallable = null;
			Throwable failure = pendingFailure;
			pendingFailure = null;
			if (failure != null) {
				if (failure instanceof Exception e) {
					throw e;
				}
				throw new RuntimeException(failure);
			}
			@SuppressWarnings("unchecked")
			T result = (T) pendingResult;
			pendingResult = null;
			return result;
		}
	}

	void requestResume(ResumeMode mode, PausedEventImpl pause) {
		synchronized (this) {
			JSRuntimeContext pauseContext = pause.frames().isEmpty() ? null
					: ((DebugFrameImpl) pause.frames().get(0)).getContext();
			switch (mode) {
				case RESUME -> {
				}
				case STEP_INTO -> breakOnNextStatement = true;
				case STEP_OVER -> breakOnStepOverContext = pauseContext;
				case STEP_OUT -> {
					if (pauseContext != null) {
						breakOnReturnBoundary = pauseContext.getDebugCallBoundaryContext();
					}
				}
			}
			resumeModeConsumed = mode;
			currentPause = null;
			notifyAll();
		}
		// Fired here, synchronously, as soon as the resume request is
		// accepted - not after the paused thread actually wakes (that's an
		// async detail no CDP client needs to wait on). Bug found via a
		// real CDP session: without this, Debugger.resumed was never sent
		// at all, and a client waiting on it (or simply on the script
		// finishing, which a real client typically also does) hung forever.
		fireListeners(l -> l.resumed(pause));
	}

	private List<String> matchBreakpoints(JSRuntimeContext context, DebugLocation location) {
		// Breakpoints are set on the root script: a statement on the same
		// line of another unit (an imported module, eval code, an expression
		// a client evaluates) is not a hit
		JSUnitContext unit = context.getMainContext();
		if (unit != null && unit.getScriptUnit() != rootUnit) {
			return List.of();
		}
		List<String> hit = new ArrayList<>();
		int line = location.line();
		for (BreakpointImpl bp : breakpoints) {
			if (bp.matches(script, line) && bp.matchesColumn(location.col()) && conditionHolds(bp, context)) {
				hit.add(bp.id());
			}
		}
		return hit;
	}

	// A breakpoint condition is evaluated in the paused frame, on the script's
	// own thread, before deciding to pause. An error counts as false, as in
	// V8 - which is also what makes DevTools logpoints work: they are sent as
	// a condition that logs and returns false.
	private boolean conditionHolds(BreakpointImpl bp, JSRuntimeContext context) {
		String condition = bp.request().condition();
		if (condition == null || condition.isBlank()) {
			return true;
		}
		if (!(context instanceof JSInterpretedRuntimeContext interpreted)) {
			// v1 scope: no expression evaluation in a transpiled frame (see
			// DebugFrameImpl.evaluate()) - the breakpoint stays unconditional
			return true;
		}
		suppressPauses = true;
		try {
			Object result = interpreted.getEnvironment().evaluate(interpreted, condition);
			return RuntimeUtil.toBoolean(result);
		} catch (JSRuntimeUncatchableException e) {
			throw e;
		} catch (RuntimeException e) {
			return false;
		} finally {
			suppressPauses = false;
		}
	}

	// Frame 0 (the innermost, currently-executing frame) is always built
	// directly from `location` - the exact statement that paused, known in
	// BOTH modes (an interpreted ASTDebugHook node's own position, or the
	// literal line/col baked into generated transpiled Java). Any further
	// (caller) frames come from Callstack's existing walk, seeded with a
	// null node (Callstack's own first-entry-from-node behavior is only
	// ever used to special-case reporting the CURRENT frame, which frame 0
	// above already covers) - unchanged from before this refactor for
	// interpreted mode; for transpiled mode this currently contributes no
	// further frames at all (a documented v1 gap - see
	// TranspiledRuntimeContext.getDebugCallParent()'s own doc).
	// Returns whether the thread must now wait: a listener may resume the
	// pause right away, from its paused() callback (a session with no
	// debugger enabled does) - waiting then would never end, the resume's
	// notify having happened before the wait.
	private boolean firePause(JSRuntimeContext context, DebugLocation location, PauseReason reason, Object exception, List<String> hitBreakpoints) {
		List<DebugFrameImpl> impls = new ArrayList<>();
		Location frame0Location = new Location(script, location.line(), location.col());
		impls.add(new DebugFrameImpl(0, context, null, frame0Location, script));

		Callstack stack = new Callstack(context, null);
		for (Callstack.Entry entry : stack.getEntries()) {
			impls.add(new DebugFrameImpl(impls.size(), entry.getContext(), entry.getCallerNode(), null, script));
		}

		List<DebugFrame> frames = new ArrayList<>(impls);
		PausedEventImpl event = new PausedEventImpl(Thread.currentThread(), reason, frames, hitBreakpoints,
				exception, executionContext, this);
		for (DebugFrameImpl frame : impls) {
			frame.setPausedEvent(event);
		}
		currentPause = event;
		fireListeners(l -> l.paused(event));
		if (event.isResumed()) {
			resumeModeConsumed = null;
			return false;
		}
		return true;
	}

	private void fireListeners(java.util.function.Consumer<DebugListener> action) {
		for (DebugListener l : listeners) {
			action.accept(l);
		}
	}
}
