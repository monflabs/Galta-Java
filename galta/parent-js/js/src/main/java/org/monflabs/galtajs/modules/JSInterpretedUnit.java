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
package org.monflabs.galtajs.modules;

import java.io.PrintStream;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.JSModule;
import org.monflabs.galtajs.JSModuleDescriptor;
import org.monflabs.galtajs.jsonfactory.JSObject;
import org.monflabs.galtajs.node.ASTProgram;
import org.monflabs.galtajs.optimizer.JSOptimizerContext;
import org.monflabs.galtajs.optimizer.ScriptOptimizer;
import org.monflabs.galtajs.rt.JSGlobalContext;
import org.monflabs.galtajs.rt.JSGlobalContext.RunningState;
import org.monflabs.galtajs.rt.JSModuleContext;
import org.monflabs.galtajs.rt.JSResult;
import org.monflabs.galtajs.rt.JSRuntimeContext;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.interpreter.InterpretedGlobalRuntimeContext;
import org.monflabs.galtajs.rt.interpreter.InterpretedModuleRuntimeContext;
import org.monflabs.galtajs.rt.interpreter.JSInterpretedRuntimeContext;
import org.monflabs.galtajs.rt.transpiler.VarAccessor;
import org.monflabs.util.Console;



/**
 * Interpreted script.
 */
public class JSInterpretedUnit extends JSScriptUnit {

	public static final String DEFAULT_FILE_EXTENSION = "js";

    private ASTProgram program;
    private ModuleRuntimeContext moduleContext;
    // A module's own top-level runtime context, for getExportAccessor()'s
    // live-variable lookup. Set unconditionally in executeWithContext() -
    // covers BOTH a dependency module (initModule() calls
    // executeWithContext(moduleContext), so this ends up equal to
    // moduleContext anyway) AND a ROOT module execution (run directly via
    // executeWithContext(), which never calls initModule() at all, so
    // moduleContext itself stays permanently null - see
    // InterpretedGlobalRuntimeContext.registerRootModule()'s own doc
    // comment for why a root module still needs to resolve export
    // accessors from ITS OWN top-level scope, e.g. for a self-import).
    private JSInterpretedRuntimeContext executionContext;
    // Reliable "is this module the one currently executing THIS statement"
    // check for ASTImport's own self-reference detection - unlike
    // JSGlobalContext.getScriptUnit() (mutated by setScriptUnit() on every
    // NESTED module load reached via RuntimeUtil.importModule(), with no
    // restoration afterward - a real, pre-existing footgun for anyone
    // comparing it after that call), this field is set exactly ONCE, right
    // at the top of THIS unit's own executeWithContext() call, and never
    // touched again.
    public JSInterpretedRuntimeContext getExecutionContext() {
    	return executionContext;
    }

    // Deferred retries for a SELF-import reaching this module's own
    // NON-hoistable default export (`export default <expr>`/`export
    // default class C{}`) before it's run - unlike a hoistable default
    // (task #256/#260) or a named item (ASTImport's own unresolvedItems),
    // there is no later opportunity within this module's own single,
    // top-to-bottom evaluate() pass to retry (the self-importing `import`
    // statement is positioned BEFORE the `export default` in source, so
    // by the time the import statement's own evaluate() runs, the default
    // export genuinely hasn't been produced yet). Fires as soon as
    // setDefaultExport() is actually called (see the override below) - NOT
    // deferred to whole-body completion (an earlier version did that,
    // which is wrong whenever the self-importing module's OWN later code,
    // after its own `export default`, reads the binding again before the
    // body truly ends: test262 module-self-import-async-resolution-ticks.js
    // reads `self` a second time after its own `export default await ...`
    // but before $DONE() - waiting for the whole body to finish would
    // never let that second read observe the value at all, since the read
    // itself is part of the same still-in-progress body). Immediate,
    // synchronous fire if the default export is ALREADY set by the time
    // this is called - mirrors addEvaluationCompletionCallback()'s own
    // "already terminal" fast path. See ASTImport.evaluate()'s own doc
    // comment for the registering side.
    private java.util.List<Runnable> defaultExportCallbacks;
    public void addDefaultExportCallback(Runnable r) {
    	if(hasDefaultExport()) {
    		r.run();
    		return;
    	}
    	if(defaultExportCallbacks==null) {
    		defaultExportCallbacks = new java.util.ArrayList<>();
    	}
    	defaultExportCallbacks.add(r);
    }
    @Override
    public void setDefaultExport(Object defaultExport) {
    	super.setDefaultExport(defaultExport);
    	if(defaultExportCallbacks!=null) {
    		java.util.List<Runnable> callbacks = defaultExportCallbacks;
    		defaultExportCallbacks = null;
    		for(Runnable cb: callbacks) {
    			cb.run();
    		}
    	}
    }

    // Deferred retry for a circular `export * from` (A `export * from B`, B
    // `export * from A`, sourceModule still mid-evaluation when reached) -
    // unlike the default-export case above, there is no single "the value
    // is now final" moment to hook (addStarReExport()'s target is this
    // module's whole, ongoing namedExports set) - run once, right after
    // sourceModule's ENTIRE body finishes (see executeWithContext()'s own
    // call site), by which point every one of its own statements has had a
    // chance to run. See ASTExport.evaluate()'s own doc comment for the
    // registering side.
    private java.util.List<Runnable> settleCallbacks;
    public void addSettleCallback(Runnable r) {
    	if(settleCallbacks==null) {
    		settleCallbacks = new java.util.ArrayList<>();
    	}
    	settleCallbacks.add(r);
    }

    // A caller (so far, only dynamic import()'s own promise resolution -
    // RuntimeUtil.dynamicImportWithAttributes()) that needs to know when
    // this module's evaluation GENUINELY finishes, not just when
    // importModule() happened to return - see executeWithContext()'s own
    // doc for why those two can differ (a module with its OWN top-level
    // await, reached from a NESTED execute() call, e.g. dynamic import's
    // queued microtask, which is always nested since it runs from inside
    // the outer drainPendingTasks() loop). Fires immediately, synchronously,
    // if evaluation has ALREADY genuinely finished (either terminal status)
    // by the time this is called - the common case (no top-level await, or
    // a NON-nested caller, where execute() already only returns once truly
    // done). Never fired for isPendingDeferredEvaluation()'s own "cached
    // but not yet evaluated" state - only once real evaluation is actually
    // running (moduleStatus is EVALUATING or later by the time anyone
    // could reach this).
    private java.util.List<Runnable> evaluationCompletionCallbacks;
    public void addEvaluationCompletionCallback(Runnable r) {
    	if(moduleStatus==ModuleStatus.EVALUATED || moduleStatus==ModuleStatus.ERRORED) {
    		r.run();
    		return;
    	}
    	if(evaluationCompletionCallbacks==null) {
    		evaluationCompletionCallbacks = new java.util.ArrayList<>();
    	}
    	evaluationCompletionCallbacks.add(r);
    }

    public JSInterpretedUnit(JSEnvironment env, ASTProgram program, JSModuleDescriptor descriptor) {
    	super(env,descriptor);
    	this.program = program;
    }

    public String getText() {
    	return program.getText();
    }

    // program.isGenuinelyStrictMode() (not isForceStrictMode()) - for an
    // ordinary script/module program the two are identical (no caller to
    // inherit strictness from), but for a direct eval's freshly-parsed
    // program they diverge: isForceStrictMode() reflects only the eval'd
    // text's own directive prologue, while isGenuinelyStrictMode() also
    // folds in the calling context's genuine strictness (ASTProgram's
    // callerForcedStrict, set from __init's `forceStrict` param) - which
    // every consumer of this method (executeForEval's eval-context runtime
    // strictness, InterpretedGlobalRuntimeContext/TranspiledGlobalRuntimeContext's
    // isStrictMode() while a nested eval is the active scriptUnit) needs.
    @Override
	public boolean isForceStrictMode() {
    	return program.isGenuinelyStrictMode();
    }


    /////////////////////////////////////////////////////////////////////////////
    // Execution
    /////////////////////////////////////////////////////////////////////////////

    @Override
	public Object execute() {
    	return executeThis(null);
    }
    @Override
	public Object executeThis(Object _this) {
    	InterpretedGlobalRuntimeContext context = new InterpretedGlobalRuntimeContext(
    			getEnvironment(),
    			getEnvironment().createProgramExecutor(),
    			_this);
    	return executeWithContext(context);
    }
    @Override
	public Object executeWithContext(JSRuntimeContext _context) {
    	JSInterpretedRuntimeContext context = (JSInterpretedRuntimeContext)_context;
    	this.executionContext = context;
    	if(program.isModule()) {
    		// See ModuleStatus's own doc comment. Covers BOTH a root
    		// module (this method reached directly, never through
    		// initModule()) and a dependency (initModule() calls this
    		// same method) - the ONE common entry point for a module's
    		// own body actually starting to run.
    		moduleStatus = ModuleStatus.EVALUATING;
    		boolean ready = context.with( () -> {
    			// linkModule() below resolves each dependency's own request
    			// via RuntimeUtil.importModule(context, req), which - for a
    			// ROOT module specifically (context.getMainContext()==context
    			// itself, self-referential; a DEPENDENCY's own
    			// InterpretedModuleRuntimeContext is unaffected, its
    			// getScriptUnit() is a stable field set at construction, not
    			// this shared one) - needs gc.getScriptUnit() to already
    			// resolve to `this`, to know what THIS module's own path is
    			// relative to. runBody() below sets this too (restored in
    			// its own finally), but that happens too LATE for
    			// linkModule() - dependency resolution must already be able
    			// to answer it. Save/restore here so a nested dependency's
    			// OWN linkModule()/runBody() calls (reached synchronously,
    			// recursively, from inside the loop below) don't leave this
    			// pointing at the wrong module once THIS call returns.
    			JSGlobalContext gc = context.getGlobalContext();
    			JSScriptUnit oldScriptUnit = gc.getScriptUnit();
    			gc.setScriptUnit(this);
    			try {
    				// Self-registration for a ROOT module execution (never
    				// itself reached via importModule()) - see
    				// JSGlobalContext.registerRootModule()'s own doc comment
    				// for why this is needed for a root file's self-/
    				// circular-import to truly find THIS SAME instance
    				// instead of recursing into loading (and fully
    				// re-running) a duplicate copy. MUST happen before
    				// linkModule() below, not after (originally this ran as
    				// part of runBody(), reached only once ready - too late:
    				// a self-import discovered DURING linkModule()'s own
    				// dependency walk, e.g. a fixture module that re-imports
    				// this root module back, needs to find it registered
    				// already). Polymorphic (not gated to
    				// InterpretedGlobalRuntimeContext specifically) - a
    				// module's body always executes via JSInterpretedUnit
    				// regardless of whether the overall program is running
    				// in transpiled mode (modules aren't ahead-of-time
    				// transpiled per file), so a transpiled-mode-configured
    				// environment running a module ROOT needs this exact
    				// same self-registration too (test262 import-defer/
    				// errors/get-self-while-evaluating.js under transpiled
    				// mode).
    				gc.registerRootModule(this);
    				// A ROOT module shares this SAME JSGlobalContext as its
    				// own `this` owner (a DEPENDENCY module loaded via
    				// initModule() gets a separate ModuleRuntimeContext
    				// instead, whose own `_this` is already correctly
    				// null/undefined from construction) - the constructor's
    				// own "no `this` specified, default to globalThis"
    				// fallback (correct for a SCRIPT) must be overridden
    				// here: a module's `this` is ALWAYS undefined,
    				// unconditionally, never globalThis (test262
    				// eval-this.js).
    				gc.setThis(RuntimeUtil.UNDEFINED);
    				// This module's OWN let/const/var/function bindings must
    				// already exist before linkModule() below starts
    				// resolving dependencies - see ASTProgram.
    				// hoistDeclarations()'s own doc for why (a circular
    				// dependency reading back into one of THIS module's own,
    				// not-yet-hoisted bindings otherwise fails).
    				program.hoistDeclarations(context);
    				// linkModule() runs OUTSIDE any coroutine's own
    				// synchronous prefix (it's reached BEFORE this module's
    				// own runBody()/startCoroutine() call even begins), but
    				// its dependency walk can synchronously start a
    				// DEPENDENCY module's own async execution (an ordinary
    				// static `import` of an async module) - that nested
    				// execute(...,true) call must not drain the whole
    				// microtask queue to completion right there (it would,
    				// by default, since nothing else has claimed
    				// `draining` yet at this point) or a sibling dependency
    				// processed LATER in this same loop would wrongly
    				// observe state as it exists AFTER the first
    				// dependency's entire async body already ran, instead
    				// of only its synchronous prefix (test262
    				// async-module-does-not-block-sibling-modules.js). See
    				// JSExecutor.runWithDrainSuppressed()'s own doc.
    				return gc.getExecutor().runWithDrainSuppressed(() -> linkModule(context));
    			} finally {
    				gc.setScriptUnit(oldScriptUnit);
    			}
    		});
    		if(!ready) {
    			// See linkModule()'s own doc: this module has at least one
    			// still-pending async dependency - its body is deliberately
    			// NOT started here. notifyAsyncParents() (called from
    			// whichever dependency this is waiting on, once THAT one
    			// genuinely finishes) calls runBody() for it later, once
    			// pendingAsyncDependencies reaches 0.
    			//
    			// Nothing else will ever trigger a drain of the pending
    			// microtask queue for a module that defers here - runBody()
    			// (the ONLY other call site that reaches execute(), which is
    			// what normally triggers a drain) is never even called.
    			// Without this, a suspended dependency's own continuation
    			// (e.g. mid-`await` inside a sibling module started from
    			// linkModule()'s own dependency walk above) would simply
    			// never run, permanently hanging both it and this module
    			// (test262 async-module-does-not-block-sibling-modules.js).
    			// Correctly a no-op for a NESTED module reaching this same
    			// point (still inside an ANCESTOR's own
    			// runWithDrainSuppressed() scope) - only the true outermost
    			// module's own deferral actually drains.
    			context.with( () -> {
    				// Same gc.scriptUnit save/restore as linkModule()'s own
    				// call site above, for the same reason - a queued
    				// microtask this drain processes (e.g. a dynamic
    				// import()'s own module-resolution callback) may need to
    				// resolve a RELATIVE specifier, which requires
    				// gc.getScriptUnit() to still resolve to `this`; the
    				// EARLIER save/restore already put it back to
    				// oldScriptUnit by the time control reaches here (confirmed
    				// via a genuine NPE: InterpretedGlobalRuntimeContext.
    				// importModule() dereferencing a null getScriptUnit(),
    				// test262 module-graphs-does-not-hang.js's own `await
    				// import(...)` microtask, reached from INSIDE this drain).
    				JSGlobalContext gc = context.getGlobalContext();
    				JSScriptUnit oldScriptUnit = gc.getScriptUnit();
    				gc.setScriptUnit(this);
    				try {
    					gc.getExecutor().drainAndShutdownIfOutermost();
    				} finally {
    					gc.setScriptUnit(oldScriptUnit);
    				}
    				return null;
    			});
    			return RuntimeUtil.UNDEFINED;
    		}
    	}
    	return context.with( () -> runBody(context) );
    }

    // Runs this module's (or a plain script/eval unit's) own body NOW -
    // either called inline, synchronously, from executeWithContext() above
    // (the overwhelmingly common case: a script/eval, or a module whose own
    // dependency-graph link phase found nothing left to wait on), or later,
    // from notifyAsyncParents(), for a module that was deferred there and
    // has now become ready. Always invoked from within context.with() - see
    // both call sites.
    private Object runBody(JSInterpretedRuntimeContext context) {
    	JSGlobalContext gc = context.getGlobalContext();
    	RunningState oldState = gc.getRunningState();
    	gc.setRunningState(RunningState.RUNNING);
    	// Saved/restored symmetrically with oldState below - unlike
    	// executeForEval()'s own identical save/restore (which this
    	// mirrors), this was PREVIOUSLY MISSING here: gc.setScriptUnit()
    	// is GLOBAL, MUTABLE state, shared across the whole module
    	// graph - a nested module load (any import/export-from reached
    	// from within this module's own body) leaves it pointing at
    	// whatever it loaded LAST, with nothing to put it back. For a
    	// DEPENDENCY module this was usually harmless (its own
    	// ASTExport/ASTImport statements read `moduleContext.
    	// getScriptUnit()` off their own properly-scoped
    	// InterpretedModuleRuntimeContext, not gc, so they're immune),
    	// but a ROOT module's own top-level context genuinely IS this
    	// same InterpretedGlobalRuntimeContext - so a root module with
    	// two or more export/import-from statements could silently
    	// misattribute a LATER statement's own addNamedExports()/
    	// addStarReExport() calls onto whatever module an EARLIER
    	// statement most recently (transitively) loaded, instead of
    	// onto itself. Confirmed via namespace-unambiguous-if-export-
    	// star-as-from.js: two chained `export * from` statements at
    	// root level, the second one's contribution was silently lost
    	// onto the wrong module (previously masked - the first
    	// statement alone happened to already satisfy that specific
    	// test's own assertion; resolveExport()'s new EARLIER, hoist-
    	// time resolution attempts made the corruption reach even the
    	// FIRST statement, turning this latent bug into an observable
    	// regression, which is how it was actually found).
    	JSScriptUnit oldScriptUnit = gc.getScriptUnit();
		try {
    		gc.setScriptUnit(this); // we keep this for later access when evaluating properties
    		if(gc instanceof InterpretedGlobalRuntimeContext igc) {
    			igc.setOwnForceStrictMode(isForceStrictMode());
    			// Root-module self-registration/`this` binding now happens
    			// EARLIER, in executeWithContext() - see its own doc for why
    			// (must be visible to a self-/circular-import reached from
    			// linkModule()'s own dependency walk, which runs BEFORE this
    			// method at all for a module).
    		}
			// The completion callback (3rd arg), NOT the code right after
			// this call, is where settleCallbacks/moduleStatus/
			// evaluationError get updated - see JSExecutor.execute()'s own
			// doc: for a module with its own top-level await reached from
			// a NESTED execute() call (dynamic import()'s queued microtask
			// above all - always nested, since it runs from inside the
			// outer drainPendingTasks() loop), this call's own RETURN
			// happens after only the synchronous prefix, genuinely before
			// the module is done - the callback below, by contrast, is
			// guaranteed to fire exactly once with the TRUE final outcome,
			// synchronously here if evaluation already finished within
			// that prefix, or later (from inside a promise reaction)
			// otherwise. Fixes await-dynamic-import-resolution.js and
			// ~10 sibling files (see KnownGaps.md) that were previously
			// observing this module's exports/status via
			// addEvaluationCompletionCallback() before they were actually
			// set, because moduleStatus was marked EVALUATED unconditionally
			// right after this call returned, regardless of whether that
			// return was genuine.
			Object r = gc.getExecutor().execute( () -> {
		    	return program.evaluateValue(context,new JSResult());
			}, program.isAsyncExecution(), (value, error) -> {
	    		if(error!=null) {
	    			if(program.isModule()) {
	    				// See ModuleStatus's own doc comment - the SAME
	    				// JS-visible error value is re-thrown verbatim on
	    				// every later access attempt (test262 import-defer/
	    				// errors/module-throws/*.js), never re-evaluated.
	    				moduleStatus = ModuleStatus.ERRORED;
	    				evaluationError = org.monflabs.galtajs.rt.JSRuntimeException.exceptionObject(error);
	    			}
	    		} else {
		    		// See settleCallbacks' own field doc comment.
		    		if(settleCallbacks!=null) {
		    			java.util.List<Runnable> callbacks = settleCallbacks;
		    			settleCallbacks = null;
		    			for(Runnable cb: callbacks) {
		    				cb.run();
		    			}
		    		}
		    		if(program.isModule()) {
		    			moduleStatus = ModuleStatus.EVALUATED;
		    			// Spec AsyncModuleExecutionFulfilled step 2: reset
		    			// [[AsyncEvaluation]] to false now that this module's
		    			// own body has genuinely finished - linkModule()'s
		    			// dependency loop (dep.asyncEvaluation check) tests
		    			// this to decide whether a dependency is STILL
		    			// pending; leaving it permanently true after
		    			// completion made an already-EVALUATED dependency
		    			// look eternally pending to any LATER importer,
		    			// registering a wait that would never be notified
		    			// (asyncParentModules is drained, and emptied, by
		    			// notifyAsyncParents() the ONE time this module's own
		    			// completion callback fires) - a genuine hang for any
		    			// dependency whose entire async body happens to
		    			// settle synchronously, before linkModule()'s own
		    			// RuntimeUtil.importModule() call even returns
		    			// (test262 top-level-await/module-import-resolution.js).
		    			asyncEvaluation = false;
		    		}
	    		}
	    		if(program.isModule() && evaluationCompletionCallbacks!=null) {
	    			java.util.List<Runnable> callbacks = evaluationCompletionCallbacks;
	    			evaluationCompletionCallbacks = null;
	    			for(Runnable cb: callbacks) {
	    				cb.run();
	    			}
	    		}
	    		// Cascade: anyone deferred waiting specifically on THIS
	    		// module (see linkModule()'s own asyncParentModules doc) may
	    		// now be ready to start (or, on error, must be told to stop
	    		// waiting and propagate it) - see notifyAsyncParents()'s own
	    		// doc.
	    		if(program.isModule()) {
	    			notifyAsyncParents();
	    		}
    		});
	    	if(r instanceof CharSequence cs) {
	    		return cs.toString();
	    	}
	    	return r;
		} catch(Throwable ex) {
			// moduleStatus/evaluationError above are already set by the
			// completion callback by the time this catches anything - the
			// ONLY way an exception reaches here at all (as opposed to a
			// later promise rejection, which never propagates through
			// this call stack) is execute()'s own synchronous throw path,
			// which always invokes that callback first.
			throw ex;
		} finally {
			gc.setRunningState(oldState);
			gc.setScriptUnit(oldScriptUnit);
		}
    }

    // One shared DFS walk (spec InnerModuleEvaluation's own `stack`/
    // `index`), scoped per-thread: the link phase itself is pure,
    // synchronous Java recursion (resolving/starting a dependency never
    // needs to suspend - only actual BODY execution, later, might, via the
    // ordinary await()/coroutine machinery, unrelated to this), so a
    // thread-local correctly threads the SAME walk through nested
    // dependency loads (RuntimeUtil.importModule() -> ... ->
    // JSInterpretedUnit.initModule() -> executeWithContext() ->
    // linkModule() again, recursively, all still on this one thread)
    // without needing to change any of those methods' own public
    // signatures to carry it explicitly.
    private static final ThreadLocal<LinkState> CURRENT_LINK = new ThreadLocal<>();
    private static final class LinkState {
    	final java.util.List<JSInterpretedUnit> stack = new java.util.ArrayList<>();
    	int index;
    }
    // Spec [[AsyncEvaluationOrder]] - only its RELATIVE ordering among
    // modules from the SAME dependency graph matters (for
    // notifyAsyncParents()' own doc/simplification - see there for why this
    // implementation doesn't need a sortedExecList at all), so one counter
    // shared across the whole JVM (not reset per environment/run) is safe.
    private static final java.util.concurrent.atomic.AtomicInteger ASYNC_ORDER_COUNTER = new java.util.concurrent.atomic.AtomicInteger();

    // Spec [[DFSIndex]]/[[DFSAncestorIndex]] (-1 = not yet visited).
    private int dfsIndex = -1;
    private int dfsAncestorIndex = -1;
    // Spec [[CycleRoot]] - null while still open (mid-DFS, on `stack`
    // somewhere); once set (SCC-closed), points to the first-visited member
    // of this module's own strongly-connected component (itself, for a
    // non-cyclic module).
    private JSInterpretedUnit cycleRoot;
    // Null only before this module's own linkModule() has ever run
    // (SCC-closing always sets it, even for a non-cyclic module - see the
    // field's own doc). Exposed for InterpretedGlobalRuntimeContext.
    // importModule()'s own cache-hit check: a LATER, separate import of a
    // module that finished successfully but whose CYCLE ROOT subsequently
    // errored (a sibling cycle member, not `this` itself) must still
    // reject with that recorded error (spec Evaluate() step: "If module.
    // CycleRoot is not empty, set module to module.CycleRoot" happens
    // BEFORE the TopLevelCapability/error check - test262 import-
    // fulfilled-member-of-errored-cycle.js).
    public JSInterpretedUnit getCycleRoot() {
    	return cycleRoot;
    }
    // The member that actually tracks this module's SCC-wide completion
    // status: this module's own cycle root, or itself if it's not (yet)
    // part of any cycle (cycleRoot==null before this module's own
    // linkModule() has run, or genuinely itself once it has, for a
    // non-cyclic module). Needed wherever spec's IsModuleSCCEvaluated (or
    // an [[AsyncEvaluation]] check standing in for it) must consult "is
    // this whole strongly-connected component done", not just this
    // member's own possibly-already-finished-individually status - a
    // non-root cycle member's own body can complete
    // (moduleStatus==EVALUATED) well before the rest of its cycle (and
    // hence the cycle's shared root) does.
    public JSInterpretedUnit getCycleRootOrSelf() {
    	return cycleRoot!=null ? cycleRoot : this;
    }
    // Spec [[AsyncEvaluation]].
    private boolean asyncEvaluation;
    // GaltaJS has no separate EVALUATING-ASYNC ModuleStatus (see that
    // enum's own doc comment) - a module suspended mid-body on its own
    // `await` and one genuinely still executing synchronously right now
    // both show moduleStatus==EVALUATING. This distinguishes them: cleared
    // to false only once a module's body genuinely, fully completes (see
    // the completion callback in executeWithContext()), so it stays true
    // throughout a suspension. Needed wherever a raw EVALUATING check is
    // meant to mean spec's real "genuinely still on the call stack right
    // now" (e.g. GatherAsynchronousTransitiveDependencies step 6), not
    // "evaluating-async" (which spec does NOT exclude at that step - a
    // suspended module with its own [[HasTLA]] is exactly what needs to be
    // gathered, not skipped).
    public boolean isAsyncEvaluation() {
    	return asyncEvaluation;
    }
    // Spec [[PendingAsyncDependencies]].
    private int pendingAsyncDependencies;
    // Spec [[AsyncParentModules]] - populated ONLY via linkModule()'s own
    // dep.cycleRoot redirect (spec step 11.c.iv.1), so a waiting parent is
    // always registered against a dependency's CYCLE ROOT, never a non-root
    // cycle member directly (a no-op distinction for a non-cyclic
    // dependency, whose own cycleRoot is itself).
    private java.util.List<JSInterpretedUnit> asyncParentModules;

    // Implements spec's InnerModuleEvaluation (16.2.1.5.3), narrowed to
    // GaltaJS's own lazy, incremental module-loading model - dependencies
    // are discovered/loaded HERE, recursively, the first time each is
    // reached (GaltaJS has no separate parse-time linking pass distinct
    // from evaluate - see ModuleStatus's own doc). Returns true if this
    // module is ready to run its own body NOW (pendingAsyncDependencies==0
    // once every request is processed); false if it must wait - its body
    // will be started LATER, by notifyAsyncParents(), once whichever
    // dependencies it's still pending on finish.
    //
    // Reached exactly once per module (guarded by moduleStatus==EVALUATING
    // only being set once, in executeWithContext()) - a SUBSEQUENT request
    // for the SAME module (a self-/circular-reference, or simply a shared
    // dependency reached again via a different import path) instead hits
    // the "already cached" branch in InterpretedGlobalRuntimeContext.
    // importModule(), never reaching this method a second time at all.
    private boolean linkModule(JSInterpretedRuntimeContext context) {
    	LinkState state = CURRENT_LINK.get();
    	boolean outermost = state==null;
    	if(outermost) {
    		state = new LinkState();
    		CURRENT_LINK.set(state);
    	}
    	try {
    		dfsIndex = state.index;
    		dfsAncestorIndex = state.index;
    		pendingAsyncDependencies = 0;
    		state.index++;
    		state.stack.add(this);
    		// ONE combined, source-ORDER-preserving walk (spec's own
    		// InnerModuleEvaluation builds a single evaluationList this way,
    		// interleaving ordinary requests with each `import defer`
    		// request's own GatherAsynchronousTransitiveDependencies result
    		// in source position, NOT two separate passes) - see
    		// ASTProgram.getModuleEvaluationOrder()'s own doc comment. Getting
    		// this interleaving right is observable (test262 flattening-
    		// order.js asserts an exact evaluation-order array).
    		for(ASTProgram.ModuleRequestItem item: program.getModuleEvaluationOrder()) {
    			if(!item.deferred()) {
    				JSModule required;
    				try {
    					required = RuntimeUtil.importModule(context, item.specifier());
    				} catch(RuntimeException ex) {
    					unwindOnError(state, org.monflabs.galtajs.rt.JSRuntimeException.exceptionObject(ex));
    					throw ex;
    				}
    				if(required instanceof JSInterpretedUnit dep) {
    					if(dep.cycleRoot==null) {
    						// Still open (mid-DFS on THIS same walk, reached
    						// via SOME ancestor's own stack frame, possibly
    						// `this` itself for a self-import) - a cycle. Per
    						// spec step 11.c.iii, this only ever tightens the
    						// ancestor index - NEVER contributes to
    						// pendingAsyncDependencies, so a purely-
    						// synchronous cycle (however many members) never
    						// waits on anything.
    						if(dep.dfsAncestorIndex<dfsAncestorIndex) {
    							dfsAncestorIndex = dep.dfsAncestorIndex;
    						}
    					} else {
    						registerDependencyPending(state, dep);
    					}
    				}
    				// A non-JSInterpretedUnit dependency (native/JSON/text
    				// module) is always synchronously ready the instant
    				// importModule() returns it - nothing further to track.
    			} else {
    				// `import defer` itself is deliberately NEVER eagerly
    				// resolved/loaded here (that would defeat the entire
    				// point of deferring) - but per spec, its GATHERED
    				// async (HasTLA) pieces genuinely are part of the SAME
    				// evaluationList/InnerModuleEvaluation walk as ordinary
    				// requests, in the SAME source-order position - so each
    				// one is STARTED right here too (same
    				// startAsyncDependencyEvaluation() ASTImport.
    				// hoistBindings() also calls, safe to call twice - it
    				// no-ops once a unit is no longer pending-deferred),
    				// not left until this module's own later, separate
    				// hoistBindings() pass - which runs AFTER every ordinary
    				// eager import above has ALREADY fully evaluated,
    				// wrongly collapsing "1, defer-2-pieces, 3, defer-4-
    				// pieces, 5" into "1, 3, 5, defer-2-pieces, defer-4-
    				// pieces" (test262 flattening-order.js). Once started
    				// (or if it was already running/finished elsewhere -
    				// e.g. reached via some OTHER, unrelated import path),
    				// its cycleRoot is set - redirect through it exactly
    				// like an ordinary dependency, so THIS module becomes
    				// properly pending if that cycle root is still async
    				// (test262 async-cycle-dependency-of-deferred-
    				// module.js). Deliberately does NOT tighten
    				// dfsAncestorIndex the way the ordinary-request branch
    				// above does for a still-open (cycleRoot==null)
    				// dependency - `import defer` never creates a REAL
    				// edge in the eager dependency graph, so a target
    				// that's still mid-DFS elsewhere (not yet resolved
    				// into any cycle) is simply left alone here.
    				java.util.List<JSInterpretedUnit> asyncDeps;
    				try {
    					asyncDeps = context.getGlobalContext().gatherAsynchronousTransitiveDependencies(context.getMainContext(), item.specifier());
    				} catch(RuntimeException ex) {
    					unwindOnError(state, org.monflabs.galtajs.rt.JSRuntimeException.exceptionObject(ex));
    					throw ex;
    				}
    				for(JSInterpretedUnit dep: asyncDeps) {
    					try {
    						context.getGlobalContext().startAsyncDependencyEvaluation(dep, context.getGlobalContext());
    					} catch(RuntimeException ex) {
    						unwindOnError(state, org.monflabs.galtajs.rt.JSRuntimeException.exceptionObject(ex));
    						throw ex;
    					}
    					if(dep.cycleRoot!=null) {
    						registerDependencyPending(state, dep);
    					}
    				}
    			}
    		}
    		asyncEvaluation = pendingAsyncDependencies>0 || program.isAsyncExecution();
    		if(asyncEvaluation) {
    			asyncEvaluationOrder = ASYNC_ORDER_COUNTER.incrementAndGet();
    		}
    		// SCC-closing (spec step 15): only the DFS ROOT of this
    		// module's own strongly-connected component
    		// (dfsAncestorIndex==dfsIndex - unconditionally true for a
    		// non-cyclic module; true only for the FIRST-VISITED member of
    		// a genuine cycle) pops the whole component off `stack` here,
    		// assigning itself as cycleRoot for every member (including
    		// itself). A non-root cycle member is left ON the stack for
    		// its own ancestor to close later.
    		if(dfsAncestorIndex==dfsIndex) {
    			while(true) {
    				JSInterpretedUnit m = state.stack.remove(state.stack.size()-1);
    				m.cycleRoot = this;
    				if(m==this) {
    					break;
    				}
    				// `m` is a NON-ROOT cycle member. By this point its own
    				// body has already STARTED (it became "ready" - see the
    				// dependency loop above's `dep.cycleRoot==null` branch,
    				// which never blocks a still-open cyclic edge - and any
    				// module reaching pendingAsyncDependencies==0 runs its
    				// body immediately, synchronously, in
    				// executeWithContext()) - if it's genuinely async, it's
    				// currently suspended mid-body, not finished. Per real
    				// engine behavior (confirmed via direct experimentation
    				// against Node.js/V8, NOT derivable from the spec text
    				// alone - see KnownGaps.md's own entry for the full
    				// repro), a cycle's non-root member(s) must run to FULL
    				// completion, one at a time, in this same pop order,
    				// BEFORE the cycle ROOT's own body (`this`, reached
    				// once this loop exits) starts - unlike an ordinary
    				// (non-cyclic) async dependency, which must NOT block a
    				// later sibling (test262 async-module-does-not-block-
    				// sibling-modules.js) but MAY freely interleave with
    				// it. drainUntil() drives the SAME microtask queue this
    				// suspended module's own continuation is sitting in,
    				// stopping the instant `m` settles (not the whole
    				// queue) - test262 pending-async-dep-from-cycle.js.
    				if(m.asyncEvaluation) {
    					context.getGlobalContext().getExecutor().drainUntil(
    						() -> m.moduleStatus==ModuleStatus.EVALUATED || m.moduleStatus==ModuleStatus.ERRORED);
    				}
    			}
    		}
    		return pendingAsyncDependencies==0;
    	} finally {
    		if(outermost) {
    			CURRENT_LINK.remove();
    		}
    	}
    }
    private int asyncEvaluationOrder;

    // Spec step 11.c.iv-v: redirects to `dep`'s OWN cycle root before doing
    // anything else (a no-op reassignment for a non-cyclic dep, whose own
    // cycleRoot is itself - only actually changes `waitFor` for a genuine
    // cycle) - MUST redirect before both the error check AND the async-
    // pending registration, not just the latter - registering the error
    // check against the wrong (non-root) member of a cycle, or waiting on
    // a non-root member directly instead of its cycle's shared completion,
    // is observably wrong whenever a module reaches a cycle from OUTSIDE it
    // via a member other than the root (test262 pending-async-dep-from-
    // cycle.js, which exercises exactly this step). Shared by linkModule()'s
    // two dependency loops (ordinary eager requests, and `import defer`'s
    // gathered async transitive dependencies) - `dep` is only ever passed
    // here once already confirmed DFS-closed (dep.cycleRoot!=null) by both
    // callers.
    private void registerDependencyPending(LinkState state, JSInterpretedUnit dep) {
    	JSInterpretedUnit waitFor = dep.cycleRoot;
    	if(waitFor.moduleStatus==ModuleStatus.ERRORED) {
    		unwindOnError(state, waitFor.evaluationError);
    		throw RuntimeUtil.wrap(waitFor.evaluationError);
    	}
    	if(waitFor.asyncEvaluation) {
    		// Its own body hasn't genuinely finished yet - we become
    		// pending on it (spec step 11.c.v).
    		pendingAsyncDependencies++;
    		if(waitFor.asyncParentModules==null) {
    			waitFor.asyncParentModules = new java.util.ArrayList<>();
    		}
    		waitFor.asyncParentModules.add(this);
    	}
    }

    // Unwinds the current DFS stack down to (and including) `this`,
    // marking every popped member ERRORED with `err` - spec
    // InnerModuleEvaluation step 8: an abrupt completion while resolving
    // dependencies fails the WHOLE currently-open component, not just the
    // one module whose own request triggered it (a cycle's OTHER members
    // must see the SAME failure too, not silently appear to have
    // succeeded). Safe to call with `this` not yet corresponding to the
    // top of `stack` (an error surfacing partway through the dependency
    // loop) - `this` is always still somewhere in `stack` at that point
    // (pushed at the top of linkModule(), never popped until SCC-closing,
    // which hasn't run yet if we're still inside the loop that failed).
    private void unwindOnError(LinkState state, Object err) {
    	while(!state.stack.isEmpty()) {
    		JSInterpretedUnit m = state.stack.remove(state.stack.size()-1);
    		m.moduleStatus = ModuleStatus.ERRORED;
    		m.evaluationError = err;
    		if(m==this) {
    			break;
    		}
    	}
    }

    // Mirrors spec's AsyncModuleExecutionFulfilled/AsyncModuleExecutionRejected
    // own ancestor-notification step (GatherAvailableAncestors), called
    // once THIS module's own body has genuinely finished (from runBody()'s
    // completion callback, right after moduleStatus is set to
    // EVALUATED/ERRORED). Notifies whoever registered against THIS SPECIFIC
    // module - correct even for a genuine cycle, since linkModule()'s own
    // dep.cycleRoot redirect (spec step 11.c.iv.1) already guarantees
    // asyncParentModules is only ever populated on a cycle's ROOT, never a
    // non-root member (whose own asyncParentModules stays permanently
    // null/empty, making this call a harmless no-op when a non-root member
    // is the one that just finished - test262
    // pending-async-dep-from-cycle.js exercises exactly this, an external
    // module reaching a cycle via a non-root member).
    private void notifyAsyncParents() {
    	if(asyncParentModules==null) {
    		return;
    	}
    	java.util.List<JSInterpretedUnit> parents = asyncParentModules;
    	asyncParentModules = null;
    	boolean errored = moduleStatus==ModuleStatus.ERRORED;
    	for(JSInterpretedUnit parent: parents) {
    		parent.pendingAsyncDependencies--;
    		if(errored) {
    			// Spec AsyncModuleExecutionRejected: propagate the SAME
    			// error to every waiting ancestor WITHOUT ever running its
    			// own body at all.
    			if(parent.moduleStatus!=ModuleStatus.ERRORED) {
    				parent.moduleStatus = ModuleStatus.ERRORED;
    				parent.evaluationError = evaluationError;
    				if(parent.evaluationCompletionCallbacks!=null) {
    					java.util.List<Runnable> callbacks = parent.evaluationCompletionCallbacks;
    					parent.evaluationCompletionCallbacks = null;
    					for(Runnable cb: callbacks) {
    						cb.run();
    					}
    				}
    				parent.notifyAsyncParents();
    			}
    			continue;
    		}
    		if(parent.pendingAsyncDependencies==0 && parent.moduleStatus==ModuleStatus.EVALUATING) {
    			JSInterpretedRuntimeContext parentContext = parent.executionContext;
    			parentContext.with( () -> parent.runBody(parentContext) );
    		}
    	}
    }
    public Object executeForEval(JSInterpretedRuntimeContext evalContext) {
    	// This one should not eval in it own loop!
    	if(evalContext instanceof org.monflabs.galtajs.rt.JSEvalRuntimeContext eec) {
    		eec.setForceStrictMode(isForceStrictMode());
    	}
    	return evalContext.with( () -> {
    		if(program.isAsyncExecution()) {
    			throw RuntimeUtil.syntaxError("await is only valid in async functions and the top level bodies of modules");
    		}
	    	JSGlobalContext gc = evalContext.getGlobalContext();
	    	JSScriptUnit oldScriptUnit = gc.getScriptUnit();
	    	boolean oldEval = gc.isEvalExecution();
	    	try {
	    		gc.setScriptUnit(this); // we keep this for later access when evaluating properties
	    		gc.setEvalExecution(true);
		    	//evalContext.getSourceCode().put(getFileName(),this);
				JSResult result = new JSResult();
		    	return program.evaluateValue(evalContext,result);
	    	} finally {
	    		gc.setScriptUnit(oldScriptUnit);
	    		gc.setEvalExecution(oldEval);
	    	}
    	});
    }

    public boolean isConstant() {
		JSOptimizerContext context = new JSOptimizerContext.MainOptimizerContext(getEnvironment(),ScriptOptimizer.emptyOptimizer());
    	return program.isConstant(context);
    }


    /////////////////////////////////////////////////////////////////////////////
    // Compiled code access
    /////////////////////////////////////////////////////////////////////////////

    public ASTProgram getProgram() {
        return program;
    }


    /////////////////////////////////////////////////////////////////////////////
    // Module support
    /////////////////////////////////////////////////////////////////////////////

	//public abstract JSModule initModule(JSUnitContext context, String name);

	// `import defer * as ns from '...'` (import-defer proposal) - see
	// InterpretedGlobalRuntimeContext.importDeferredNamespace()'s own doc
	// comment. A deferred import parses/caches this unit WITHOUT calling
	// initModule() until the FIRST real access on its deferred namespace
	// object - this EXPLICIT flag (set ONLY by importDeferredNamespace(),
	// cleared once initModule() actually runs) is what lets a later
	// caller (that namespace object's own delegate, or an ordinary,
	// non-deferred import of the same path finding this same cached
	// instance) know "has this module's body actually run yet".
	//
	// Deliberately NOT inferred from moduleContext==null (which was tried
	// first and caused a real regression): a ROOT module (run directly via
	// executeThis()/executeWithContext(), never through initModule() at
	// all - see moduleContext's own field doc comment) has moduleContext
	// permanently null too, even while it's the CURRENTLY EXECUTING
	// module - a self-import's own hoist-time cache lookup would
	// wrongly see "not yet evaluated" and call initModule() on itself a
	// SECOND time, re-running its entire body from scratch mid-evaluation
	// (confirmed via instn-named-bndng-var.js: a self-import's `y` ended
	// up already 23 instead of the expected pre-evaluation `undefined`).
	private boolean pendingDeferredEvaluation;
	public void markPendingDeferredEvaluation() {
		this.pendingDeferredEvaluation = true;
	}
	public boolean isPendingDeferredEvaluation() {
		return pendingDeferredEvaluation;
	}

	// Spec 16.2.1.6's own Cyclic Module Record [[Status]] - a NARROWER
	// slice than the full spec state machine (no separate linking/linked
	// states - GaltaJS has no real link phase distinct from evaluate, see
	// KnownGaps.md), just enough to implement ReadyForSyncExecution/
	// EnsureDeferredNamespaceEvaluation (spec 10.4.6.8/10.4.6.14) for
	// `import defer`'s own "accessing a deferred namespace of a module
	// that's CURRENTLY mid-evaluation (self, or reached via a synchronous
	// re-entrant chain) must throw TypeError, not deadlock or silently
	// re-run it" requirement, and for "a module whose evaluation already
	// FAILED must re-throw that SAME error on every later access, never
	// re-attempt evaluation" (test262 import-defer/errors/module-throws/*.js).
	// Gated to isModule() at every write site below - a plain script/eval
	// execution never touches this at all.
	public enum ModuleStatus { UNLINKED, EVALUATING, EVALUATED, ERRORED }
	private ModuleStatus moduleStatus = ModuleStatus.UNLINKED;
	// The JS-VISIBLE error value (matching what a `catch(e)` in the
	// importING module would see) from this module's own evaluation, set
	// only when moduleStatus==ERRORED - re-thrown verbatim (same object
	// identity, not a fresh wrap) on every later access attempt.
	private Object evaluationError;
	public ModuleStatus getModuleStatus() {
		return moduleStatus;
	}
	public Object getEvaluationError() {
		return evaluationError;
	}

	// Spec ReadyForSyncExecution (10.4.6.14), narrowed to what GaltaJS
	// can actually answer: recursively walks this module's own STATIC
	// dependency list (ASTProgram.getAllModuleRequests() - already built
	// for eval-rqstd-order.js's own hoisting, see its own doc comment)
	// rather than a true spec [[RequestedModules]] list (GaltaJS doesn't
	// materialize one separately). `seen` breaks cycles exactly like
	// spec's own `seen` parameter.
	public boolean readyForSyncExecution(java.util.Set<JSInterpretedUnit> seen, JSGlobalContext globalContext) {
		if(!seen.add(this)) {
			return true;
		}
		// IsModuleSCCEvaluated(this): see getCycleRootOrSelf()'s own doc
		// comment - mirrors linkModule()'s identical dep.cycleRoot redirect
		// (spec step 11.c.iv) and importModule()'s own cycle-root-error
		// redirect. A cycle member whose OWN body already finished
		// (moduleStatus==EVALUATED) can still have an un-finished cycle
		// ROOT (still suspended asynchronously elsewhere in the same SCC) -
		// checking the member's own status instead of the root's let a
		// forced-sync-evaluation trigger (e.g. accessing an `import defer`
		// namespace) run too early (test262 async-cycle-dependency-of-
		// deferred-module.js).
		JSInterpretedUnit sccTarget = getCycleRootOrSelf();
		if(sccTarget.moduleStatus==ModuleStatus.EVALUATED || moduleStatus==ModuleStatus.ERRORED) {
			// ERRORED counts as "ready" here (not spec-literal - spec has
			// no separate errored status, an errored module still ends up
			// [[Status]]==evaluated with a stored [[EvaluationError]]) -
			// EnsureDeferredNamespaceEvaluation's own caller re-throws the
			// stored error either way, so treating it as "ready" (rather
			// than blocking with an unrelated TypeError) is what actually
			// lets the ORIGINAL error surface. Checked against THIS
			// module's own status (not sccTarget's) - it can genuinely
			// error independently of its cycle root.
			return true;
		}
		// GaltaJS has no separate EVALUATING-ASYNC status value (see
		// ModuleStatus's own doc comment) - a module suspended mid-body on
		// its own `await` and one genuinely still executing synchronously
		// both show EVALUATING. `sccTarget.asyncEvaluation` (cleared to
		// false the instant a module's body genuinely completes - see the
		// completion callback in executeWithContext()) is the correct
		// signal for "this SCC's root hasn't genuinely settled yet",
		// covering the suspended-mid-await case the raw EVALUATING check
		// alone would miss for a non-root member whose OWN body already
		// returned control (e.g. B above, once its synchronous body has
		// run to completion but its cycle root A is still pending).
		if(moduleStatus==ModuleStatus.EVALUATING || sccTarget.asyncEvaluation) {
			return false;
		}
		if(program.isAsyncExecution()) {
			// [[HasTLA]] - GaltaJS has no separate synchronous-completion
			// tracking for an async module's OWN body (see this document's
			// own top-level-await entries), so conservatively treat any
			// async-executing module as never sync-ready, matching spec's
			// unconditional `return false` for [[HasTLA]]==true.
			return false;
		}
		for(String req: program.getAllModuleRequests()) {
			if(!requestedModuleReady(req, seen, globalContext)) {
				return false;
			}
		}
		return true;
	}
	private boolean requestedModuleReady(String moduleRequest, java.util.Set<JSInterpretedUnit> seen, JSGlobalContext globalContext) {
		if(executionContext!=null) {
			JSModule m = resolveModuleForRequest(moduleRequest);
			if(m instanceof JSInterpretedUnit unit) {
				return unit.readyForSyncExecution(seen, globalContext);
			}
			if(m instanceof org.monflabs.galtajs.rt.transpiler.JSTranspiledUnit tu) {
				// A transpiled module has real evaluation phases too (see
				// JSTranspiledUnit.ModuleEvalStatus's own doc comment) -
				// NOT the same as a genuinely instant-construction native/
				// JSON/text module (the case the fallback below is actually
				// for), so it needs the same readiness delegation as an
				// interpreted dependency, just via its own (narrower)
				// mechanism.
				return tu.readyForSyncExecution(new java.util.HashSet<>(), globalContext);
			}
			// Not loaded at all yet, or a genuinely non-interpreted (native/
			// JSON/text) module - the latter is always synchronously "ready"
			// (already fully constructed the instant it's cached, no
			// separate evaluation phase); the former genuinely isn't ready
			// (hasn't even started).
			return m!=null;
		}
		// This module (this) hasn't started executing yet, so resolving
		// its own requests the NORMAL way (RuntimeUtil.importModule, via
		// the executionContext!=null branch above) isn't available - and
		// deliberately NOT worked around by resolving through some OTHER
		// live context instead: that was tried and reverted, because
		// importModule() doesn't just resolve a specifier, it LOADS (and,
		// for a never-before-seen path, immediately EVALUATES) whatever it
		// finds - turning a pure readiness CHECK into a side-effecting
		// eager evaluation of a dependency nobody asked for yet (confirmed
		// via a regression: get-other-while-dep-evaluating.js's dep-3 got
		// evaluated by the mere act of checking whether dep-2 was ready).
		// Fall back to a pure, non-loading cache peek instead: a
		// dependency nobody has touched yet can't be mid-evaluation or
		// part of a cycle (nothing has ever reached it to start either),
		// so optimistically treat it as ready - this only under-detects
		// an unresolvable-in-practice case (a fresh, never-touched,
		// transitively-async dependency several hops deep), not the
		// self-reference/currently-evaluating-sibling patterns test262
		// actually exercises here, both of which involve an ALREADY
		// cached module (this module's own registration, or a root
		// module's registerRootModule()).
		JSModule m = globalContext.peekModule(this, moduleRequest);
		if(m instanceof JSInterpretedUnit unit) {
			return unit.readyForSyncExecution(seen, globalContext);
		}
		if(m instanceof org.monflabs.galtajs.rt.transpiler.JSTranspiledUnit tu) {
			// See the identical dispatch/doc comment in the executionContext!=null
			// branch above - a transpiled module needs the same readiness
			// delegation as an interpreted one, not the "never touched,
			// optimistically ready" treatment below.
			return tu.readyForSyncExecution(new java.util.HashSet<>(), globalContext);
		}
		return true;
	}

	@Override
	public JSModule initModule(JSGlobalContext globalContext, boolean commonJS) {
		if(moduleContext!=null) {
			throw RuntimeUtil.error("Module {0} is already initialized",getDescriptor().getName());
		}
		this.pendingDeferredEvaluation = false;
		this.moduleContext = new ModuleRuntimeContext(globalContext,this);
		if(commonJS) {
			executeWithContext(moduleContext);
			Object module = moduleContext.getVariableValue(ModuleUtil.MODULE,null);
			if(module instanceof JSObject jm) {
				Object exports =jm.getOwnProperty(ModuleUtil.EXPORTS);
				if(RuntimeUtil.isNotNullOrUndefined(exports)) {
					if(exports instanceof JSObject jo) {
						setNamedExports(jo);
					}
					setDefaultExport(exports);
				}
			}
		} else {
			executeWithContext(moduleContext);
		}
		// We remove the global context as this was ephemeral during the module initialization
		// We remove it afterwards to avoid a memory leak...
		//moduleContext.clearGlobalContext();
		return this;
    }
	private static class ModuleRuntimeContext extends InterpretedModuleRuntimeContext {
		private ModuleRuntimeContext(JSGlobalContext globalContext, JSInterpretedUnit module) {
			super(globalContext.getEnvironment(),module);
			// This is the variable defined in AbstractContext
			this.globalContext =globalContext;
		}
	}

	@Override
	public JSModuleContext getModuleContext() {
		if(moduleContext==null) {
			throw RuntimeUtil.error("Module {0} is not yet initialized",getDescriptor().getName());
		}
		return moduleContext;
	}

	// A local `export var/let/const x`, `export function x(){}`, `export
	// class X{}`, or unaliased `export {x}` all declare/reuse a module-
	// top-level variable named EXACTLY `name` - by the time ANY statement
	// in this module runs (even a hoisted-first import - see ASTImport's
	// own HoistableNode status), ASTProgram's own var/let/const/function
	// hoisting pass has ALREADY created that variable's real VarAccessor
	// cell (its VALUE may still be unset/undefined at this point, but the
	// CELL itself - what a live binding needs to alias - already exists).
	// So this can resolve a live accessor directly from the module's own
	// scope, by name, with no separate export-time registration needed for
	// this (dominant) case. Falls back to the interface default (a
	// read-only value snapshot via getExport()) for anything NOT backed by
	// a same-named local variable - an aliased `export {a as b}`, a
	// re-export (`export {x} from '...'`/`export * from '...'`), or
	// `export default` (bundled with a separate, not-yet-implemented
	// hoisting fix - see KnownGaps.md) - preserving today's existing
	// (non-live, but correct) behavior for those forms rather than
	// guessing.
	@Override
	public VarAccessor getExportAccessor(String name) {
		JSModule.ResolvedBinding rb = resolveExport(name, new java.util.HashSet<>());
		if(rb!=null) {
			return rb.accessor();
		}
		return super.getExportAccessor(name);
	}

	// Spec 16.2.1.6.3 ResolveExport, for the interpreted module kind - see
	// JSModule.resolveExport()'s own doc comment for the general contract.
	// Order matches spec exactly: local export entries first (unlike the
	// OLD dynamic starExportSources/namedReExportSources maps in
	// AbstractModule, ASTProgram's static lists here mean this works
	// EVEN IF this module's own body hasn't started running yet, or is
	// mid-run and hasn't reached the relevant `export` statement - the
	// value/cell may still be TDZ-unset, but the STATIC shape (what's
	// exported, and from where) never depends on execution order), then
	// indirect (named-`from`) entries, then star entries - with
	// resolveSet as the cycle guard (spec step 3/8b: revisiting the same
	// (module, exportName) pair along one path means "no resolution
	// through here", not an infinite recursion or a StackOverflow).
	@Override
	public JSModule.ResolvedBinding resolveExport(String name, java.util.Set<JSModule.ResolveKey> resolveSet) {
		// Spec step 3: register (this, name) BEFORE anything else below,
		// including the local-entry check - so a genuinely cyclic lookup
		// that comes back around to the exact same (module, name) (however
		// it gets there: local-that's-really-an-import, indirect, or star)
		// is caught uniformly.
		JSModule.ResolveKey key = new JSModule.ResolveKey(this, name);
		if(!resolveSet.add(key)) {
			// Already resolving (this, name) along this same walk - a
			// genuine cycle with no local binding anywhere to terminate
			// it. Spec: no resolution through this path (not an error by
			// itself - only a top-level import of a name that resolves to
			// nothing/ambiguous anywhere is).
			return null;
		}
		if(executionContext!=null) {
			// containsKey(), NOT get()!=null: an anonymous `export default
			// function(){}`/`function*(){}`'s OWN local variable name
			// (ASTFunctionDecl.getFunctionName()) is genuinely Java null
			// (never populated to "" anywhere - see ASTFunctionDecl.
			// createFunction()/evaluate(), which already use this same raw
			// null as the REAL variable's own key elsewhere) - so
			// getLiveExportNames() legitimately maps "default" -> null for
			// that case, which get()!=null alone can't distinguish from
			// "no local mapping for this name at all".
			boolean isLocal = program.getLiveExportNames().containsKey(name);
			String localName = isLocal ? program.getLiveExportNames().get(name) : null;
			if(!isLocal) {
				// A self-referencing `export {x as y} from './this-file.js'`
				// is ALSO reachable below via the indirect-entries loop
				// recursing back into this SAME module instance (resolved
				// via resolveModuleForRequest()) - this earlier, direct
				// check is kept only as a cheap short-circuit / fallback
				// for any case that resolveModuleForRequest() itself can't
				// reach (e.g. before this module is registered in the
				// loader's cache under its own name).
				localName = program.getSelfReexportLocalName(name, getDescriptor().getName());
				isLocal = localName!=null;
			}
			if(isLocal) {
				// See ASTProgram.getImportedLocalNames()'s own doc comment:
				// `import {a} from mod; export {a};` isn't a local export
				// at all per spec - it's an indirect one tracing back to
				// mod's own "a", needed for star-ambiguity identity to be
				// correct even though the VALUE is identical either way.
				ASTProgram.IndirectExportEntry importedFrom = program.getImportedLocalNames().get(localName);
				if(importedFrom==null) {
					VarAccessor a = executionContext.getLocalVariableEntry(localName);
					if(a!=null) {
						return new JSModule.ResolvedBinding(this, localName, a);
					}
				} else {
					JSModule source = resolveModuleForRequest(importedFrom.moduleRequest());
					if(source==null) {
						return null;
					}
					if(ASTProgram.SOURCE_IMPORT_NAME.equals(importedFrom.importName())) {
						return resolveSourceBinding(name, importedFrom.moduleRequest());
					}
					if(ASTProgram.NAMESPACE_IMPORT_NAME.equals(importedFrom.importName())) {
						// `import * as x from '...'; export {x};` - see
						// ASTProgram.NAMESPACE_IMPORT_NAME's own doc
						// comment; same treatment as `export * as ns from`.
						return new JSModule.ResolvedBinding(source, ASTProgram.NAMESPACE_IMPORT_NAME,
								VarAccessor.ofStatic(name, source.getModuleNamespaceObject()));
					}
					return source.resolveExport(importedFrom.importName(), resolveSet);
				}
			}
		}
		// "default" is never a genuine LocalExportEntry in the general
		// named-export sense - getLiveExportNames() only maps it for the
		// HOISTABLE function/generator/async form (already handled, and
		// returned, above). A NON-hoistable default (`export default
		// <expr>;`/`export default class C{}`) is tracked separately via
		// setDefaultExport()/hasDefaultExport()/getDefaultExport() - this
		// fallback lets a THIRD module re-exporting `default` from this
		// one (`export {default} from '...'`/`export {x as default} from
		// '...'`) resolve it via the ordinary indirect-entry recursion
		// below, exactly like any other name. Only reached once the
		// hoistable-live-accessor path above has already had its chance
		// (and found nothing) - a hoistable default that HAS started
		// running keeps getting the live accessor, not this static
		// snapshot, preserving reassignment visibility (test262
		// eval-gtbndng-indirect-update-dflt.js).
		if("default".equals(name) && hasDefaultExport()) {
			return new JSModule.ResolvedBinding(this, "default", VarAccessor.ofStatic("default", getDefaultExport()));
		}
		for(ASTProgram.IndirectExportEntry ie: program.getIndirectExportEntries()) {
			if(!ie.exportName().equals(name)) {
				continue;
			}
			JSModule source = resolveModuleForRequest(ie.moduleRequest());
			if(source==null) {
				return null;
			}
			if(ASTProgram.NAMESPACE_IMPORT_NAME.equals(ie.importName())) {
				// `export * as ns from '...'` - resolves directly to the
				// SOURCE module's own namespace (spec ResolveExport step
				// 6.iii), not a recursive name lookup INTO it. bindingName
				// is the SAME sentinel regardless of which local alias/
				// intermediate module reached here, so two `export * as
				// foo from` re-exports of the SAME underlying source
				// module correctly compare as non-ambiguous (matching
				// [[Module]] AND [[BindingName]]) even when reached
				// through different intermediate re-exporting modules.
				return new JSModule.ResolvedBinding(source, ASTProgram.NAMESPACE_IMPORT_NAME,
						VarAccessor.ofStatic(name, source.getModuleNamespaceObject()));
			}
			return source.resolveExport(ie.importName(), resolveSet);
		}
		JSModule.ResolvedBinding starResult = null;
		for(ASTProgram.StarExportEntry se: program.getStarExportEntries()) {
			JSModule source = resolveModuleForRequest(se.moduleRequest());
			if(source==null) {
				continue;
			}
			JSModule.ResolvedBinding r = source.resolveExport(name, resolveSet);
			if(r==null) {
				continue;
			}
			if(starResult==null) {
				starResult = r;
			} else if(starResult.module()!=r.module() || !starResult.bindingName().equals(r.bindingName())) {
				// Two DIFFERENT star sources both resolve this name, to
				// different bindings - ambiguous (spec ResolveExport step
				// 8d.ii.2-3), same rule AbstractModule.addStarReExport()
				// already enforces for the dynamic/value-snapshot path.
				return null;
			}
		}
		return starResult;
	}

	// Spec 16.2.1.6.2 GetExportedNames - see JSModule.getExportedNames()'s
	// own doc comment for the general contract/why this exists. Local +
	// indirect entries are exactly ASTProgram.getStaticExportedNames()
	// (already excludes bare `export *` names); star entries are followed
	// transitively here, with exportStarSet (keyed by MODULE, not by
	// name+module like resolveExport()'s resolveSet) as the cycle guard -
	// a module already being visited along this walk contributes nothing
	// further (spec step 2: "If exportStarSet contains module, return a
	// new empty List").
	@Override
	public java.util.Set<String> getExportedNames(java.util.Set<JSModule> exportStarSet) {
		if(!exportStarSet.add(this)) {
			return java.util.Collections.emptySet();
		}
		java.util.LinkedHashSet<String> names = new java.util.LinkedHashSet<>(program.getStaticExportedNames());
		for(ASTProgram.StarExportEntry se: program.getStarExportEntries()) {
			JSModule source = resolveModuleForRequest(se.moduleRequest());
			if(source==null) {
				continue;
			}
			for(String n: source.getExportedNames(exportStarSet)) {
				if(!"default".equals(n)) {
					names.add(n);
				}
			}
		}
		return names;
	}

	// Resolves a module specifier (an `export ... from`/`import ... from`
	// string, relative to THIS module) to its already-loaded (or freshly
	// loaded) JSModule instance, for resolveExport()'s own recursion -
	// same call ASTExport/ASTImport already make via RuntimeUtil.
	// importModule(), so this triggers no new loading behavior; it may
	// return a module that's still mid-evaluation (this module's own
	// earlyRegister-before-run guarantee - see JSSourceModuleResolver's
	// doc comment - is exactly what makes resolving INTO a not-yet-
	// finished module safe here). Returns null (never throws) on failure,
	// matching resolveExport()'s own "not resolvable" contract - a
	// genuinely missing module surfaces instead at the ordinary import
	// statement's own non-resolveExport-based evaluate(), which already
	// throws a proper error for it.
	private JSModule resolveModuleForRequest(String moduleRequest) {
		if(executionContext==null) {
			return null;
		}
		try {
			return RuntimeUtil.importModule(executionContext, moduleRequest);
		} catch(RuntimeException ex) {
			return null;
		}
	}

	// `import source x from '...'; export {x};` - spec ResolveExport step
	// 7.a.iv: a ResolvedBinding whose [[BindingName]] is ~source~ and whose
	// [[Module]] is the TARGET module record. The target is deliberately
	// never loaded here (a source phase import must not evaluate it), so
	// there is no JSModule to put in [[Module]]: the resolved name is folded
	// into the binding name instead, with a null module - which is exactly
	// what the star-ambiguity comparison (same module, same binding name)
	// needs to treat two re-exports of the same target as one binding.
	// Null (not an exception) on failure, like resolveModuleForRequest().
	private JSModule.ResolvedBinding resolveSourceBinding(String name, String moduleRequest) {
		if(executionContext==null) {
			return null;
		}
		try {
			org.monflabs.galtajs.rt.builtins.standard.module.ModuleSource ms = RuntimeUtil.importModuleSource(executionContext, moduleRequest);
			return new JSModule.ResolvedBinding(null, ASTProgram.SOURCE_IMPORT_NAME + ms.getModuleName(), VarAccessor.ofStatic(name, ms));
		} catch(RuntimeException ex) {
			return null;
		}
	}

	// Unlike getExportAccessor("default") (which would fall through to the
	// interface default's getExport("default") - always a throw, since
	// "default" is never stored in namedExports at all), this returns null
	// (not an exception) when this module's default export isn't a
	// hoistable declaration or its binding isn't available yet - the
	// caller (ASTImport.hoistBindings()) treats null as "defer to the
	// ordinary, non-hoisted default-import path" rather than an error.
	@Override
	public VarAccessor getLiveDefaultExportAccessor() {
		if(executionContext!=null) {
			// containsKey(), not get()!=null - see resolveExport()'s own
			// identical comment: an anonymous hoistable default's local
			// name is genuinely Java null, not absent.
			if(program.getLiveExportNames().containsKey("default")) {
				String localName = program.getLiveExportNames().get("default");
				return executionContext.getLocalVariableEntry(localName);
			}
		}
		return null;
	}


    /////////////////////////////////////////////////////////////////////////////
    // Debug
    /////////////////////////////////////////////////////////////////////////////

    public void dump() {
    	dump(Console.outStream());
    }
    public void dump(PrintStream ps) {
    	program.dump(ps);
    }
}
