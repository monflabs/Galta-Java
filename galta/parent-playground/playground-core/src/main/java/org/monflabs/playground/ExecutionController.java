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

import java.io.OutputStream;
import java.io.PrintStream;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

import org.monflabs.util.Console;

/**
 * Runs the executions of a playground, one at a time, without any UI
 * dependency.
 * <ul>
 * <li><b>Debounce</b>: {@link #request(ExecutionContext, Map, long)} schedules a run
 * after a delay; a newer request made before it starts replaces it.</li>
 * <li><b>One current run</b>: starting a run cancels the one in progress (a
 * soft interrupt when the engine supports it, a thread interrupt otherwise)
 * and waits for it up to the {@linkplain #setStopTimeout(Duration) stop
 * timeout}; a run that does not stop in time is abandoned - its thread is a
 * daemon and is left to finish on its own.</li>
 * <li><b>Superseded runs are silent</b>: every run has an id; the listener
 * only hears about the current run, and the console of a superseded run is
 * muted (see {@link Run#gate(PrintStream)}).</li>
 * <li>{@link #cancel()} supersedes everything (e.g. when another snippet is
 * selected), {@link #stop()} stops the current run (the Stop button), and
 * {@link #close()} shuts the controller down.</li>
 * </ul>
 * Every thread it creates is a daemon thread.
 */
public class ExecutionController implements AutoCloseable {

	/**
	 * Notified about the current run only, on the run's own thread (or the
	 * controller's, for an abandoned run). A listener moves to its UI thread
	 * itself, and should check {@link Run#isCurrent()} again there.
	 */
	public interface Listener {
		/**
		 * The run is about to execute.
		 */
		default void executionStarted(Run run) {
		}
		/**
		 * The run is over.
		 *
		 * @param result the engine's result, null when it failed or returned none
		 * @param failure what the execution threw, or null
		 */
		default void executionFinished(Run run, ExecutionResult result, Throwable failure) {
		}
	}

	/**
	 * One execution.
	 */
	public final class Run {
		private final long id;
		private final ExecutionContext context;
		private final Map<String,Object> options;
		private final AtomicBoolean finished = new AtomicBoolean();
		private volatile ExecutionEngine engine;
		private volatile Thread thread;
		private volatile boolean stopped;
		private volatile boolean abandoned;
		private volatile long startTime;

		private Run(long id, ExecutionContext context, Map<String,Object> options) {
			this.id = id;
			this.context = context;
			this.options = options!=null ? Map.copyOf(options) : Map.of();
		}

		public long getId() {
			return id;
		}
		public ExecutionContext getContext() {
			return context;
		}
		/**
		 * The options captured when the run was requested.
		 */
		public Map<String,Object> getOptions() {
			return options;
		}
		public Object getOption(String key, Object defaultValue) {
			return options.getOrDefault(key, defaultValue);
		}
		/**
		 * The engine, once the run started (null before).
		 */
		public ExecutionEngine getEngine() {
			return engine;
		}
		/**
		 * When the run started ({@link System#currentTimeMillis()}), 0 before.
		 */
		public long getStartTime() {
			return startTime;
		}
		/**
		 * Whether this is the controller's current run: neither superseded by a
		 * newer one, nor cancelled, nor abandoned.
		 */
		public boolean isCurrent() {
			return current==this && !abandoned;
		}
		/**
		 * Whether {@link ExecutionController#stop()} was called for this run.
		 */
		public boolean isStopped() {
			return stopped;
		}
		/**
		 * Whether the run did not stop in time and was left behind.
		 */
		public boolean isAbandoned() {
			return abandoned;
		}
		public boolean isFinished() {
			return finished.get();
		}

		/**
		 * A stream forwarding to {@code out} only while this run is the
		 * current one: a superseded run cannot write over the console of the
		 * next.
		 */
		public PrintStream gate(PrintStream out) {
			return new GatedPrintStream(out, this);
		}

		@Override
		public String toString() {
			return "Run#"+id;
		}
	}

	private static final ThreadLocal<Run> CURRENT_THREAD_RUN = new ThreadLocal<>();

	/**
	 * The run executing on the calling thread, or null when the thread is not
	 * an execution thread of a controller.
	 */
	public static Run currentThreadRun() {
		return CURRENT_THREAD_RUN.get();
	}

	private static final AtomicInteger CONTROLLER_COUNT = new AtomicInteger();

	private final Function<ExecutionContext,ExecutionEngine> engineFactory;
	private final Listener listener;
	private final String threadPrefix;
	private final AtomicInteger threadCount = new AtomicInteger();
	private final ScheduledExecutorService scheduler;
	private final AtomicLong lastRequest = new AtomicLong();
	private volatile Run current;
	private volatile Runnable beforeRun;
	private volatile Duration stopTimeout = Duration.ofSeconds(2);
	private volatile boolean closed;

	/**
	 * @param engineFactory creates the engine of each run
	 * @param listener notified about the current run, or null
	 */
	public ExecutionController(Function<ExecutionContext,ExecutionEngine> engineFactory, Listener listener) {
		this.engineFactory = Objects.requireNonNull(engineFactory);
		this.listener = listener!=null ? listener : new Listener() {};
		this.threadPrefix = "playground-execution-"+CONTROLLER_COUNT.incrementAndGet();
		this.scheduler = Executors.newSingleThreadScheduledExecutor(daemonThreadFactory(threadPrefix+"-scheduler"));
	}

	private ThreadFactory daemonThreadFactory(String name) {
		return r -> {
			Thread t = new Thread(r, name);
			t.setDaemon(true);
			return t;
		};
	}

	/**
	 * How long a cancelled run is waited for before it is abandoned (2s by default).
	 */
	public Duration getStopTimeout() {
		return stopTimeout;
	}
	public void setStopTimeout(Duration stopTimeout) {
		this.stopTimeout = Objects.requireNonNull(stopTimeout);
	}

	/**
	 * Called right before each run starts, on the controller's thread - e.g.
	 * to copy the editors' text into the execution context.
	 */
	public void setBeforeRun(Runnable beforeRun) {
		this.beforeRun = beforeRun;
	}

	/**
	 * The current run (requested last, and not cancelled), or null.
	 */
	public Run getCurrentRun() {
		return current;
	}

	/**
	 * Whether the current run is executing.
	 */
	public boolean isRunning() {
		Run r = current;
		return r!=null && r.startTime!=0 && !r.isFinished();
	}

	/**
	 * Requests a run of the context after the delay. Returns right away: a
	 * newer request made before this one starts replaces it.
	 *
	 * @param options the execution options, captured now
	 * @return the request id, the id of the run if it happens
	 */
	public long request(ExecutionContext context, Map<String,Object> options, long delayMillis) {
		Objects.requireNonNull(context);
		long id = lastRequest.incrementAndGet();
		Run run = new Run(id, context, options);
		try {
			scheduler.schedule(() -> start(run), Math.max(0, delayMillis), TimeUnit.MILLISECONDS);
		} catch(RejectedExecutionException e) {
			// closed: nothing runs anymore
		}
		return id;
	}

	/**
	 * Cancels the pending requests and the current run, which becomes
	 * superseded: its results and console output are dropped. Used when the
	 * executed content goes away (another snippet is selected).
	 */
	public void cancel() {
		lastRequest.incrementAndGet();
		Run r = current;
		current = null;
		if(r!=null) {
			interrupt(r);
			watchStop(r);
		}
	}

	/**
	 * Stops the current run, which stays the current one: the listener hears
	 * about its end (typically an interrupt exception). A run that does not
	 * stop within the stop timeout is abandoned, and reported finished with a
	 * {@link PlaygroundException}.
	 */
	public void stop() {
		Run r = current;
		if(r!=null && !r.isFinished()) {
			r.stopped = true;
			interrupt(r);
			watchStop(r);
		}
	}

	/**
	 * Cancels everything and stops the controller's threads. Abandoned runs
	 * still running are daemon threads.
	 */
	@Override
	public void close() {
		closed = true;
		cancel();
		scheduler.shutdownNow();
	}

	public boolean isClosed() {
		return closed;
	}

	private void start(Run run) {
		if(closed || run.id!=lastRequest.get()) {
			return;	// debounced: a newer request replaced it
		}
		Run previous = current;
		current = run;
		if(previous!=null && !previous.isFinished()) {
			interrupt(previous);
			if(!awaitEnd(previous)) {
				abandon(previous);
			}
		}
		if(current!=run) {
			return;	// cancelled while waiting
		}
		Runnable before = beforeRun;
		if(before!=null) {
			try {
				before.run();
			} catch(RuntimeException e) {
				Console.log(e);
			}
		}
		Thread t = new Thread(() -> execute(run), threadPrefix+"-run-"+threadCount.incrementAndGet());
		t.setDaemon(true);
		run.thread = t;
		run.startTime = System.currentTimeMillis();
		t.start();
	}

	private void execute(Run run) {
		CURRENT_THREAD_RUN.set(run);
		ExecutionResult result = null;
		Throwable failure = null;
		try {
			if(run.isCurrent()) {
				notifyStarted(run);
				ExecutionEngine engine = engineFactory.apply(run.context);
				run.engine = engine;
				if(run.stopped || !run.isCurrent()) {
					// stopped before the engine existed
					throw new InterruptedException();
				}
				result = engine.execute();
			}
		} catch(Throwable t) {
			failure = t;
		} finally {
			CURRENT_THREAD_RUN.remove();
			finish(run, result, failure);
		}
	}

	private void finish(Run run, ExecutionResult result, Throwable failure) {
		if(run.finished.compareAndSet(false, true) && run.isCurrent()) {
			try {
				listener.executionFinished(run, result, failure);
			} catch(RuntimeException e) {
				Console.log(e);
			}
		}
	}

	private void notifyStarted(Run run) {
		try {
			listener.executionStarted(run);
		} catch(RuntimeException e) {
			Console.log(e);
		}
	}

	private static void interrupt(Run run) {
		try {
			ExecutionEngine engine = run.engine;
			if(engine!=null && engine.isSoftInterruptable()) {
				engine.softInterrupt();
			} else {
				Thread t = run.thread;
				if(t!=null) {
					t.interrupt();
				}
			}
		} catch(RuntimeException e) {
			Console.log(e);
		}
	}

	private boolean awaitEnd(Run run) {
		Thread t = run.thread;
		if(t==null) {
			return true;
		}
		try {
			t.join(Math.max(1, stopTimeout.toMillis()));
		} catch(InterruptedException e) {
			Thread.currentThread().interrupt();
		}
		return !t.isAlive();
	}

	// Checks, after the stop timeout, that the run is over: abandons it otherwise
	private void watchStop(Run run) {
		if(run.thread==null) {
			return;
		}
		try {
			scheduler.schedule(() -> {
				if(!run.isFinished() && run.thread.isAlive()) {
					abandon(run);
				}
			}, Math.max(1, stopTimeout.toMillis()), TimeUnit.MILLISECONDS);
		} catch(RejectedExecutionException e) {
			// closed
		}
	}

	private void abandon(Run run) {
		boolean wasCurrent = run.isCurrent();
		run.abandoned = true;
		if(run.finished.compareAndSet(false, true)) {
			Console.log("Playground execution {0} did not stop within {1}ms: abandoned", run.id, stopTimeout.toMillis());
			if(wasCurrent) {
				try {
					listener.executionFinished(run, null, new PlaygroundException(null,
							"The execution did not stop within {0}ms: abandoned", stopTimeout.toMillis()));
				} catch(RuntimeException e) {
					Console.log(e);
				}
			}
		}
	}

	/**
	 * Forwards to a stream while its run is the current one.
	 */
	private static final class GatedPrintStream extends PrintStream {
		private final Run run;
		private final PrintStream target;

		GatedPrintStream(PrintStream target, Run run) {
			super(new OutputStream() {
				@Override
				public void write(int b) {
					if(run.isCurrent()) {
						target.write(b);
					}
				}
				@Override
				public void write(byte[] b, int off, int len) {
					if(run.isCurrent()) {
						target.write(b, off, len);
					}
				}
				@Override
				public void flush() {
					if(run.isCurrent()) {
						target.flush();
					}
				}
			}, false, java.nio.charset.StandardCharsets.UTF_8);
			this.run = run;
			this.target = target;
		}

		// Text goes straight to the target (its own encoding), not through
		// this stream's encoder
		@Override
		public void print(String s) {
			if(run.isCurrent()) {
				target.print(s);
			}
		}
		@Override
		public void println(String s) {
			if(run.isCurrent()) {
				target.println(s);
			}
		}
		@Override
		public void println() {
			if(run.isCurrent()) {
				target.println();
			}
		}
		@Override
		public void print(Object o) {
			print(String.valueOf(o));
		}
		@Override
		public void println(Object o) {
			println(String.valueOf(o));
		}
		@Override
		public void flush() {
			if(run.isCurrent()) {
				target.flush();
			}
		}
	}
}
