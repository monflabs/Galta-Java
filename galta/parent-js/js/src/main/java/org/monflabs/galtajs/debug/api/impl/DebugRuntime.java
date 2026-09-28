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

import java.util.function.BooleanSupplier;

import org.monflabs.galtajs.rt.JSGlobalContext;
import org.monflabs.galtajs.rt.JSGlobalContext.RunningState;
import org.monflabs.galtajs.rt.JSRuntimeContext;

/**
 * Mode-neutral entry point both interpreted ({@code ASTDebugHook}) and
 * transpiled ({@code JSTranspiledUnit.debugStatement()}) instrumentation
 * call into. Centralizing the pause/resume synchronization here (rather than
 * duplicating it per mode) means the one tricky piece of concurrency in this
 * whole feature is written and tested once.
 */
public final class DebugRuntime {

	/**
	 * JVM-wide fast path: no debugger session anywhere is active. Read on
	 * every instrumented statement, so it stays a plain volatile field;
	 * change it only through {@link #sessionStarted()} and
	 * {@link #sessionEnded()}, which serialize the read-modify-write.
	 */
	public static volatile int ACTIVE_SESSIONS = 0;

	private static final Object SESSIONS_LOCK = new Object();

	/** Counts one more active debugger session. */
	public static void sessionStarted() {
		synchronized (SESSIONS_LOCK) {
			ACTIVE_SESSIONS++;
		}
	}

	/** Counts one debugger session less. */
	public static void sessionEnded() {
		synchronized (SESSIONS_LOCK) {
			if (ACTIVE_SESSIONS > 0) {
				ACTIVE_SESSIONS--;
			}
		}
	}

	// One JS-level throw propagates through MANY enclosing statement-level
	// wraps as it unwinds (the throwing statement's own wrap, then its
	// caller's wrap, then ITS caller's, etc., all the way to the nearest
	// catch) - each one's catch block sees the SAME exception instance.
	// Without this, onExceptionThrown() (and a pause) fires once per wrap
	// it passes through, not once per throw - confirmed the hard way: a
	// single Debugger.resume only released the innermost of several stacked
	// pauses, leaving the script blocked on the next one forever. Identity
	// (not equality) is exactly right here: the propagating exception is
	// the same Java object reference at every enclosing catch.
	private static final ThreadLocal<Throwable> lastReportedException = new ThreadLocal<>();

	private DebugRuntime() {
	}

	/**
	 * Whether this exact exception instance has already been reported to
	 * {@link DebugHook#onExceptionThrown} on this thread - see the
	 * dedup rationale above. Marks it reported as a side effect.
	 */
	public static boolean shouldReportException(Throwable t) {
		if (lastReportedException.get() == t) {
			return false;
		}
		lastReportedException.set(t);
		return true;
	}

	/**
	 * The hook attached to context's global context, or null if none (either
	 * because nothing is active anywhere, or this particular execution isn't
	 * attached to a debugger).
	 */
	public static DebugHook hookFor(JSRuntimeContext context) {
		if (ACTIVE_SESSIONS <= 0) {
			return null;
		}
		JSGlobalContext gctx = context.getGlobalContext();
		if (gctx.getRunningState() == RunningState.STOPPING) {
			return null;
		}
		if (!(gctx instanceof Debuggable debuggable)) {
			return null;
		}
		return debuggable.getDebugHook();
	}

	/**
	 * Runs {@code decide}, and if it returns true, blocks the calling thread
	 * until resumed. The decision and the wait() are atomic with respect to
	 * a resumer's notify() - both happen inside the SAME
	 * {@code synchronized(hook)} block, so a resumer (whose own resume must
	 * also synchronize on {@code hook} to call notify()) can never slip its
	 * notify() into the gap between "yes, pause" and "now waiting", which
	 * would otherwise be a lost wakeup that hangs the paused thread forever.
	 * Consequence: {@link DebugHook} method implementations run while
	 * holding this lock and must not block or do slow work.
	 */
	public static void checkPause(JSGlobalContext gctx, DebugHook hook, BooleanSupplier decide) {
		synchronized (hook) {
			if (decide.getAsBoolean()) {
				gctx.setRunningState(RunningState.SUSPENDED);
				// Loop rather than a single wait(): a notify() may be waking
				// this thread up to run a callable someone else handed to
				// PausedEvent.call() (see DebugHook.onWoken()'s own doc),
				// not necessarily to actually resume.
				boolean keepWaiting = true;
				while (keepWaiting) {
					try {
						hook.wait();
					} catch (InterruptedException ie) {
					}
					keepWaiting = hook.onWoken();
				}
				gctx.checkInterrupted();
				gctx.setRunningState(RunningState.RUNNING);
			}
		}
	}

	/**
	 * Called directly from generated transpiled Java at each instrumented
	 * statement boundary (see {@code JSTranspiledUnit.debugStatement()}).
	 * Transpiled statements have no AST node to delegate to afterward (the
	 * statement's own generated code runs regardless, immediately after this
	 * call returns) - so, unlike {@code ASTDebugHook}, there is no
	 * onExit/onExceptionThrown call paired with this one. See the plan's own
	 * documented v1 scope: transpiled mode gets statement-level breakpoints
	 * and stepping, not pause-on-exception.
	 */
	public static void checkStatement(JSRuntimeContext context, int line, int col, boolean debuggerStatement) {
		DebugHook hook = hookFor(context);
		if (hook == null) {
			return;
		}
		DebugLocation location = new DebugLocation(line, col, true, debuggerStatement);
		checkPause(context.getGlobalContext(), hook, () -> hook.onStatement(context, location));
	}
}
