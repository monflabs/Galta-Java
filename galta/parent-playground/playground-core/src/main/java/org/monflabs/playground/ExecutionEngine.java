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
package org.monflabs.playground;

/**
 * Executes a snippet, once: an engine is created for each run by the
 * {@link ExecutionEngineFactory}. {@link #execute()} runs {@link #_execute()}
 * on the calling thread (READY, RUNNING, then COMPLETED).
 * <p>
 * An engine that can stop cooperatively returns true from
 * {@link #isSoftInterruptable()} and implements {@link #softInterrupt()};
 * the others are stopped by interrupting their thread, and abandoned when
 * they do not stop (see {@link ExecutionController}).
 */
public abstract class ExecutionEngine {
	
	public static enum STATE {
		READY,
		RUNNING,
		COMPLETED
	}
	
	// written by the execution thread, read by others (stop, debugger watchers)
	private volatile STATE state;

	private volatile ExecutionContext executionContext;
	private volatile Thread executionThread;
	
	protected ExecutionEngine(ExecutionContext context) {
		this.state = STATE.READY;
		this.executionContext = context;
	}

	public ExecutionContext getExecutionContext() {
		return executionContext;
	}

	public STATE getState() {
		return state;
	}
	
	public Thread getExecutionThread() {
		return executionThread;
	}
	
	public final ExecutionResult execute() throws Exception {
		if(state!=STATE.READY) {
			throw new PlaygroundException(null,"Can't execute script because it is in state {0}",state);
		}
		this.state = STATE.RUNNING;
		executionThread = Thread.currentThread();
		try {
			return _execute();
		} finally {
			// The context is kept: code that outlives the run (asynchronous
			// callbacks, a debugger, the result views) may still use it
			this.state = STATE.COMPLETED;
			executionThread = null;
		}
	}

	public boolean isSoftInterruptable() {
		return false;
	}
	public void softInterrupt() {
	}
	
	protected abstract ExecutionResult _execute() throws Exception;
}