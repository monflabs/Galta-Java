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
package org.monflabs.galtajs.rt.builtins.standard.generator;

import java.util.NoSuchElementException;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.jsonfactory.JSObject;
import org.monflabs.galtajs.rt.AwaitYieldSignal;
import org.monflabs.galtajs.rt.JSRuntimeException;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.YieldStarDelegateResult;
import org.monflabs.galtajs.rt.builtins.BaseMethod;
import org.monflabs.galtajs.rt.builtins.BasePrototype;
import org.monflabs.galtajs.rt.builtins.PropertyDescriptor;
import org.monflabs.galtajs.rt.builtins.primitives.symbol.Symbol;
import org.monflabs.galtajs.rt.builtins.standard.promise.BuiltinPromise;
import org.monflabs.galtajs.rt.builtins.standard.promise.BuiltinPromiseConstructor;
import org.monflabs.util.generators.Generator;

/**
 * %AsyncGeneratorPrototype% - the shape-only counterpart to
 * BuiltinGeneratorPrototype for `async function*`. Per spec its own
 * [[Prototype]] is %AsyncIteratorPrototype% - now a real, minimal object
 * (see BuiltinAsyncIteratorPrototype, wired up via getDefaultPrototype()
 * below) carrying only [Symbol.asyncIterator], so it doesn't leak the SYNC
 * %IteratorPrototype%/%IteratorHelperPrototype% hierarchy's map/filter/
 * take/drop/... methods onto an async generator the way directly chaining
 * to the sync %IteratorPrototype% would have.
 *
 * Giving an async generator FUNCTION a real own "prototype" property
 * (BuiltinFunctionInterpreter, fixed alongside this class) means a real
 * async generator INSTANCE's [[Prototype]] now genuinely resolves here
 * (BuiltinGenerator's GetPrototypeFromConstructor-style constructor picks up
 * that own "prototype" instead of falling back to the SYNC
 * %GeneratorPrototype% default) - so this class's next()/return()/throw()
 * must at least preserve GaltaJS's existing (already-documented,
 * spec-imperfect) behavior for a real instance rather than leaving it
 * without any next() at all.
 *
 * Per spec, next()/return()/throw() must always return a real Promise object
 * (AsyncGeneratorEnqueue's own NewPromiseCapability step). The `drive()`
 * helper below resumes the generator body (gen.next()/throwInto()/
 * returnWith()) exactly like BuiltinGeneratorPrototype's own synchronous
 * resume - EXCEPT that when the body's own `await` yields an
 * AwaitYieldSignal (see that class's own doc comment for why an
 * async generator's `await` can't just block like a plain async
 * function's), `drive()` doesn't treat it as a real yielded value: it
 * resolves the awaited value through a real Promise and, once that
 * settles (asynchronously, via `.then_()` - never blocking any thread),
 * resumes the generator again (gen.next() on fulfillment, gen.throwInto()
 * on rejection) and recurses, repeating until a REAL yield, a normal
 * completion, or an uncaught exception occurs. This gives genuine
 * suspend-until-settled interleaving of yield and await without needing
 * JSAsyncExecutor's thread-per-async-call model to interleave anything
 * itself - the generator's own Exchanger-paired worker thread is only ever
 * suspended via GeneratorImpl's existing yield/resume protocol, never
 * parked mid-await.
 */
public class BuiltinAsyncGeneratorPrototype extends BasePrototype {

	public static BuiltinAsyncGeneratorPrototype get(JSEnvironment env) {
		BuiltinAsyncGeneratorPrototype proto = (BuiltinAsyncGeneratorPrototype)env.getRegisteredPrototype(BuiltinAsyncGeneratorPrototype.class);
		if(proto==null) {
			proto = new BuiltinAsyncGeneratorPrototype(env);
			env.registerPrototype(BuiltinAsyncGeneratorPrototype.class,proto);
		}
		return proto;
	}

	private BuiltinAsyncGeneratorPrototype(JSEnvironment env) {
		super(env);
		setOwnMethod(new Method(env,MethodId._next,1));
		setOwnMethod(new Method(env,MethodId._throw,1));
		setOwnMethod(new Method(env,MethodId._return,1));
		setOwnProperty(Symbol.TO_STRING_TAG,"AsyncGenerator",PropertyDescriptor.DESC_PROP_TOSTRINGTAG);
	}

	@Override
	public String getClassName() {
		return "AsyncGenerator";
	}

	// Per spec, %AsyncGeneratorPrototype%'s own [[Prototype]] is
	// %AsyncIteratorPrototype% (see BuiltinAsyncIteratorPrototype's own doc
	// comment for why this is now safe to wire up, unlike chaining through
	// the sync %IteratorPrototype% hierarchy).
	@Override
	protected Object getDefaultPrototype() {
		return BuiltinAsyncIteratorPrototype.get(getEnvironment());
	}

	private static Generator<Object,Object> asGenerator(Object obj) {
		// A sync generator instance (`function*`) is also a BuiltinGenerator -
		// isAsync() distinguishes it from a real async generator instance, so
		// AsyncGeneratorPrototype.next/throw/return correctly reject a sync
		// generator `this` too (confirmed via this-val-not-async-generator.js,
		// which calls these methods with a plain sync generator instance).
		if(!(obj instanceof BuiltinGenerator bg) || !bg.isAsync()) {
			throw RuntimeUtil.typeError("Method called on incompatible receiver {0}", obj!=null?obj.getClass():"null");
		}
		return bg.getGenerator();
	}

	private static enum MethodId {
		_next("next"),
		_throw("throw"),
		_return("return"),
		;
		final String id;
		MethodId(String id) {
			this.id = id;
		}
	}

	private final static class Method extends BaseMethod {
		private MethodId methodId;

		private Method(JSEnvironment env, MethodId methodId, int length) {
			super(env,methodId.id,length);
			this.methodId = methodId;
		}

		@Override
		public Object call(final Object obj, final Object[] args) {
			// AsyncGeneratorEnqueue (spec) always returns a real Promise, even
			// when resuming the generator throws synchronously - so every path
			// through drive() below settles `p` instead of throwing/returning a
			// plain value directly. See this class's own doc comment. This
			// includes the `this`-value validation itself: per spec step 3,
			// an invalid `this` REJECTS the promise (NewPromiseCapability runs
			// before the check), it doesn't throw synchronously - confirmed
			// via this-val-not-async-generator.js/this-val-not-object.js,
			// which .then()-chain a rejection handler and never expect a
			// thrown exception at the call site.
			BuiltinPromise p = new BuiltinPromise(getEnvironment());
			Generator<Object,Object> gen;
			BuiltinGenerator bg;
			try {
				gen = asGenerator(obj);
				bg = (BuiltinGenerator)obj;
			} catch(Throwable t) {
				p.reject(JSRuntimeException.exceptionObject(t));
				return p;
			}
			Object arg = args.length>0 ? args[0] : RuntimeUtil.UNDEFINED;
			// Queue this request rather than driving it directly - if an
			// earlier request on this SAME generator is still in-flight
			// (suspended mid-await), pumpQueue() below is a no-op for now and
			// this task runs later, once that earlier request reaches a real
			// yield/completion/exception (see BuiltinGenerator's own doc
			// comment on asyncResumeQueue for why this can't just resume
			// immediately).
			bg.getAsyncResumeQueue().add(() -> drive(gen,p,obj,methodId,arg,bg));
			if(!bg.isAsyncResuming()) {
				bg.setAsyncResuming(true);
				pumpQueue(bg);
			}
			return p;
		}

		// Runs the next queued request for this generator, if any - called
		// once at the tail of drive() for every request that reaches a REAL
		// terminal result (never for one still suspended mid-await, which
		// instead resumes the chain itself once its own await settles).
		private void pumpQueue(BuiltinGenerator bg) {
			Runnable next = bg.getAsyncResumeQueue().poll();
			if(next==null) {
				bg.setAsyncResuming(false);
				return;
			}
			next.run();
		}

		// Resumes gen per `kind` (mirroring next()/throwInto()/returnWith() as
		// methodId always did), then either settles `p` with the result, or -
		// if the body's own `await` produced an AwaitYieldSignal instead of a
		// real yield - asynchronously resolves the awaited value and resumes
		// again, recursing until a real yield/completion/exception is reached.
		// Never blocks: each resumption after the first happens inside a
		// `.then_()` callback, i.e. as its own later microtask.
		//
		// A `return()` completion gets three additional spec behaviors
		// (AsyncGeneratorAwaitReturn/AsyncGeneratorUnwrapYieldResumption) not
		// shared by next()/throw():
		// - while [[AsyncGeneratorState]] is suspendedStart (never resumed) or
		//   completed (already done), the body is closed WITHOUT being resumed
		//   at all - confirmed via return-suspendedStart.js ("Generator must
		//   not be resumed" if the body ran) and return-state-completed.js
		//   (repeat return() on an exhausted generator).
		// - the completion value is always unwrapped through PromiseResolve
		//   before settling, whether or not the generator ever ran - confirmed
		//   via return-suspendedStart-promise.js/return-suspendedYield-
		//   promise.js's `ret.value === 'unwrapped-value'` (not the raw
		//   Promise object).
		// - while suspended MID-EXECUTION (a real yield point), the return
		//   value is ALSO unwrapped through PromiseResolve, but BEFORE the
		//   coroutine is resumed at all, not after - a rejection there
		//   becomes a THROW completion at the yield point, observable by an
		//   enclosing try/catch inside the generator body (see the
		//   dedicated pre-resumeAndProcess() Await block below).
		private void drive(Generator<Object,Object> gen, BuiltinPromise p, Object obj, MethodId kind, Object arg, BuiltinGenerator bg) {
			// Once completed (whether via a natural body completion or one of
			// the two skip-resume paths below), the underlying `gen` must
			// never be touched again for ANY kind - it may never have
			// actually run (the suspendedStart+return case), so calling
			// gen.next()/throwInto() on it now would genuinely start
			// executing the body instead of correctly no-op'ing. Confirmed
			// via return-suspendedStart.js's own follow-up `it.next()` after
			// the generator was closed by return() without ever running -
			// expects {value:undefined,done:true}, not the body's own throw.
			if(bg.isAsyncCompleted()) {
				switch(kind) {
					case _next -> p.resolvePromise(JSObject.of(getEnvironment(),"value",RuntimeUtil.UNDEFINED,"done",true));
					case _throw -> p.reject(arg);
					case _return -> settleReturnValue(p, arg, bg);
				}
				if(kind!=MethodId._return) {
					pumpQueue(bg);
				}
				return;
			}
			if(kind==MethodId._return && !bg.isAsyncStarted()) {
				bg.setAsyncCompleted(true);
				settleReturnValue(p, arg, bg);
				return;
			}
			if(kind==MethodId._return) {
				// AsyncGeneratorUnwrapYieldResumption (27.6.3.7 step 2)/
				// yield*'s own "generatorKind is async" Await step: resuming
				// a SUSPENDED coroutine (mid-execution, at a yield or yield*
				// point) with a return completion must Await the return
				// VALUE first - the awaited/settled outcome becomes the
				// ACTUAL completion the yield expression resumes with
				// (fulfilled -> a return completion carrying the awaited
				// value; rejected -> a THROW completion instead, observable
				// by an enclosing try/catch INSIDE the generator body
				// itself, before any GeneratorReturnSignal-style unwind
				// happens). Unlike the two cases above (suspendedStart/
				// completed, where settleReturnValue() already does this
				// same await, but AFTER the body - which never runs at all
				// there), here the await must happen BEFORE resumeAndProcess()
				// ever calls gen.returnWith() - test262 AsyncGeneratorPrototype/
				// return/return-suspendedYield-broken-promise-try-catch.js,
				// statements/async-generator/yield-star-return-then-getter-
				// ticks.js.
				BuiltinPromise awaited;
				try {
					awaited = BuiltinPromiseConstructor.resolve(getEnvironment(),arg);
				} catch(Throwable t) {
					resumeAndProcess(gen,p,obj,MethodId._throw,JSRuntimeException.exceptionObject(t),bg);
					return;
				}
				awaited.performPromiseThen_(
					(thisArg,fulfilledArgs) -> {
						Object v = fulfilledArgs.length>0 ? fulfilledArgs[0] : RuntimeUtil.UNDEFINED;
						resumeAndProcess(gen,p,obj,MethodId._return,v,bg);
						return RuntimeUtil.UNDEFINED;
					},
					(thisArg,rejectedArgs) -> {
						Object reason = rejectedArgs.length>0 ? rejectedArgs[0] : RuntimeUtil.UNDEFINED;
						resumeAndProcess(gen,p,obj,MethodId._throw,reason,bg);
						return RuntimeUtil.UNDEFINED;
					}
				);
				return;
			}
			resumeAndProcess(gen,p,obj,kind,arg,bg);
		}

		// The actual gen.next()/throwInto()/returnWith() resumption and
		// result handling, shared by drive()'s own next()/throw() dispatch
		// AND by its return() dispatch's post-await continuation above -
		// kept separate so that continuation doesn't re-trigger the
		// pre-resume Await a second time.
		private void resumeAndProcess(Generator<Object,Object> gen, BuiltinPromise p, Object obj, MethodId kind, Object arg, BuiltinGenerator bg) {
			bg.setAsyncStarted(true);
			try {
				Object yielded = switch(kind) {
					case _next -> gen.next(arg);
					case _throw -> gen.throwInto(JSRuntimeException.asJavascriptException(null,arg));
					case _return -> gen.returnWith(arg);
				};
				if(yielded instanceof AwaitYieldSignal ays) {
					BuiltinPromise awaited;
					try {
						// PromiseResolve (BuiltinPromiseConstructor.resolve())
						// can itself throw SYNCHRONOUSLY (its own doc: a
						// poisoned `constructor` getter on an already-Promise
						// value, per spec's `?` on PromiseResolve step 2a) -
						// this happens OUTSIDE the coroutine's own stack (the
						// body already suspended via yielder.yield() to reach
						// this driver code), so awaitInGenerator_()'s CALLER
						// (e.g. yieldStar_()'s own try/catch around a
						// sync-delegate value's await, which needs to run its
						// OWN cleanup - close the underlying sync iterator)
						// would otherwise never see this exception at all if
						// it just propagated to the outer catch below (that
						// rejects `p` directly and abandons the coroutine,
						// still suspended, forever). Route it back INTO the
						// coroutine instead, exactly like an ordinary
						// rejected-promise .then_() reject handler already
						// does below, so it resumes at the yield point via a
						// real throw - test262 built-ins/
						// AsyncFromSyncIteratorPrototype/{next,throw}/
						// *-poisoned-wrapper.js.
						awaited = BuiltinPromiseConstructor.resolve(getEnvironment(),ays.value());
					} catch(Throwable t) {
						drive(gen,p,obj,MethodId._throw,JSRuntimeException.exceptionObject(t),bg);
						return;
					}
					awaited.performPromiseThen_(
						(thisArg,fulfilledArgs) -> {
							Object v = fulfilledArgs.length>0 ? fulfilledArgs[0] : RuntimeUtil.UNDEFINED;
							drive(gen,p,obj,MethodId._next,v,bg);
							return RuntimeUtil.UNDEFINED;
						},
						(thisArg,rejectedArgs) -> {
							Object reason = rejectedArgs.length>0 ? rejectedArgs[0] : RuntimeUtil.UNDEFINED;
							drive(gen,p,obj,MethodId._throw,reason,bg);
							return RuntimeUtil.UNDEFINED;
						}
					);
					return;
				}
				Object v = (yielded instanceof YieldStarDelegateResult ysr) ? ysr.rawResult() : JSObject.of(getEnvironment(),"value",yielded,"done",false);
				p.resolvePromise(v);
			} catch(NoSuchElementException e) {
				bg.setAsyncCompleted(true);
				if(kind==MethodId._return) {
					// A genuinely new completion event with its own value -
					// same latch reset BuiltinGeneratorPrototype's own sync
					// return() case uses, so getDoneValue() below returns the
					// return() call's OWN arg instead of degrading to
					// undefined (the "already consumed" behavior correct only
					// for a later redundant next()).
					bg.resetConsumedReturnedValue();
					// Deliberately NOT settleReturnValue() here (unlike the
					// suspendedStart/completed cases in drive(), which call
					// it directly) - THIS completion is only ever reached
					// via resumeAndProcess(), which for kind==_return is
					// only ever invoked AFTER drive()'s own pre-resume
					// Await already unwrapped the return value (spec
					// AsyncGeneratorYield step 8b/AsyncGeneratorUnwrapYieldResumption
					// step 2) - re-unwrapping it again here would be a
					// SECOND, spec-incorrect PromiseResolve/Await of the
					// same value (confirmed as an observable regression via
					// test262 language/statements/async-generator/yield-
					// return-then-getter-ticks.js: a second "then" getter
					// read that spec's own algorithm never performs, since
					// AsyncGeneratorAwaitReturn's unwrap is exclusive to the
					// generator-never-actually-resumed cases).
				}
				p.resolvePromise(JSObject.of(getEnvironment(),"value",bg.getDoneValue(),"done",true));
			} catch(Throwable t) {
				bg.setAsyncCompleted(true);
				p.reject(JSRuntimeException.exceptionObject(t));
			}
			pumpQueue(bg);
		}

		// AsyncGeneratorAwaitReturn's value-unwrap: settle `p` with
		// {value, done:true} where `value` is `raw` resolved through
		// PromiseResolve (a thenable `raw` is awaited, a plain value passes
		// through unchanged). Always asynchronous (even for a plain value),
		// matching every already-passing caller of this path being a
		// `flags:[async]` test that only ever observes settlement via
		// `.then()`.
		private void settleReturnValue(BuiltinPromise p, Object raw, BuiltinGenerator bg) {
			// Spec (AsyncGeneratorAwaitReturn step 6/7): "Let promise be
			// Completion(PromiseResolve(%Promise%, value))." - if THAT
			// itself is an abrupt completion (e.g. a poisoned "constructor"
			// getter on a promise-shaped `raw`), the generator's result
			// promise must be REJECTED with it, not let the exception
			// propagate uncaught out of this method (test262's own
			// return-{suspendedStart,state-completed}-broken-promise.js).
			BuiltinPromise resolved;
			try {
				resolved = BuiltinPromiseConstructor.resolve(getEnvironment(),raw);
			} catch(RuntimeException e) {
				p.reject(JSRuntimeException.exceptionObject(e));
				pumpQueue(bg);
				return;
			}
			resolved.performPromiseThen_(
				(thisArg,fulfilledArgs) -> {
					Object v = fulfilledArgs.length>0 ? fulfilledArgs[0] : RuntimeUtil.UNDEFINED;
					p.resolvePromise(JSObject.of(getEnvironment(),"value",v,"done",true));
					pumpQueue(bg);
					return RuntimeUtil.UNDEFINED;
				},
				(thisArg,rejectedArgs) -> {
					Object reason = rejectedArgs.length>0 ? rejectedArgs[0] : RuntimeUtil.UNDEFINED;
					p.reject(reason);
					pumpQueue(bg);
					return RuntimeUtil.UNDEFINED;
				}
			);
		}
	}
}
