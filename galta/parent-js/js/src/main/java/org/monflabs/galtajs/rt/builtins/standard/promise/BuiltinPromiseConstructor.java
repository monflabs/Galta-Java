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
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.jsonfactory.JSArray;
import org.monflabs.galtajs.jsonfactory.JSObject;
import org.monflabs.galtajs.rt.JSRuntimeContext;
import org.monflabs.galtajs.rt.JSRuntimeException;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.builtins.BaseMethod;
import org.monflabs.galtajs.rt.builtins.Callable;
import org.monflabs.galtajs.rt.builtins.Constructor;
import org.monflabs.galtajs.rt.builtins.errors.AggregateError;
import org.monflabs.galtajs.rt.builtins.primitives.BaseStandardConstructor;
import org.monflabs.galtajs.rt.builtins.standard.function.BuiltinFunctionNative;
import org.monflabs.galtajs.rt.builtins.primitives.symbol.Symbol;


/* ============================================================
 * Promise constructor & static methods
 * ============================================================ */
public class BuiltinPromiseConstructor extends BaseStandardConstructor {

	public static final String CLASSNAME = "Promise";

	public BuiltinPromiseConstructor(JSEnvironment env) {
		super(env,CLASSNAME, BuiltinPromisePrototype.get(env), 1);
		setOwnMethod(new Method(env,MethodId.all, 1));
		setOwnMethod(new Method(env,MethodId.allSettled, 1));
		setOwnMethod(new Method(env,MethodId.allKeyed, 1));
		setOwnMethod(new Method(env,MethodId.allSettledKeyed, 1));
		setOwnMethod(new Method(env,MethodId.any, 1));
		setOwnMethod(new Method(env,MethodId.race, 1));
		setOwnMethod(new Method(env,MethodId.reject, 1));
		setOwnMethod(new Method(env,MethodId.resolve, 1));
		setOwnMethod(new Method(env,MethodId._try, 1));
		setOwnMethod(new Method(env,MethodId.withResolvers, 1));
		
		// get [Symbol.species] () { return this; } - a getter-only accessor
		// (not a static value) so subclasses correctly return themselves.
		setOwnProperty(Symbol.SPECIES, true, false, (base,key) -> base, null);
	}

	@Override
	public Class<?> getNativeClass() {
		return BuiltinPromise.class;
	}

	@Override
	public Object call(Object _this, Object[] parameters) {
		throw RuntimeUtil.typeError("Promise constructor cannot be called as a function");
	}

	@Override
	public Object constructObject(Object[] parameters, Constructor topConstructor) {
		if (parameters.length < 1) {
			throw RuntimeUtil.typeError("Promise constructor requires a callable as its first argument");
		}

		if (!BuiltinUtil.isCallable(parameters[0])) {
			throw RuntimeUtil.typeError("Promise constructor requires a callable as its first argument");
		}

		Callable c = (Callable) parameters[0];

		Object _this = RuntimeUtil.UNDEFINED;
		if (!RuntimeUtil.isStrictMode()) {
			Object tcs = JSRuntimeContext.get().getGlobalContext().getThis();
			if (tcs != null) {
				_this = tcs;
			}
		}

		BuiltinPromise promise = applyNewTargetPrototype(new BuiltinPromise(getEnvironment()), topConstructor);
		AtomicBoolean alreadyResolved = new AtomicBoolean(false);

		// Promise Resolve/Reject Functions are spec'd as real, anonymous,
		// non-constructor built-in function objects (length 1 each) - not
		// raw Java lambdas - so JS code that captures `resolve`/`reject`
		// observes correct length/name/prototype/extensibility.
		Callable resolveFn = new BuiltinFunctionNative(getEnvironment(), "", 1) {
			@Override
			public Object call(Object thisArg, Object[] args, Constructor newTarget) {
				if (alreadyResolved.getAndSet(true))
					return RuntimeUtil.UNDEFINED;
				Object resolution = args.length > 0 ? args[0] : RuntimeUtil.UNDEFINED;
				promise.resolvePromise(resolution);
				return RuntimeUtil.UNDEFINED;
			}
		};
		Callable rejectFn = new BuiltinFunctionNative(getEnvironment(), "", 1) {
			@Override
			public Object call(Object thisArg, Object[] args, Constructor newTarget) {
				if (alreadyResolved.getAndSet(true))
					return RuntimeUtil.UNDEFINED;
				Object reason = args.length > 0 ? args[0] : RuntimeUtil.UNDEFINED;
				promise.reject(reason);
				return RuntimeUtil.UNDEFINED;
			}
		};
		PromiseCapability cap = new PromiseCapability(promise, resolveFn, rejectFn);

		try {
			c.call(_this, new Object[] { cap.getResolve(), cap.getReject() });
		} catch (JSRuntimeException e) {
			RuntimeUtil.rethrowIfUncatchable(e);
			cap.getReject().call(_this, new Object[] { e.getJavascriptException() });
		}
		return promise;
	}

	private static enum MethodId {
		all,
		allSettled,
		allKeyed,
		allSettledKeyed,
		any,
		race, 
		reject, 
		resolve, 
		_try("try"), 
		withResolvers,
		// Symbols
		species(Symbol.SPECIES)
		;

		Object id;

		MethodId() {
			this.id = name();
		}

		MethodId(String name) {
			this.id = name;
		}

		MethodId(Symbol id) {
			this.id = id;
		}
	}

	private static final class Method extends BaseMethod {
		private MethodId methodId;

		private Method(JSEnvironment env, MethodId methodId, int length) {
			super(env, methodId.id, length);
			this.methodId = methodId;
		}

		@Override
		protected Object invoke(final Object obj, final Object[] args) {
			// obj is the 'this' value - the constructor these static methods were
			// called on. Per spec, all/allSettled/any/race/resolve/reject each
			// start with "If Type(C) is not Object, throw a TypeError" - was
			// silently falling back to the default Promise constructor instead,
			// which let e.g. Promise.all.call(undefined, []) succeed instead of
			// throwing (test262's */ctx-non-object.js). A non-object receiver
			// must throw; a non-constructor OBJECT receiver is left to
			// NewPromiseCapability's own IsConstructor check further down.
			if (!RuntimeUtil.isObject(getEnvironment(), obj)) {
				throw RuntimeUtil.typeError("Promise method called on a non-object receiver");
			}
			Constructor thisConstructor = (obj instanceof Constructor) ? (Constructor) obj : null;

			switch (methodId) {
				case all -> {
				    // Create the capability (promise + resolve + reject)
				    PromiseCapability cap = BuiltinPromise.newPromiseCapability(getEnvironment(), thisConstructor);

				    // Spec: promiseResolve = ? Get(constructor,"resolve") happens
				    // exactly ONCE, before the iterator is even created - not once
				    // per element, and not skipped for a zero-element iterable
				    // (test262's invoke-resolve-get-once-{multiple-calls,no-calls}.js).
				    Callable promiseResolve;
				    try {
				        promiseResolve = getPromiseResolve(getEnvironment(), thisConstructor);
				    } catch (Exception ex) {
				        cap.getReject().call(null, JSRuntimeException.exceptionObject(ex));
				        return cap.getPromise();
				    }

					Object arr = param(args, 0);

				    // ---- Get iterator ----
				    Iterator<Object> it;
				    try {
				        it = RuntimeUtil.valueIterator(getEnvironment(), arr);
				    } catch (Exception ex) {
				        // Per spec: if getting iterator fails, reject the promise (don't throw)
				        cap.getReject().call(null, JSRuntimeException.exceptionObject(ex));
				        return cap.getPromise();
				    }

				    // ---- Prepare bookkeeping ----
				    JSArray values = JSArray.create(getEnvironment());
				    // Per spec: remaining starts at 1, so final decrement resolves even if empty
				    AtomicInteger remaining = new AtomicInteger(1);
				    int index = 0;

				    // ---- Iterate ----
				    boolean iterDone = false;
				    try {
				        while (true) {
				            // IteratorStep/IteratorValue: an abrupt completion HERE
				            // means the iterator itself is already considered done -
				            // no IteratorClose (test262's iter-{next-val,step}-err-
				            // no-close.js) - unlike an abrupt completion from the
				            // resolve/then calls below, which DOES need IteratorClose.
				            Object nextValue;
				            try {
				                if (!it.hasNext()) {
				                    break;
				                }
				                nextValue = it.next();
				            } catch (Exception ex) {
				                iterDone = true;
				                throw ex;
				            }
				            final int idx = index++;
				            values.arrayAdd(null);                 // pre-allocate slot
				            remaining.incrementAndGet();        // +1 for this element

				            Object p = promiseResolve.call(thisConstructor, new Object[]{nextValue});

				            // Promise All Resolve Element Functions: [[AlreadyCalled]] - a
				            // thenable invoking its onFulfilled callback more than once
				            // must have every call after the first be a no-op. Spec'd as
				            // a real, anonymous, non-constructor function object (length
				            // 1), not a raw Java lambda.
				            AtomicBoolean elementCalled = new AtomicBoolean(false);
				            Callable resolveElementFn = new BuiltinFunctionNative(getEnvironment(), "", 1) {
				                @Override
				                public Object call(Object t, Object[] a, Constructor newTarget) {
				                    if (!elementCalled.compareAndSet(false, true)) {
				                        return RuntimeUtil.UNDEFINED;
				                    }
				                    Object v = a.length > 0 ? a[0] : null;
				                    values.setOwnProperty(idx, v);
				                    // decrement and possibly resolve
				                    if (remaining.decrementAndGet() == 0) {
				                        cap.getResolve().call(null, values);
				                    }
				                    return RuntimeUtil.UNDEFINED;
				                }
				            };
				            // Per spec (PerformPromiseAll step 6.r): the SAME
				            // resultCapability.[[Reject]] is passed directly as the
				            // reject argument to `then` - not a separate per-element
				            // wrapper - it already no-ops after the first settle
				            // (shared [[AlreadyResolved]] with resolve, see
				            // BuiltinPromiseConstructor.constructObject).
				            thenOnPromiseLike(getEnvironment(), p, resolveElementFn, cap.getReject());
				        }
				        iterDone = true;
				    } catch (Exception ex) {
				        // Iterator.next()/resolve()/then() threw synchronously - per
				        // spec, IteratorClose the iterator (best-effort) before rejecting.
				        if (!iterDone) {
				            RuntimeUtil.iteratorCloseQuietly(getEnvironment(), it);
				        }
				        cap.getReject().call(null, JSRuntimeException.exceptionObject(ex));
				        return cap.getPromise();
				    }

				    // ---- Final decrement (handles empty iterables) ----
				    // By this point the iterator is definitely already exhausted, so
				    // no IteratorClose either way - but resolve() itself may still
				    // throw for a non-default constructor (test262's
				    // capability-resolve-throws-no-close.js), which must reject
				    // rather than propagate as an uncaught exception.
				    try {
				        if (remaining.decrementAndGet() == 0) {
				            cap.getResolve().call(null, values);
				        }
				    } catch (Exception ex) {
				        cap.getReject().call(null, JSRuntimeException.exceptionObject(ex));
				    }

				    return cap.getPromise();
				}
				case allSettled -> {
					PromiseCapability cap = BuiltinPromise.newPromiseCapability(getEnvironment(), thisConstructor);

				    // Spec: promiseResolve = ? Get(constructor,"resolve") happens
				    // exactly ONCE, before the iterator is even created - see the
				    // `all` case above for the full rationale.
				    Callable promiseResolve;
				    try {
				        promiseResolve = getPromiseResolve(getEnvironment(), thisConstructor);
				    } catch (Exception ex) {
				        cap.getReject().call(null, JSRuntimeException.exceptionObject(ex));
				        return cap.getPromise();
				    }

					Object arr = param(args, 0);

				    Iterator<Object> it;
				    try {
				        it = RuntimeUtil.valueIterator(getEnvironment(), arr);
				    } catch (Exception ex) {
				        // Per spec: if getting iterator fails, reject the promise (don't throw)
				        cap.getReject().call(null, JSRuntimeException.exceptionObject(ex));
				        return cap.getPromise();
				    }

					JSArray out = JSArray.create(getEnvironment());
					// Per spec: remaining starts at 1, so final decrement resolves even if empty
					AtomicInteger remaining = new AtomicInteger(1);
					int index = 0;

					boolean iterDone = false;
					try {
						while (true) {
							// See the `all` case above: an abrupt completion from the
							// iterator itself means no IteratorClose, unlike one from
							// resolve/then below.
							Object item;
							try {
								if (!it.hasNext()) {
									break;
								}
								item = it.next();
							} catch (Exception ex) {
								iterDone = true;
								throw ex;
							}
							int idx = index++;
							out.arrayAdd(null);
							remaining.incrementAndGet();
							Object p = promiseResolve.call(thisConstructor, new Object[]{item});
							// Promise AllSettled Resolve/Reject Element Functions:
							// [[AlreadyCalled]] - each element settles at most once.
							// Spec'd as real, anonymous, non-constructor function
							// objects (length 1 each), not raw Java lambdas.
							AtomicBoolean elementCalled = new AtomicBoolean(false);
							Callable resolveElementFn = new BuiltinFunctionNative(getEnvironment(), "", 1) {
								@Override
								public Object call(Object t, Object[] a, Constructor newTarget) {
									if (!elementCalled.compareAndSet(false, true)) {
										return RuntimeUtil.UNDEFINED;
									}
									JSObject r = JSObject.create(getEnvironment());
									r.setOwnProperty("status", "fulfilled");
									r.setOwnProperty("value", a.length > 0 ? a[0] : null);
									out.setOwnProperty(idx, r);
									if (remaining.decrementAndGet() == 0) {
										cap.getResolve().call(null, out);
									}
									return RuntimeUtil.UNDEFINED;
								}
							};
							Callable rejectElementFn = new BuiltinFunctionNative(getEnvironment(), "", 1) {
								@Override
								public Object call(Object t, Object[] a, Constructor newTarget) {
									if (!elementCalled.compareAndSet(false, true)) {
										return RuntimeUtil.UNDEFINED;
									}
									JSObject r = JSObject.create(getEnvironment());
									r.setOwnProperty("status", "rejected");
									r.setOwnProperty("reason", a.length > 0 ? a[0] : null);
									out.setOwnProperty(idx, r);
									if (remaining.decrementAndGet() == 0) {
										cap.getResolve().call(null, out);
									}
									return RuntimeUtil.UNDEFINED;
								}
							};
							thenOnPromiseLike(getEnvironment(), p, resolveElementFn, rejectElementFn);
						}
						iterDone = true;
					} catch (Exception ex) {
						// Iterator.next()/resolve()/then() threw synchronously → reject
						// per spec, IteratorClose (best-effort) first.
						if (!iterDone) {
							RuntimeUtil.iteratorCloseQuietly(getEnvironment(), it);
						}
						cap.getReject().call(null, JSRuntimeException.exceptionObject(ex));
						return cap.getPromise();
					}
					// ---- Final decrement (handles empty iterables) ----
					try {
						if (remaining.decrementAndGet() == 0) {
							cap.getResolve().call(null, out);
						}
					} catch (Exception ex) {
						cap.getReject().call(null, JSRuntimeException.exceptionObject(ex));
					}
					return cap.getPromise();
				}
				case allKeyed -> {
					// Promise.allKeyed(promises) - "await-dictionary" proposal:
					// like Promise.all, but `promises` is an object (dictionary)
					// rather than an iterable - each OWN ENUMERABLE property
					// (string AND symbol keys, in [[OwnPropertyKeys]] order) is
					// resolved, and the result is a null-prototype object with
					// the SAME keys mapped to the resolved values. Rejects (like
					// Promise.all) on the first element rejection, or if
					// `promises` isn't an Object at all.
					PromiseCapability cap = BuiltinPromise.newPromiseCapability(getEnvironment(), thisConstructor);

					Callable promiseResolve;
					try {
						promiseResolve = getPromiseResolve(getEnvironment(), thisConstructor);
					} catch (Exception ex) {
						cap.getReject().call(null, JSRuntimeException.exceptionObject(ex));
						return cap.getPromise();
					}

					Object dict = param(args, 0);
					if (!RuntimeUtil.isObject(getEnvironment(), dict)) {
						cap.getReject().call(null, RuntimeUtil.typeError("Promise.allKeyed argument must be an object").getJavascriptException());
						return cap.getPromise();
					}

					JSObject out = JSObject.createWithPrototype(getEnvironment(), null);
					AtomicInteger remaining = new AtomicInteger(1);

					try {
						for (var it = getEnvironment().getAccessor(dict).ownEntries(dict, true); it.hasNext(); ) {
							var e = it.next();
							Object key = e.getKey();
							Object value = e.getValue();
							// Pre-create the output property (in dictionary
							// iteration order) BEFORE any promise settles -
							// resolveElementFn below only ever UPDATES this
							// same key later, never creates a fresh one, so
							// Object.keys(result) always reflects the
							// ORIGINAL key order regardless of which promise
							// happens to settle first (confirmed via
							// test262's own key-order-preserved.js).
							setKeyedProperty(out, key, RuntimeUtil.UNDEFINED);
							remaining.incrementAndGet();
							Object p = promiseResolve.call(thisConstructor, new Object[]{value});
							AtomicBoolean elementCalled = new AtomicBoolean(false);
							Callable resolveElementFn = new BuiltinFunctionNative(getEnvironment(), "", 1) {
								@Override
								public Object call(Object t, Object[] a, Constructor newTarget) {
									if (!elementCalled.compareAndSet(false, true)) {
										return RuntimeUtil.UNDEFINED;
									}
									setKeyedProperty(out, key, a.length > 0 ? a[0] : null);
									if (remaining.decrementAndGet() == 0) {
										cap.getResolve().call(null, out);
									}
									return RuntimeUtil.UNDEFINED;
								}
							};
							thenOnPromiseLike(getEnvironment(), p, resolveElementFn, cap.getReject());
						}
					} catch (Exception ex) {
						cap.getReject().call(null, JSRuntimeException.exceptionObject(ex));
						return cap.getPromise();
					}
					try {
						if (remaining.decrementAndGet() == 0) {
							cap.getResolve().call(null, out);
						}
					} catch (Exception ex) {
						cap.getReject().call(null, JSRuntimeException.exceptionObject(ex));
					}
					return cap.getPromise();
				}
				case allSettledKeyed -> {
					// Promise.allSettledKeyed(promises) - like allSettled, but
					// dictionary-keyed the same way allKeyed is: never rejects
					// due to an element's own rejection (only if `promises`
					// itself isn't an Object), each key mapping to a
					// {status,value}/{status,reason} settlement record.
					PromiseCapability cap = BuiltinPromise.newPromiseCapability(getEnvironment(), thisConstructor);

					Callable promiseResolve;
					try {
						promiseResolve = getPromiseResolve(getEnvironment(), thisConstructor);
					} catch (Exception ex) {
						cap.getReject().call(null, JSRuntimeException.exceptionObject(ex));
						return cap.getPromise();
					}

					Object dict = param(args, 0);
					if (!RuntimeUtil.isObject(getEnvironment(), dict)) {
						cap.getReject().call(null, RuntimeUtil.typeError("Promise.allSettledKeyed argument must be an object").getJavascriptException());
						return cap.getPromise();
					}

					JSObject out = JSObject.createWithPrototype(getEnvironment(), null);
					AtomicInteger remaining = new AtomicInteger(1);

					try {
						for (var it = getEnvironment().getAccessor(dict).ownEntries(dict, true); it.hasNext(); ) {
							var e = it.next();
							Object key = e.getKey();
							Object value = e.getValue();
							// Pre-create the output property in iteration
							// order - see the identical comment in the
							// allKeyed case above.
							setKeyedProperty(out, key, RuntimeUtil.UNDEFINED);
							remaining.incrementAndGet();
							Object p = promiseResolve.call(thisConstructor, new Object[]{value});
							AtomicBoolean elementCalled = new AtomicBoolean(false);
							Callable resolveElementFn = new BuiltinFunctionNative(getEnvironment(), "", 1) {
								@Override
								public Object call(Object t, Object[] a, Constructor newTarget) {
									if (!elementCalled.compareAndSet(false, true)) {
										return RuntimeUtil.UNDEFINED;
									}
									JSObject r = JSObject.create(getEnvironment());
									r.setOwnProperty("status", "fulfilled");
									r.setOwnProperty("value", a.length > 0 ? a[0] : null);
									setKeyedProperty(out, key, r);
									if (remaining.decrementAndGet() == 0) {
										cap.getResolve().call(null, out);
									}
									return RuntimeUtil.UNDEFINED;
								}
							};
							Callable rejectElementFn = new BuiltinFunctionNative(getEnvironment(), "", 1) {
								@Override
								public Object call(Object t, Object[] a, Constructor newTarget) {
									if (!elementCalled.compareAndSet(false, true)) {
										return RuntimeUtil.UNDEFINED;
									}
									JSObject r = JSObject.create(getEnvironment());
									r.setOwnProperty("status", "rejected");
									r.setOwnProperty("reason", a.length > 0 ? a[0] : null);
									setKeyedProperty(out, key, r);
									if (remaining.decrementAndGet() == 0) {
										cap.getResolve().call(null, out);
									}
									return RuntimeUtil.UNDEFINED;
								}
							};
							thenOnPromiseLike(getEnvironment(), p, resolveElementFn, rejectElementFn);
						}
					} catch (Exception ex) {
						cap.getReject().call(null, JSRuntimeException.exceptionObject(ex));
						return cap.getPromise();
					}
					try {
						if (remaining.decrementAndGet() == 0) {
							cap.getResolve().call(null, out);
						}
					} catch (Exception ex) {
						cap.getReject().call(null, JSRuntimeException.exceptionObject(ex));
					}
					return cap.getPromise();
				}
				case any -> {
					PromiseCapability cap = BuiltinPromise.newPromiseCapability(getEnvironment(), thisConstructor);

				    // Spec: promiseResolve = ? Get(constructor,"resolve") happens
				    // exactly ONCE, before the iterator is even created - see the
				    // `all` case above for the full rationale.
				    Callable promiseResolve;
				    try {
				        promiseResolve = getPromiseResolve(getEnvironment(), thisConstructor);
				    } catch (Exception ex) {
				        cap.getReject().call(null, JSRuntimeException.exceptionObject(ex));
				        return cap.getPromise();
				    }

					Object arr = param(args, 0);

				    Iterator<Object> it;
				    try {
				        it = RuntimeUtil.valueIterator(getEnvironment(), arr);
				    } catch (Exception ex) {
				        // Per spec: if getting iterator fails, reject the promise (don't throw)
				        cap.getReject().call(null, JSRuntimeException.exceptionObject(ex));
				        return cap.getPromise();
				    }

					// Errors are tracked BY ITERATION INDEX (a placeholder is
					// appended in iteration order as each element is seen), not by
					// settle/rejection order - elements can settle synchronously,
					// out of order, during the loop itself (test262's
					// resolve-before-loop-exit-from-same.js expects errors[i] to
					// match the i-th iterated element regardless of which
					// element's rejection actually arrives first).
					List<Object> errors = Collections.synchronizedList(new ArrayList<>());
					AtomicInteger remaining = new AtomicInteger(1);
					boolean iterDone = false;
					try {
						while (true) {
							// See the `all` case above: an abrupt completion from the
							// iterator itself means no IteratorClose, unlike one from
							// resolve/then below.
							Object item;
							try {
								if (!it.hasNext()) {
									break;
								}
								item = it.next();
							} catch (Exception ex) {
								iterDone = true;
								throw ex;
							}
							int idx = errors.size();
							errors.add(null);
							remaining.incrementAndGet();
							Object p = promiseResolve.call(thisConstructor, new Object[]{item});
							// Promise Any Reject Element Functions: [[AlreadyCalled]].
							// Spec'd as a real, anonymous, non-constructor function
							// object (length 1), not a raw Java lambda.
							AtomicBoolean elementCalled = new AtomicBoolean(false);
							Callable rejectElementFn = new BuiltinFunctionNative(getEnvironment(), "", 1) {
								@Override
								public Object call(Object t, Object[] a, Constructor newTarget) {
									if (!elementCalled.compareAndSet(false, true)) {
										return RuntimeUtil.UNDEFINED;
									}
									errors.set(idx, a.length > 0 ? a[0] : null);
									if (remaining.decrementAndGet() == 0) {
										cap.getReject().call(null, new AggregateError(getEnvironment(),new ArrayList<>(errors)));
									}
									return RuntimeUtil.UNDEFINED;
								}
							};
							// Per spec (PerformPromiseAny step r): the SAME
							// resultCapability.[[Resolve]] is passed directly - not a
							// separate per-element wrapper - only the reject side needs
							// its own per-element function (to collect errors by index).
							thenOnPromiseLike(getEnvironment(), p, cap.getResolve(), rejectElementFn);
						}
						iterDone = true;
					} catch (Exception ex) {
						// Iterator.next()/resolve()/then() threw synchronously → reject
						// per spec, IteratorClose (best-effort) first.
						if (!iterDone) {
							RuntimeUtil.iteratorCloseQuietly(getEnvironment(), it);
						}
						cap.getReject().call(null, JSRuntimeException.exceptionObject(ex));
						return cap.getPromise();
					}
					// Empty iterator or all rejected: reject with AggregateError (per spec)
					try {
						if (remaining.decrementAndGet() == 0) {
							cap.getReject().call(null, new AggregateError(getEnvironment(),errors));
						}
					} catch (Exception ex) {
						cap.getReject().call(null, JSRuntimeException.exceptionObject(ex));
					}
					return cap.getPromise();
				}
				case race -> {
					PromiseCapability cap = BuiltinPromise.newPromiseCapability(getEnvironment(), thisConstructor);

				    // Spec: promiseResolve = ? Get(constructor,"resolve") happens
				    // exactly ONCE, before the iterator is even created - see the
				    // `all` case above for the full rationale.
				    Callable promiseResolve;
				    try {
				        promiseResolve = getPromiseResolve(getEnvironment(), thisConstructor);
				    } catch (Exception ex) {
				        cap.getReject().call(null, JSRuntimeException.exceptionObject(ex));
				        return cap.getPromise();
				    }

					Object arr = param(args, 0);

				    Iterator<Object> it;
				    try {
				        it = RuntimeUtil.valueIterator(getEnvironment(), arr);
				    } catch (Exception ex) {
				        // Per spec: if getting iterator fails, reject the promise (don't throw)
				        cap.getReject().call(null, JSRuntimeException.exceptionObject(ex));
				        return cap.getPromise();
				    }

					boolean iterDone = false;
					try {
						while (true) {
							// See the `all` case above: an abrupt completion from the
							// iterator itself means no IteratorClose, unlike one from
							// resolve/then below.
							Object item;
							try {
								if (!it.hasNext()) {
									break;
								}
								item = it.next();
							} catch (Exception ex) {
								iterDone = true;
								throw ex;
							}
							Object p = promiseResolve.call(thisConstructor, new Object[]{item});
							// Per spec (PerformPromiseRace step j): the capability's own
							// [[Resolve]]/[[Reject]] are passed directly - no per-element
							// wrapper at all - they already no-op after the first settle.
							thenOnPromiseLike(getEnvironment(), p, cap.getResolve(), cap.getReject());
						}
						iterDone = true;
					} catch (Exception ex) {
						// Iterator.next()/resolve()/then() threw synchronously → reject
						// per spec, IteratorClose (best-effort) first.
						if (!iterDone) {
							RuntimeUtil.iteratorCloseQuietly(getEnvironment(), it);
						}
						cap.getReject().call(null, JSRuntimeException.exceptionObject(ex));
						return cap.getPromise();
					}
					return cap.getPromise();
				}
				case reject -> {
					Object r = param(args, 0, RuntimeUtil.UNDEFINED);
					return reject(getEnvironment(), thisConstructor, r);
				}
				case resolve -> {
					Object x = param(args, 0, RuntimeUtil.UNDEFINED);
					return resolve(getEnvironment(), thisConstructor, x);
				}
				case _try -> {
					Object fn = param(args, 0, RuntimeUtil.UNDEFINED);
					if (!(fn instanceof Callable c && c.isCallable())) {
						throw RuntimeUtil.typeError("Promise.try callback is not a function");
					}
					Object[] restArgs = args.length > 1 ? java.util.Arrays.copyOfRange(args, 1, args.length) : RuntimeUtil.EMPTY_PARAMS;
					// Spec: a promise capability is only ever created on the
					// REJECT path (callback throws). On success, return
					// PromiseResolve(C, result) directly - same "avoid an
					// extra wrapper promise" shortcut as Promise.resolve()
					// itself: if `result` is already a promise whose own
					// constructor === C, it's returned AS-IS, no new
					// capability/promise created at all (test262's own
					// avoids-wrap.js/avoids-wrap-for-subclass.js).
					try {
						Object result = c.call(RuntimeUtil.UNDEFINED, restArgs);
						return resolve(getEnvironment(), thisConstructor, result);
					} catch (Exception ex) {
						PromiseCapability cap = BuiltinPromise.newPromiseCapability(getEnvironment(), thisConstructor);
						cap.getReject().call(null, JSRuntimeException.exceptionObject(ex));
						return cap.getPromise();
					}
				}
				case withResolvers -> {
					PromiseCapability cap = BuiltinPromise.newPromiseCapability(getEnvironment(), thisConstructor);
					JSObject result = JSObject.create(getEnvironment());
					result.setOwnProperty("promise", cap.getPromise());
					result.setOwnProperty("resolve", cap.getResolve());
					result.setOwnProperty("reject", cap.getReject());
					return result;
				}
	
				default -> {
					throw new IllegalStateException(); // Should never be here
				}
			}
		}
	}

	// Promise.all/allSettled/any/race must call the OBSERVABLE, overridable
	// constructor.resolve METHOD (spec: "Let promiseResolve be ? Get(C,
	// 'resolve')") - unlike the internal resolve(env,constructor,x) below,
	// which implements the abstract PromiseResolve operation used by e.g.
	// Promise.prototype.then and deliberately bypasses any user override.
	// Fetched exactly ONCE per combinator call (not once per element, and
	// not skipped for an empty iterable) - test262's invoke-resolve-get-
	// once-{multiple-calls,no-calls}.js.
	private static Callable getPromiseResolve(JSEnvironment env, Constructor constructor) {
		Object resolveFn = env.getAccessor(constructor).getProperty(constructor, "resolve", RuntimeUtil.UNDEFINED);
		if (!(resolveFn instanceof Callable c && c.isCallable())) {
			throw RuntimeUtil.typeError("Promise resolve is not a function");
		}
		return c;
	}

	// Likewise, .then() must ALWAYS be invoked generically (Get+Call) - not
	// just for non-BuiltinPromise results, but even for a genuine
	// BuiltinPromise instance, since its own OWN "then" property can be
	// individually overridden (Object.defineProperty(promise, "then", ...))
	// while the object itself remains a real BuiltinPromise. A Java-level
	// fast path calling bp.then_() directly would silently skip such an
	// override (test262's Promise/all/invoke-then-error-close.js: an
	// infinite iterator combined with an overridden, always-throwing
	// "then" that never actually gets invoked would spin forever).
	private static void thenOnPromiseLike(JSEnvironment env, Object promiseLike, Callable onFulfilled, Callable onRejected) {
		Object thenFn = env.getAccessor(promiseLike).getProperty(promiseLike, "then", RuntimeUtil.UNDEFINED);
		if (!(thenFn instanceof Callable c && c.isCallable())) {
			throw RuntimeUtil.typeError("then is not a function");
		}
		c.call(promiseLike, new Object[]{onFulfilled, onRejected});
	}

	// allKeyed/allSettledKeyed's result keys can be either strings or symbols
	// (ownEntries(dict,true) enumerates both) - dispatch on the actual
	// runtime key type, same pattern as IteratorZip's buildKeyedRow.
	private static void setKeyedProperty(JSObject out, Object key, Object value) {
		if (key instanceof Symbol sym) {
			out.setOwnProperty(sym, value);
		} else {
			out.setOwnProperty((String) key, value);
		}
	}

	// `constructor` may be ANY constructor (Promise.resolve/reject can be
	// called with an arbitrary `this`, e.g. Promise.resolve.call(C, x)) - the
	// result is whatever NewPromiseCapability(C) produces, not necessarily a
	// real BuiltinPromise.
	public static Object resolve(JSEnvironment env, Constructor constructor, Object x) {
		// Per spec (PromiseResolve step 2a): "Let xConstructor be ? Get(x,
		// 'constructor')" - the `?` means an abrupt completion here (e.g. a
		// poisoned "constructor" getter throwing) must propagate directly
		// out of PromiseResolve, NOT be silently swallowed with a fallback
		// to normal resolution. Callers of this method are already inside
		// contexts that convert a thrown exception into a promise rejection
		// (e.g. performPromiseThenReactionJob's handler-call try/catch), so
		// letting it propagate here is both spec-correct and safe.
		if (x instanceof BuiltinPromise p) {
			Object xConstructor = env.getAccessor(p).getProperty(p, "constructor", null);
			if (xConstructor == constructor) {
				return p;
			}
		}
		PromiseCapability cap = BuiltinPromise.newPromiseCapability(env, constructor);
		cap.getResolve().call(null, x);
		return cap.getPromise();
	}

	public static Object reject(JSEnvironment env, Constructor constructor, Object r) {
		PromiseCapability cap = BuiltinPromise.newPromiseCapability(env, constructor);
		cap.getReject().call(null, r);
		return cap.getPromise();
	}

	// Convenience methods that use the default Promise constructor - always
	// produces a real BuiltinPromise, so safe to cast/declare as such.
	public static BuiltinPromise resolve(JSEnvironment env, Object x) {
		return (BuiltinPromise)resolve(env, env.getStandardObjects().getConstructor(CLASSNAME), x);
	}

	public static BuiltinPromise reject(JSEnvironment env, Object r) {
		return (BuiltinPromise)reject(env, env.getStandardObjects().getConstructor(CLASSNAME), r);
	}
}
