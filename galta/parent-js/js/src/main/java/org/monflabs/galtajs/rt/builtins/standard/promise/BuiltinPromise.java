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
package org.monflabs.galtajs.rt.builtins.standard.promise;

import org.monflabs.galtajs.rt.builtins.BuiltinUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.jdt.annotation.NonNull;
import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.node.ASTNode;
import org.monflabs.galtajs.rt.JSRuntimeContext;
import org.monflabs.galtajs.rt.JSRuntimeException;
import org.monflabs.galtajs.rt.JSRuntimeUncatchableException;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.builtins.Callable;
import org.monflabs.galtajs.rt.builtins.Constructor;
import org.monflabs.galtajs.rt.builtins.JSAccessor;
import org.monflabs.galtajs.rt.builtins.NativeObject;
import org.monflabs.galtajs.rt.builtins.errors.TypeError;
import org.monflabs.galtajs.rt.builtins.standard.function.BuiltinFunctionInterpreter;
import org.monflabs.galtajs.rt.builtins.standard.function.BuiltinFunctionNative;
import org.monflabs.galtajs.rt.builtins.standard.promise.PromiseReaction.Type;
import org.monflabs.galtajs.rt.executors.MicroTask;

/**
 * Promise object
 */
public class BuiltinPromise extends NativeObject {

	enum State {
		PENDING, FULFILLED, REJECTED
	}

	// Internal slots
	private State state = State.PENDING; // [[PromiseState]]
	private Object result; // [[PromiseResult]]
	private final List<PromiseReaction> fulfillReactions = new ArrayList<>(); // [[FulfillReactions]]
	private final List<PromiseReaction> rejectReactions = new ArrayList<>(); // [[RejectReactions]]
	private boolean isHandled; // [[IsHandled]]

	public BuiltinPromise(JSEnvironment env) {
		super(env);
	}

	@Override
	public String getClassName() {
		return BuiltinPromiseConstructor.CLASSNAME;
	}

	@Override
	protected Object getDefaultPrototype() {
		return BuiltinPromisePrototype.get(getEnvironment());
	}

	/* ====== Core resolution & rejection ====== */
	public synchronized void fulfill(Object value) {
		if (state != State.PENDING) {
			return;
		}
		this.state = State.FULFILLED;
		this.result = value;
		triggerReactions(fulfillReactions);
	}

	public synchronized void reject(Object reason) {
		if (state != State.PENDING) {
			return;
		}
		this.state = State.REJECTED;
		this.result = reason;
		triggerReactions(rejectReactions);
		// HostPromiseRejectionTracker(promise, "reject") when !isHandled: a
		// no-op, the engine has no unhandled-rejection reporting.
	}

	private void triggerReactions(List<PromiseReaction> reactions) {
		JSRuntimeContext context = JSRuntimeContext.get();
		for (PromiseReaction r : reactions) {
			context.getGlobalContext().getExecutor().queueMicrotask(new MicroTask("Promise - reaction",context,BuiltinPromise.getCallerNode(r.getHandler())) {
				@Override
				public void run() {
					performPromiseThenReactionJob(result, r);
				}
			});
		}
		reactions.clear();
	}

	private static void performPromiseThenReactionJob(Object argument, PromiseReaction reaction) {
		Object handlerResult;
		try {
			if (reaction.getHandler() == null) {
				// identity for fulfill; thrower for reject
				if (reaction.getType() == Type.FULFILL) {
					handlerResult = argument;
				} else {
					// The reason is passed through as is (not re-wrapped)
					reaction.getCapability().getReject().call(RuntimeUtil.UNDEFINED, argument);
					return;
				}
			} else {
				// Spec: Call(handler, undefined, «argument») - a raw Java
				// `null` here is NOT the same as JS `undefined`: a
				// genuinely-strict handler function must see `this ===
				// undefined` with no this-coercion, but reading `this`
				// inside it surfaced actual JS `null` instead (confirmed
				// via test262's own rxn-handler-fulfilled-invoke-strict.js:
				// "'this' must be undefined, got null").
				handlerResult = reaction.getHandler().call(RuntimeUtil.UNDEFINED, argument);
			}
			// Resolve capability with handlerResult
			reaction.getCapability().getResolve().call(RuntimeUtil.UNDEFINED, handlerResult);
        } catch(JSRuntimeUncatchableException t) {
        	throw t;
		} catch (Exception e) {
			// The handler's exception, as a JS catch clause sees it. Errors
			// (StackOverflowError, OutOfMemoryError...) are not JS exceptions
			// and propagate, as they do through a JS try/catch.
			reaction.getCapability().getReject().call(RuntimeUtil.UNDEFINED, JSRuntimeException.exceptionObject(e));
		}
	}

	private static JSRuntimeException asThrowable(Object reason) {
		if(reason instanceof Throwable t) {
			return RuntimeUtil.wrap(t);
		}
		return JSRuntimeException.asJavascriptException(null, reason);
	}

	static PromiseCapability newPromiseCapability(JSEnvironment env, Constructor ctor) {
	    // IsConstructor(C) is false -> throw. `ctor` is null when the caller's
	    // `this` was an object but not a constructor (see BuiltinPromiseConstructor).
	    if (ctor == null) {
	        throw RuntimeUtil.typeError("Promise constructor must be a constructor function");
	    }

	    // Prepare the capability record
		final PromiseCapability cap = new PromiseCapability();

		// GetCapabilitiesExecutor Functions: captures whatever RAW values were
		// passed (no callable check here) - throws if called a second time
		// with EITHER slot already non-undefined (matching spec's exact
		// per-slot "is not undefined" check, not a simple "already called"
		// flag - test262's capability-executor-called-twice.js exercises the
		// asymmetric case where only one slot was set on the first call).
		// Callability is only verified afterward, once construction completes.
		final Object[] rawResolve = { RuntimeUtil.UNDEFINED };
		final Object[] rawReject = { RuntimeUtil.UNDEFINED };
		// GetCapabilitiesExecutor is spec'd as a real, anonymous, non-
		// constructor built-in function object (length 2) - not a raw Java
		// lambda - so a caller that captures it (e.g. Promise.resolve.call
		// (NotPromiseConstructor)) observes correct length/name/prototype/
		// extensibility.
		Callable executor = new BuiltinFunctionNative(env, "", 2) {
			@Override
			public Object call(Object thisArg, Object[] args, Constructor newTarget) {
				Object resolveArg = args.length > 0 ? args[0] : RuntimeUtil.UNDEFINED;
				Object rejectArg = args.length > 1 ? args[1] : RuntimeUtil.UNDEFINED;
				if (rawResolve[0] != RuntimeUtil.UNDEFINED) {
					throw RuntimeUtil.typeError("Promise resolve function is already set");
				}
				if (rawReject[0] != RuntimeUtil.UNDEFINED) {
					throw RuntimeUtil.typeError("Promise reject function is already set");
				}
				rawResolve[0] = resolveArg;
				rawReject[0] = rejectArg;
				return RuntimeUtil.UNDEFINED;
			}
		};

		// Construct the new promise by calling the constructor with the
		// executor. `C` may be any constructor (e.g. via Promise.all.call(C,...)
		// with a non-Promise C) - the result need not be a real BuiltinPromise.
		Object p = ctor.constructObject(new Object[] {executor}, ctor);
		cap.setPromise(p);

	    // --- Verify resolve/reject are callable ---
	    if (!(rawResolve[0] instanceof Callable resolveFn && resolveFn.isCallable()) || !(rawReject[0] instanceof Callable rejectFn && rejectFn.isCallable())) {
	        throw RuntimeUtil.typeError("Promise constructor did not provide callable resolve/reject functions");
	    }
	    cap.setResolve(resolveFn);
	    cap.setReject(rejectFn);

		return cap;
	}

    // Spec: Promise.prototype.finally is fully GENERIC - "Return ? Invoke
    // (promise, 'then', « thenFinally, catchFinally »)" - it works on ANY
    // object with a "then" property, not just a genuine BuiltinPromise (and
    // must respect an own "then" override even ON a genuine BuiltinPromise
    // instance - test262's invokes-then-with-function.js). thenFinally/
    // catchFinally are real, anonymous, non-constructor built-in function
    // objects (length 1), not raw Java lambdas.
    public static Object finally_(JSEnvironment env, Object promise, Object onFinallyArg) {
        // Spec step 3: SpeciesConstructor(promise, %Promise%) is resolved
        // ONCE here, at the top - thenFinally/catchFinally below both close
        // over this SAME `C`, using it for their own PromiseResolve(C, ...)
        // step, never the hardcoded default %Promise% (confirmed via
        // test262's own species-constructor.js, which asserts the exact
        // NUMBER of `new C(...)` calls a `.finally()` chain makes - using
        // the wrong constructor silently skips invoking a subclass's own
        // constructor for these intermediate promises).
        Constructor defaultCtor = env.getStandardObjects().getConstructor(BuiltinPromiseConstructor.CLASSNAME);
        Constructor speciesCtor = RuntimeUtil.speciesConstructor(env, promise, defaultCtor);
        Object thenFinally;
        Object catchFinally;
        if (onFinallyArg instanceof Callable onFinally && onFinally.isCallable()) {
            thenFinally = new FinallyCallable(env, onFinally) {
                @Override
                public Object call(Object thisArg, Object[] args, Constructor newTarget) {
                    Object v = args.length > 0 ? args[0] : RuntimeUtil.UNDEFINED;
                    Object res = onFinally.call(RuntimeUtil.UNDEFINED);
                    // Spec: PromiseResolve(C, result) unconditionally - even
                    // when `result` is a plain, non-promise value (the
                    // common case for a `() => {}`-style onFinally) - NOT
                    // only when it happens to already be a BuiltinPromise.
                    // Skipping this for the non-promise case previously
                    // dropped BOTH the PromiseResolve's own `new C(...)` and
                    // the subsequent `.then()`'s own `new C(...)`.
                    Object resolved = BuiltinPromiseConstructor.resolve(getEnvironment(), speciesCtor, res);
                    // Spec: valueThunk is CreateBuiltinFunction(_, 0, "", «»)
                    // - a genuine, zero-argument, empty-named JS function
                    // object, not a raw Java lambda (which the property
                    // accessor doesn't recognize as a JS value at all -
                    // confirmed via test262's own *-observable-then-calls-
                    // argument.js, which inspects the argument's own
                    // .length/.name). Only ONE argument is passed to this
                    // inner .then() call (Invoke(promise,"then",«
                    // valueThunk»)) - a rejection here propagates naturally,
                    // no onRejected handler needed.
                    Callable valueThunk = new BuiltinFunctionNative(getEnvironment(), "", 0) {
                        @Override
                        public Object call(Object t, Object[] a2, Constructor nt) {
                            return v;
                        }
                    };
                    return invokeThen(getEnvironment(), resolved, valueThunk);
                }
            };
            catchFinally = new FinallyCallable(env, onFinally) {
                @Override
                public Object call(Object _this, Object[] args, Constructor newTarget) {
                    Object reason = args.length > 0 ? args[0] : RuntimeUtil.UNDEFINED;
                    Object res = onFinally.call(RuntimeUtil.UNDEFINED);
                    Object resolved = BuiltinPromiseConstructor.resolve(getEnvironment(), speciesCtor, res);
                    Callable thrower = new BuiltinFunctionNative(getEnvironment(), "", 0) {
                        @Override
                        public Object call(Object t, Object[] a2, Constructor nt) {
                            throw asThrowable(reason);
                        }
                    };
                    return invokeThen(getEnvironment(), resolved, thrower);
                }
            };
        } else {
            // Per spec: a non-callable onFinally passes through UNCHANGED as
            // both `then` arguments - not coerced to null/undefined.
            thenFinally = onFinallyArg;
            catchFinally = onFinallyArg;
        }
        Object thenFn = env.getAccessor(promise).getProperty(promise, "then", RuntimeUtil.UNDEFINED);
        if (!(thenFn instanceof Callable c && c.isCallable())) {
            throw RuntimeUtil.typeError("then is not a function");
        }
        return c.call(promise, new Object[] { thenFinally, catchFinally });
    }

    // Invokes `.then` as a plain PROPERTY on `promiseLike` (which may be any
    // "thenable" the species constructor produced, not necessarily a real
    // BuiltinPromise) - same pattern finally_() itself already uses for its
    // own top-level `.then()` call.
    private static Object invokeThen(JSEnvironment env, Object promiseLike, Callable valueThunk) {
        Object thenFn = env.getAccessor(promiseLike).getProperty(promiseLike, "then", RuntimeUtil.UNDEFINED);
        if (!(thenFn instanceof Callable c && c.isCallable())) {
            throw RuntimeUtil.typeError("then is not a function");
        }
        return c.call(promiseLike, new Object[] { valueThunk });
    }

	// Per spec, Promise.prototype.then's return value is resultCapability.
	// [[Promise]] - whatever `new C(executor)` produced for the (possibly
	// subclassed/species-overridden) constructor C, which need NOT be a
	// genuine BuiltinPromise instance at all (e.g. a subclass constructor
	// can `return {}`). See test262
	// built-ins/Promise/prototype/then/capability-executor-called-twice.js.
	public Object then_(Callable onFulfilled, Callable onRejected) {
		Constructor constructor = RuntimeUtil.speciesConstructor(getEnvironment(),this,getEnvironment().getStandardObjects().getConstructor(BuiltinPromiseConstructor.CLASSNAME));
		return registerReactions(constructor, onFulfilled, onRejected);
	}

	// Spec Await (6.2.3.1)/AsyncGeneratorAwaitReturn/etc.'s own
	// PerformPromiseThen(promise, onFulfilled, onRejected) - INTERNAL
	// engine reaction registration, deliberately NOT the same as
	// Promise.prototype.then/then_() above: spec's internal PerformPromiseThen
	// is called with resultCapability UNDEFINED, meaning NO SpeciesConstructor
	// lookup happens at all (that's exclusively a Promise.prototype.then
	// step - see its own spec text). Every internal driver call site that
	// merely wants to be notified once `this` settles (a coroutine's own
	// await resumption, an async generator's AwaitYieldSignal handling,
	// import.defer's own dependency-completion waiting, ...) must use
	// THIS method, not then_() - using then_() there was a real,
	// observable bug: it triggered a spurious `constructor` property read
	// on `this` (SpeciesConstructor's own "Let C be ? Get(O, 'constructor')"
	// step) on EVERY internal await, anywhere in the engine, which spec
	// never does for these internal purposes (test262 language/statements/
	// for-await-of/ticks-with-async-iter-resolved-promise-and-constructor-
	// lookup{,-two}.js fail specifically because of this extra, unwanted
	// read - two SIBLING for-await-of files going through the sync-
	// iterator-wrapping path stay broken even after this fix, for a
	// separate, genuine architectural gap - see KnownGaps.md). Still
	// allocates a real (internal-only,
	// never JS-visible) throwaway capability via the PLAIN, non-species
	// %Promise% constructor - registerReactions()'s own dispatch/
	// scheduling logic is shared, unchanged, and well-tested; only the
	// SPECIES LOOKUP step is skipped, matching spec's own distinction
	// exactly.
	public void performPromiseThen_(Callable onFulfilled, Callable onRejected) {
		registerReactions(getEnvironment().getStandardObjects().getConstructor(BuiltinPromiseConstructor.CLASSNAME), onFulfilled, onRejected);
	}

	private Object registerReactions(Constructor constructor, Callable onFulfilled, Callable onRejected) {
		PromiseCapability capability = newPromiseCapability(getEnvironment(),constructor);

		Callable onFul = BuiltinUtil.asCallable(onFulfilled);
		Callable onRej = BuiltinUtil.asCallable(onRejected);

		// PerformPromiseThen: every then() marks the promise as handled, with or
		// without a rejection handler (the derived promise carries the rejection)
		this.isHandled = true;

		PromiseReaction fulfillReaction = new PromiseReaction(Type.FULFILL, capability, onFul);
		PromiseReaction rejectReaction = new PromiseReaction(Type.REJECT, capability, onRej);

		// Must be atomic with fulfill()/reject(): otherwise a reaction registered here
		// while another thread is concurrently settling the promise can land in a
		// PENDING-state reactions list just after triggerReactions() already iterated
		// and cleared it - the reaction is then silently dropped and never runs (a
		// waiter parked in JSAsyncExecutor.await() would then hang forever).
		synchronized(this) {
			switch (getState()) {
				case PENDING -> {
					fulfillReactions.add(fulfillReaction);
					rejectReactions.add(rejectReaction);
				}
				case FULFILLED -> {
					JSRuntimeContext ctx = JSRuntimeContext.get();
					ctx.getGlobalContext().getExecutor().queueMicrotask(new MicroTask("Promise - Fulfilled",ctx,BuiltinPromise.getCallerNode(fulfillReaction.getHandler())) {
						@Override
						public void run() {
							performPromiseThenReactionJob(result, fulfillReaction);
						}
					});
				}
				case REJECTED -> {
					JSRuntimeContext ctx = JSRuntimeContext.get();
					ctx.getGlobalContext().getExecutor().queueMicrotask(new MicroTask("Promise - Rejected",ctx,BuiltinPromise.getCallerNode(rejectReaction.getHandler())) {
						@Override
						public void run() {
							performPromiseThenReactionJob(result, rejectReaction);
						}
					});
				}
			}
		}

		return capability.getPromise();
	}

	private static abstract class FinallyCallable extends BuiltinFunctionNative {
		private Callable callable;
		private FinallyCallable(JSEnvironment env, Callable callable) {
			super(env, "", 1);
			this.callable = callable;
		}
	}
	private static final ASTNode getCallerNode(Callable callable) {
		if(callable instanceof FinallyCallable fc) {
			callable = fc.callable;
		}
		if(callable instanceof BuiltinFunctionInterpreter fi) {
			return fi.getFunctionNode();
		}
		return null;
	}

	public State getState() {
		return state;
	}

	public Object getResult() {
		return result;
	}

	public boolean isHandled() {
		return isHandled;
	}

	public void resolvePromise(Object resolution) {
		BuiltinPromise _this = this;

		// 1. If resolution is the same promise -> TypeError
		if (_this == resolution) {
			_this.reject(new TypeError(getEnvironment(),"Chaining cycle detected for promise"));
			return;
		}

		// 2. Thenable check: If object with callable "then". Deliberately no
		// special-cased fast path for `resolution instanceof BuiltinPromise`
		// here (there used to be one, calling p2.then_() directly as a Java
		// method) - that bypassed the "then" PROPERTY entirely, silently
		// ignoring an overridden Promise.prototype.then or an instance's own
		// .then override (test262's own resolve-prms-cstm-then*.js family:
		// "Resolving with a resolved Promise instance whose `then` method
		// has been overridden"). Per spec (Promise Resolve Functions step
		// 8/12), ANY promise resolution value - not just non-Promise
		// thenables - goes through this same GetMethod(resolution,"then")
		// + deferred PromiseResolveThenableJob path.
		if (RuntimeUtil.isObject(getEnvironment(), resolution)) {
	        Object thenValue;
			try {
				// The getter can fail...
		        JSAccessor a = getEnvironment().getAccessor(resolution);
		        thenValue = a.getProperty(resolution, "then", null);
	        } catch (Exception ex) {
	        	RuntimeUtil.rethrowIfUncatchable(ex);
	            _this.reject(JSRuntimeException.exceptionObject(ex));
	            return;
	        }

	        if (!(thenValue instanceof Callable then && then.isCallable())) {
	            _this.fulfill(resolution);
	            return;
	        }

			// Per spec: thenable resolution must be done asynchronously as a job (microtask)
			JSRuntimeContext context = JSRuntimeContext.get();
			context.getGlobalContext().getExecutor().queueMicrotask(new MicroTask("Promise - Thenable resolution", context, null) {
				@Override
				public void run() {
					AtomicBoolean called = new AtomicBoolean(false);
					try {
						// Spec: these are genuine CreateBuiltinFunction(_, 1, "", «»)
						// objects (Promise Resolve/Reject Functions), not raw Java
						// Callables - a resolution whose (possibly overridden)
						// "then" inspects .length/.name/isConstructor() on its
						// arguments (test262's create-resolving-functions-
						// {resolve,reject}.js) needs a real JS-visible function
						// object here, same as newPromiseCapability()'s executor.
						Callable resolveFn = new BuiltinFunctionNative(getEnvironment(), "", 1) { // resolve
							@Override
							public Object call(Object thisArg, Object[] args, Constructor newTarget) {
								if (called.getAndSet(true)) {
									return RuntimeUtil.UNDEFINED;
								}
								Object y = args.length > 0 ? args[0] : RuntimeUtil.UNDEFINED;
								_this.resolvePromise(y);
								return RuntimeUtil.UNDEFINED;
							}
						};
						Callable rejectFn = new BuiltinFunctionNative(getEnvironment(), "", 1) { // reject
							@Override
							public Object call(Object thisArg, Object[] args, Constructor newTarget) {
								if (called.getAndSet(true)) {
									return RuntimeUtil.UNDEFINED;
								}
								Object r = args.length > 0 ? args[0] : RuntimeUtil.UNDEFINED;
								_this.reject(r);
								return RuntimeUtil.UNDEFINED;
							}
						};
						then.call(resolution, resolveFn, rejectFn);
					} catch(JSRuntimeUncatchableException t) {
						throw t;
					} catch (Exception t) {
						if (!called.get()) {
							_this.reject(JSRuntimeException.exceptionObject(t));
						}
					}
				}
			});
			return;
		}

		// 4. Non-thenable
		_this.fulfill(resolution);
	}
}
