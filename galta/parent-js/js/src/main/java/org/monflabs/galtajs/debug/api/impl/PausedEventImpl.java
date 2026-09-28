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

import java.util.List;
import java.util.concurrent.Callable;

import org.monflabs.galtajs.debug.api.DebugFrame;
import org.monflabs.galtajs.debug.api.ExecutionContext;
import org.monflabs.galtajs.debug.api.PauseReason;
import org.monflabs.galtajs.debug.api.PausedEvent;

/**
 * One pause. Immutable snapshot of what paused and why; resume/step/call are
 * delegated back to the owning {@link DebuggerImpl}, which holds the actual
 * wait/notify state (see {@code DebugHook.onWoken()}'s own doc for why the
 * rendezvous lives there and not here).
 */
public class PausedEventImpl implements PausedEvent {

	private final Thread thread;
	private final PauseReason reason;
	private final List<DebugFrame> frames;
	private final List<String> hitBreakpoints;
	private final Object exception;
	private final ExecutionContext context;
	private final DebuggerImpl debugger;
	private volatile boolean resumed;

	public PausedEventImpl(Thread thread, PauseReason reason, List<DebugFrame> frames, List<String> hitBreakpoints,
			Object exception, ExecutionContext context, DebuggerImpl debugger) {
		this.thread = thread;
		this.reason = reason;
		this.frames = frames;
		this.hitBreakpoints = hitBreakpoints;
		this.exception = exception;
		this.context = context;
		this.debugger = debugger;
	}

	@Override
	public Thread thread() {
		return thread;
	}

	@Override
	public PauseReason reason() {
		return reason;
	}

	@Override
	public List<DebugFrame> frames() {
		return frames;
	}

	@Override
	public List<String> hitBreakpoints() {
		return hitBreakpoints;
	}

	@Override
	public Object exception() {
		return exception;
	}

	@Override
	public ExecutionContext context() {
		return context;
	}

	@Override
	public boolean isResumed() {
		return resumed;
	}

	@Override
	public void resume() {
		resumed = true;
		debugger.requestResume(DebuggerImpl.ResumeMode.RESUME, this);
	}

	@Override
	public void stepInto() {
		resumed = true;
		debugger.requestResume(DebuggerImpl.ResumeMode.STEP_INTO, this);
	}

	@Override
	public void stepOver() {
		resumed = true;
		debugger.requestResume(DebuggerImpl.ResumeMode.STEP_OVER, this);
	}

	@Override
	public void stepOut() {
		resumed = true;
		debugger.requestResume(DebuggerImpl.ResumeMode.STEP_OUT, this);
	}

	@Override
	public <T> T call(Callable<T> operation) throws Exception {
		if (Thread.currentThread() == thread) {
			return operation.call();
		}
		return debugger.runOnPausedThread(operation);
	}
}
