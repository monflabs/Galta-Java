package org.monflabs.galtajs.test.test262;

import java.lang.reflect.Constructor;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.monflabs.filesystem.memory.MemoryFileSystem;
import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.jsonfactory.JSObject;
import org.monflabs.galtajs.library.GlobalLibrary;
import org.monflabs.galtajs.modules.JSInterpretedUnit;
import org.monflabs.galtajs.rt.JSRuntimeContext;
import org.monflabs.galtajs.rt.JSScriptExecutor;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.builtins.BaseMethod;
import org.monflabs.galtajs.rt.builtins.Callable;
import org.monflabs.galtajs.rt.builtins.GlobalThis;
import org.monflabs.galtajs.rt.builtins.standard.atomics.Atomics;
import org.monflabs.galtajs.rt.builtins.standard.global.StandardObjects;
import org.monflabs.galtajs.rt.builtins.standard.typedarrays.arraybuffer.ArrayBuffer;
import org.monflabs.galtajs.rt.transpiler.JSTranspiledUnit;
import org.monflabs.galtajs.transpiler.JSTranspiler;
import org.monflabs.galtajs.transpiler.JSTranspilerOptions;
import org.monflabs.javacompiler.JavaCompiler;
import org.monflabs.javacompiler.JavaCompilerFactory;
import org.monflabs.util.path.PathClassLoader;

/**
 * Host-defined bindings required by the TC39 test262 harness.
 *
 * <p>Per <em>INTERPRETING.md</em> the following globals must be present before
 * any test file is executed:
 * <ul>
 *   <li><b>{@code print(value)}</b> – writes its first argument to the test
 *       runner's output stream (used for async test signalling).</li>
 *   <li><b>{@code $262}</b> – an ordinary object exposing:
 *     <ul>
 *       <li>{@code createRealm()} – returns an object exposing a real
 *           {@code .global} (a second, independent {@link JSEnvironment}'s
 *           {@link org.monflabs.galtajs.rt.builtins.GlobalThis}) - NOT a
 *           spec-complete realm (no {@code .evalScript}, no isolated error-
 *           constructor identity); see the {@code createRealm} case in
 *           {@code build262Object()} for exactly what is/isn't covered.</li>
 *       <li>{@code detachArrayBuffer(buffer)} – no-op stub.</li>
 *       <li>{@code evalScript(source)} – evaluates source text in the current
 *           env and returns the completion value.</li>
 *       <li>{@code gc()} – hints the JVM GC.</li>
 *       <li>{@code global} – reference to the global object.</li>
 *     </ul>
 *   </li>
 * </ul>
 */
public class Test262TestLibrary extends GlobalLibrary {

	// Thread-number suffix for agent thread names (diagnostics only) -
	// static/shared is fine, it's just for readable thread dumps, not
	// correctness.
	private static final AtomicInteger AGENT_THREAD_COUNTER = new AtomicInteger();

	private final Test262AgentManager agentManager;

	public Test262TestLibrary() {
		this(new Test262AgentManager());
	}

	// Used when building an AGENT's own environment (see
	// GlobalTest262Environment.newBuilder(Test262AgentManager)) - must share
	// the SAME manager as whatever environment spawned this agent, so
	// broadcast/report actually cross the real Java thread boundary.
	public Test262TestLibrary(Test262AgentManager agentManager) {
		this.agentManager = agentManager;
	}

	@Override
	public void configureStandardObjects(JSEnvironment env, StandardObjects standardObjects) {
		standardObjects.setOwnMethod(new GlobalFn(env,GlobalFnId.print, 1));
		standardObjects.setOwnMethod(new GlobalFn(env,GlobalFnId.$DONE, 1));
		standardObjects.setOwnProperty("$262", build262Object(env));
		// Unlike a real BROWSER main thread (which genuinely can't suspend),
		// test262's own reference test runners are shell/CLI-based (d8,
		// jsshell, etc.), where the INITIAL/main agent CAN suspend too -
		// confirmed via atomicsHelper.js's own safeBroadcast(), which calls
		// `Atomics.wait(temp, 0, ...)` directly from the main thread and
		// expects a "not-equal" result, not a "cannot suspend" TypeError.
		// This marks the CURRENT (main test-execution) thread as
		// agent-capable for Atomics.wait() - same flag
		// $262.agent.start()-spawned agent threads also set for themselves.
		// This is test262-ONLY (Test262TestLibrary is confined to the
		// js-test-test262 module) - it does NOT change ordinary GaltaJS
		// embedding, where Atomics.wait() still honestly refuses to
		// suspend by default.
		Atomics.setCurrentThreadCanSuspend(true);
	}

	// -----------------------------------------------------------------------
	// print() / $DONE()
	// -----------------------------------------------------------------------

	// $DONE(error) tracking for flags:[async] test262 files - reimplements
	// doneprintHandle.js's own convention NATIVELY (rather than sourcing its
	// JS text) so a truthy `error` can THROW a real Java exception right
	// here, reusing the test runner's EXISTING, already-correct
	// execFile()/handleException() error-counting path with no changes
	// needed there. `wasCalled` is separately checked by
	// Test262BaseTest AFTER a file's execution completes normally (no
	// exception) - an async test that never calls $DONE at all must still
	// be treated as a FAILURE, not silently ignored, and only the caller
	// (which knows when one file's execution starts/ends) can tell "never
	// called" apart from "not called YET".
	//
	// PLAIN volatile field, NOT a ThreadLocal - a ThreadLocal here was a
	// real bug (found via module top-level-await's own "$DONE never called"
	// mystery failures): $DONE() can genuinely be invoked from a DIFFERENT
	// Java thread than the one that ran execFile()/checks afterExecute()
	// whenever the engine dispatches an await continuation to its own
	// worker/executor thread (confirmed via a direct repro: `await void 1`
	// at module top level evaluates correctly and $DONE() DOES run - its
	// own side effects are observed - yet wasAsyncDoneCalled() still
	// reported false, because ASYNC_DONE_CALLED.set(true) landed in the
	// WORKER thread's ThreadLocal slot while afterExecute() reads the MAIN
	// thread's slot). $262.agent-spawned agent threads (the original
	// justification for ThreadLocal) never call $DONE() themselves - they
	// use a completely separate report()/getReport() mechanism (see
	// Test262AgentManager) - so this field never actually needed per-thread
	// isolation from them. The test262 sweep itself runs one file at a time
	// in a single JVM (confirmed by the very code this comment replaces),
	// so a plain field is correct and sufficient.
	private static volatile boolean ASYNC_DONE_CALLED = false;
	public static void resetAsyncDoneCalled() {
		ASYNC_DONE_CALLED = false;
	}
	public static boolean wasAsyncDoneCalled() {
		return ASYNC_DONE_CALLED;
	}
	// Exact strings doneprintHandle.js's own $DONE(error) would have
	// printed - Test262BaseTest's getPASSED()/getFAILED() are overridden to
	// match these, reusing BaseTestSuiteTest's existing per-file output-
	// stream-driven pass/fail detection instead of adding a new one.
	public static final String ASYNC_TEST_COMPLETE = "Test262:AsyncTestComplete";
	public static final String ASYNC_TEST_FAILURE_PREFIX = "Test262:AsyncTestFailure:";

	private enum GlobalFnId { print, $DONE }

	private static final class GlobalFn extends BaseMethod {
		private final GlobalFnId id;

		GlobalFn(JSEnvironment env, GlobalFnId id, int length) {
			super(env, id.name(), length);
			this.id = id;
		}

		@Override
		public Object call(Object _this, Object[] args) {
			switch (id) {
				case print: {
					if (args.length > 0) {
						String s = RuntimeUtil.toString(getEnvironment(), args[0]);
						JSRuntimeContext.get().getGlobalContext().getOutStream().println(s);
					}
					return RuntimeUtil.UNDEFINED;
				}
				case $DONE: {
					ASYNC_DONE_CALLED = true;
					Object error = args.length > 0 ? args[0] : RuntimeUtil.UNDEFINED;
					if (error == RuntimeUtil.UNDEFINED || !RuntimeUtil.toBoolean(error)) {
						JSRuntimeContext.get().getGlobalContext().getOutStream().println(ASYNC_TEST_COMPLETE);
						return RuntimeUtil.UNDEFINED;
					}
					String msg;
					if (error instanceof JSObject eo && RuntimeUtil.hasProperty(getEnvironment(), eo, "name")) {
						msg = RuntimeUtil.toString(getEnvironment(), RuntimeUtil.getProperty(getEnvironment(), eo, "name"))
								+ ": " + RuntimeUtil.toString(getEnvironment(), RuntimeUtil.getProperty(getEnvironment(), eo, "message"));
					} else {
						msg = RuntimeUtil.toString(getEnvironment(), error);
					}
					JSRuntimeContext.get().getGlobalContext().getOutStream().println(ASYNC_TEST_FAILURE_PREFIX + msg);
					throw RuntimeUtil.error("{0}{1}", ASYNC_TEST_FAILURE_PREFIX, msg);
				}
				default:
					throw new IllegalStateException();
			}
		}
	}

	// -----------------------------------------------------------------------
	// $262 object
	// -----------------------------------------------------------------------

	private JSObject build262Object(JSEnvironment env) {
		JSObject obj = JSObject.create(env);
		obj.setOwnProperty("createRealm",  new D262Method(env,D262Id.createRealm,  0));
		obj.setOwnProperty("detachArrayBuffer", new D262Method(env,D262Id.detachArrayBuffer, 1));
		obj.setOwnProperty("evalScript",   new D262Method(env,D262Id.evalScript,   1));
		obj.setOwnProperty("gc",           new D262Method(env,D262Id.gc,           0));
		// $262.global is populated lazily in D262Method.call()
		obj.setOwnProperty("global",       RuntimeUtil.UNDEFINED);
		// Annex B.3.7 [[IsHTMLDDA]] internal slot - a real IsConstructor-
		// false, extensible, normal-property-definition-supporting object
		// (see superclass-emulates-undefined.js) that typeof/ToBoolean/
		// Abstract Equality Comparison treat as "undefined" while ===/
		// SameValue still correctly see it as a distinct, real object (see
		// JSEnvironment.isHTMLDDAObject() and its call sites in RuntimeUtil).
		// Per test262's own INTERPRETING.md contract for this host property,
		// it must ALSO be callable ("when called with no arguments or with
		// the first argument "" ... returns null") - e.g.
		// String.prototype.match/replace/etc's "custom-*-emulates-
		// undefined.js" install it as @@match/@@replace/etc and rely on
		// GetMethod's IsCallable check passing so Call() actually runs
		// (rather than GetMethod throwing a TypeError). A plain JSObject
		// has no [[Call]], so those tests failed with a TypeError instead
		// of the expected null-returning invocation.
		JSObject htmldda = new HTMLDDAObject(env);
		env.setHTMLDDAObject(htmldda);
		obj.setOwnProperty("IsHTMLDDA", htmldda);
		obj.setOwnProperty("agent", buildAgentObject(env));
		// %AbstractModuleSource% (source-phase-imports) - the proposal
		// defines no global for it, so test262 reaches it through $262.
		obj.setOwnProperty("AbstractModuleSource",
				org.monflabs.galtajs.rt.builtins.standard.module.AbstractModuleSourceConstructor.get(env));
		return obj;
	}

	// -----------------------------------------------------------------------
	// $262.agent - real cross-(Java-)thread agents. Only the RAW primitives
	// INTERPRETING.md requires are implemented in Java; test262's own
	// harness/atomicsHelper.js layers timeouts/safeBroadcast/tryYield/
	// trySleep/getReportAsync/etc. on top of these in pure JS.
	// -----------------------------------------------------------------------

	private JSObject buildAgentObject(JSEnvironment env) {
		JSObject agent = JSObject.create(env);
		agent.setOwnProperty("start",           new AgentMethod(env,AgentId.start,           1));
		agent.setOwnProperty("broadcast",        new AgentMethod(env,AgentId.broadcast,        1));
		agent.setOwnProperty("receiveBroadcast", new AgentMethod(env,AgentId.receiveBroadcast, 1));
		agent.setOwnProperty("report",           new AgentMethod(env,AgentId.report,           1));
		agent.setOwnProperty("getReport",        new AgentMethod(env,AgentId.getReport,        0));
		agent.setOwnProperty("sleep",            new AgentMethod(env,AgentId.sleep,            1));
		agent.setOwnProperty("leaving",          new AgentMethod(env,AgentId.leaving,          0));
		agent.setOwnProperty("monotonicNow",     new AgentMethod(env,AgentId.monotonicNow,     0));
		return agent;
	}

	private enum AgentId { start, broadcast, receiveBroadcast, report, getReport, sleep, leaving, monotonicNow }

	private final class AgentMethod extends BaseMethod {
		private final AgentId id;

		AgentMethod(JSEnvironment env, AgentId id, int length) {
			super(env, id.name(), length);
			this.id = id;
		}

		@Override
		public Object call(Object _this, Object[] args) {
			switch(id) {
				case start: {
					String source = paramString(args, 0);
					// A daemon thread: never blocks JVM/test-runner shutdown
					// even if a test bug (or a genuinely-unfixed engine gap)
					// leaves it permanently blocked - the real worst-case
					// cost of a stuck agent is this ONE test file's run
					// taking up to the surefire fork's own timeout, not a
					// process that never exits.
					Thread t = new Thread(() -> {
						Atomics.setCurrentThreadCanSuspend(true);
						try {
							JSEnvironment agentEnv = GlobalTest262Environment.newBuilder(agentManager).build();
							new JSScriptExecutor(agentEnv).execute(source);
						} catch(Throwable t2) {
							// Spec: an uncaught exception in an agent is
							// implementation-defined - surfacing it on
							// stderr is enough for test262's own purposes
							// (no test asserts on this engine's exact
							// handling); must NOT propagate to (or crash)
							// the spawning thread.
							t2.printStackTrace();
						}
					}, "test262-agent-"+AGENT_THREAD_COUNTER.incrementAndGet());
					t.setDaemon(true);
					t.start();
					return RuntimeUtil.UNDEFINED;
				}

				case broadcast: {
					Object sab = param(args, 0);
					agentManager.broadcast(sab);
					return RuntimeUtil.UNDEFINED;
				}

				case receiveBroadcast: {
					Object callbackArg = param(args, 0);
					if(!(callbackArg instanceof Callable callback)) {
						throw RuntimeUtil.typeError("receiveBroadcast callback is not a function");
					}
					// Blocks the CALLING (real Java) thread - always a
					// $262.agent.start()-spawned agent's own thread in every
					// test262 usage pattern actually seen, never the main
					// thread (which would just hang the whole test file
					// forever if it did this - not this method's job to
					// prevent that, test262 tests simply don't do it).
					Object sab = agentManager.receiveBroadcast();
					callback.call(RuntimeUtil.UNDEFINED, new Object[] { sab });
					return RuntimeUtil.UNDEFINED;
				}

				case report: {
					String s = RuntimeUtil.toString(getEnvironment(), param(args, 0));
					agentManager.report(s);
					return RuntimeUtil.UNDEFINED;
				}

				case getReport: {
					String r = agentManager.getReport();
					return r!=null ? r : null;
				}

				case sleep: {
					long ms = (long)RuntimeUtil.toNumber(getEnvironment(), param(args, 0)).doubleValue();
					try {
						Thread.sleep(Math.max(0, ms));
					} catch(InterruptedException e) {
						Thread.currentThread().interrupt();
					}
					return RuntimeUtil.UNDEFINED;
				}

				case leaving: {
					// No explicit bookkeeping needed - the agent's own
					// daemon thread simply finishes naturally once its
					// top-level script (which called this) returns.
					return RuntimeUtil.UNDEFINED;
				}

				case monotonicNow: {
					return System.nanoTime()/1_000_000.0;
				}

				default:
					throw new IllegalStateException();
			}
		}
	}

	// -----------------------------------------------------------------------
	// $262.IsHTMLDDA
	// -----------------------------------------------------------------------

	private static final class HTMLDDAObject extends BaseMethod {
		HTMLDDAObject(JSEnvironment env) {
			super(env, "IsHTMLDDA", 0);
		}

		@Override
		public Object call(Object _this, Object[] args) {
			// See INTERPRETING.md's IsHTMLDDA contract: called with no
			// arguments, or with the first argument the empty string, must
			// return null. Behavior for any other argument is unspecified
			// by the harness contract - undefined is as good as anything.
			if(args.length==0 || "".equals(RuntimeUtil.toString(getEnvironment(), args[0]))) {
				return null;
			}
			return RuntimeUtil.UNDEFINED;
		}
	}

	private enum D262Id { createRealm, detachArrayBuffer, evalScript, gc }

	private static final class D262Method extends BaseMethod {
		private final D262Id id;

		D262Method(JSEnvironment env, D262Id id, int length) {
			super(env, id.name(), length);
			this.id = id;
		}

		@Override
		public Object call(Object _this, Object[] args) {
			switch (id) {
				case createRealm: {
					// A minimal, non-spec-pure second realm: a genuinely
					// separate JSEnvironment (its own StandardObjects, so
					// `other.Int32Array`/`new other.Int32Array(...)` etc. are
					// real, independently-constructible intrinsics) exposed
					// as `.global`. This does NOT implement a full realm
					// (no `.evalScript` on the returned object, no recursive
					// `$262`, no isolation of error-constructor identity -
					// RuntimeUtil's error throwers resolve the AMBIENT
					// thread-local JSContext, not the throwing value's own
					// realm, so a TypeError thrown while operating on an
					// `other`-realm object is - by accident, not by design -
					// still `instanceof` the CALLING realm's TypeError,
					// which is exactly what the two target tests need).
					// Genuinely executing code INSIDE this second realm
					// (`other.evalScript(...)`) is NOT supported - only
					// built-ins/TypedArrayConstructors/internals/
					// DefineOwnProperty/{BigInt/,}detached-buffer-throws-
					// realm.js (which only ever construct instances via
					// `other`'s intrinsics, never eval code in it) are
					// targeted by this - full `features: [cross-realm]`
					// support remains out of scope (see UNSUPPORTED_FEATURES
					// above).
					// The realm's global object is the one owned by its
					// environment's own root context (JSEnvironment.
					// getRealmContext()) - the SAME context a cross-realm
					// `new other.Function()` / `other.eval(...)` runs in, so
					// a `var` declared through either is visible on
					// `other.<name>` and to a later `evalScript`, exactly as
					// one shared realm global should be.
					JSEnvironment realmEnv = GlobalTest262Environment.create();
					GlobalThis realmGlobal = realmEnv.getRealmContext().getGlobalThis();
					JSObject realmObj = JSObject.create(getEnvironment());
					realmObj.setOwnProperty("global", realmGlobal);
					// evalScript(source): evaluates source as a classic script
					// in that realm's global scope, returning its completion
					// value.
					realmObj.setOwnProperty("evalScript", new BaseMethod(realmEnv, "evalScript", 1) {
						@Override
						public Object call(Object _this, Object[] a) {
							String src = RuntimeUtil.toString(realmEnv, a.length>0 ? a[0] : RuntimeUtil.UNDEFINED);
							return realmEnv.evaluate(realmEnv.getRealmContext(), src);
						}
					});
					return realmObj;
				}

				case detachArrayBuffer: {
					// transfer(0) already detaches the source buffer as a side
					// effect (buf=null) - reuse it rather than adding a new API
					// just for this test-harness hook. Per spec, the
					// DetachArrayBuffer abstract operation is a no-op when the
					// buffer is already detached - ArrayBuffer.prototype.transfer()
					// instead THROWS in that case (confirmed via
					// sort/sort-tonumber.js, whose comparefn calls
					// $DETACHBUFFER on every invocation, not just the first),
					// so that case must be guarded here rather than delegated.
					Object buf = param(args, 0, null);
					if(buf instanceof ArrayBuffer ab && !ab.isDetached()) {
						ab.transfer(0);
					}
					return RuntimeUtil.UNDEFINED;
				}

				case evalScript: {
					String src = paramString(args, 0);
					try {
						// Per spec (%262%.evalScript), the source text runs as a
						// SCRIPT in the SAME REALM as the calling code - sharing
						// the same global object, so a property redefined via
						// Object.defineProperty(this, ...) before this call is
						// visible to (and can collide with) declarations in the
						// eval'd text. `.execute()` would instead create a
						// brand-new temporary global context every time,
						// silently starting a fresh, disconnected realm. Reuse
						// the CALLER's actual global context instead.
						JSRuntimeContext callerGlobalContext = JSRuntimeContext.get().getGlobalContext();
						if(callerGlobalContext instanceof org.monflabs.galtajs.rt.interpreter.JSInterpretedRuntimeContext) {
							return getEnvironment()
									.createScript(src, "<evalScript>")
									.executeWithContext(callerGlobalContext);
						}
						// The caller is TRANSPILED - JSInterpretedUnit.executeWithContext
						// unconditionally casts its context argument to
						// JSInterpretedRuntimeContext, which would throw
						// ClassCastException here (callerGlobalContext is a
						// TranspiledGlobalRuntimeContext). JSEnvironment.createScript()
						// has no "match the caller's own execution mode" API, so this
						// replicates the same transpile+compile+load pipeline
						// BaseTestSuiteTest already uses to run a transpiled-mode test
						// FILE, but pointed at the CALLER's own already-live
						// TranspiledGlobalRuntimeContext instead of constructing a new
						// one - JSTranspiledUnit's identifier-access helpers resolve
						// everything dynamically off the context passed to
						// executeWithContext() at call time (nothing is baked into the
						// compiled class), so this genuinely shares the caller's realm
						// (same globalThis), not a second disconnected one.
						return evalScriptTranspiled(src, callerGlobalContext);
					} catch (RuntimeException e) {
						// A genuine JS-level exception (TypeError/SyntaxError/etc,
						// including this session's GlobalDeclarationInstantiation
						// early-error work) is already the correct, observable JS
						// error type - rethrow it as-is instead of wrapping it into
						// a generic Error, which would defeat any
						// `assert.throws(TypeError, () => $262.evalScript(...))`.
						// Only a genuinely unexpected non-JS exception gets wrapped.
						throw e;
					} catch (Exception e) {
						throw RuntimeUtil.error(e, "evalScript error");
					}
				}

				case gc:
					System.gc();
					return RuntimeUtil.UNDEFINED;

				default:
					throw new IllegalStateException();
			}
		}

		// Per-call unique class name suffix - $262.evalScript(...) can be
		// called more than once within a single test file, and each call
		// needs its own freshly-compiled class (javac would reject a
		// duplicate class name reused across separate compile() calls
		// against separate MemoryFileSystem instances loaded by separate
		// PathClassLoaders, since nothing here caches/reuses a previous
		// compilation).
		private static final AtomicInteger EVAL_SCRIPT_COUNTER = new AtomicInteger();

		// Replicates BaseTestSuiteTest's own transpile+compile+load pipeline
		// (parent-js/js-test-suite's harness for running a transpiled-mode
		// test FILE) inline, entirely within this test-harness class - no
		// core-engine changes. The one deliberate difference from that
		// pipeline: runs the freshly-compiled unit against the CALLER's own
		// already-live TranspiledGlobalRuntimeContext (passed in), never a
		// fresh one - JSTranspiledUnit's identifier-access helpers resolve
		// everything dynamically off the context passed to
		// executeWithContext() at call time, so this genuinely shares the
		// caller's realm (same globalThis) instead of starting a second,
		// disconnected one.
		private Object evalScriptTranspiled(String src, JSRuntimeContext callerGlobalContext) throws Exception {
			JSEnvironment env = getEnvironment();
			JSInterpretedUnit parsed = env.createScript(src, "<evalScript>");
			String className = "EvalScript_" + EVAL_SCRIPT_COUNTER.incrementAndGet();
			JSTranspilerOptions options = JSTranspilerOptions.newBuilder().build();
			JSTranspiler transpiler = new JSTranspiler(env, options);
			String javaSource = transpiler.compile(className, "Object", parsed);

			MemoryFileSystem memoryFs = MemoryFileSystem.newBuilder().build();
			Path srcFs = Files.createDirectory(memoryFs.getPath("src"));
			Path tgtFs = Files.createDirectory(memoryFs.getPath("tgt"));
			Files.writeString(srcFs.resolve(className + ".java"), javaSource, StandardCharsets.UTF_8);

			try(JavaCompiler cp = JavaCompilerFactory.newBuilder()
					.classLoader(getClass().getClassLoader())
					.sourceFolder(srcFs, StandardCharsets.UTF_8)
					.targetFolder(tgtFs)
					.options(List.of("-Xdiags:verbose", "-Xlint:unchecked"))
					.build()) {
				cp.compile(className);
			}

			PathClassLoader cl = new PathClassLoader(getClass().getClassLoader(), tgtFs);
			Constructor<?> ctor = cl.loadClass(className).getConstructor(JSEnvironment.class, String.class);
			JSTranspiledUnit hw = (JSTranspiledUnit)ctor.newInstance(env, "<evalScript>");
			return hw.executeWithContext(callerGlobalContext);
		}
	}
}
