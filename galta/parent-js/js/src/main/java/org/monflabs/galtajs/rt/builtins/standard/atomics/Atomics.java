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
package org.monflabs.galtajs.rt.builtins.standard.atomics;

import java.math.BigInteger;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.jsonfactory.JSObject;
import org.monflabs.galtajs.rt.JSRuntimeContext;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.builtins.BaseMethod;
import org.monflabs.galtajs.rt.builtins.NativeObject;
import org.monflabs.galtajs.rt.builtins.PropertyDescriptor;
import org.monflabs.galtajs.rt.builtins.standard.promise.BuiltinPromise;
import org.monflabs.galtajs.rt.builtins.primitives.symbol.Symbol;
import org.monflabs.galtajs.rt.builtins.standard.bigint.BuiltinBigIntConstructor;
import org.monflabs.galtajs.rt.builtins.standard.typedarrays.TypedArray;
import org.monflabs.galtajs.rt.builtins.standard.typedarrays.bigint64.BigtInt64Array;
import org.monflabs.galtajs.rt.builtins.standard.typedarrays.biguint64.BigtUint64Array;
import org.monflabs.galtajs.rt.builtins.standard.typedarrays.float16.Float16Array;
import org.monflabs.galtajs.rt.builtins.standard.typedarrays.float32.Float32Array;
import org.monflabs.galtajs.rt.builtins.standard.typedarrays.float64.Float64Array;
import org.monflabs.galtajs.rt.builtins.standard.typedarrays.int32.Int32Array;
import org.monflabs.galtajs.rt.builtins.standard.typedarrays.sharedarraybuffer.SharedArrayBuffer;
import org.monflabs.galtajs.rt.builtins.standard.typedarrays.uint8clamped.Uint8ClampedArray;

/**
 * Atomics: single-threaded read-modify-write (add/and/compareExchange/
 * exchange/load/or/store/sub/xor - there is only ever one Java thread
 * touching a GIVEN element unless it's an Atomics.wait()-capable agent, see
 * below, so these are trivially atomic with no locking needed). wait()/
 * notify() DO support genuine cross-(real-Java-)thread suspend/wake, via
 * {@link AtomicsWaitRegistry}, but only for a thread that's been marked
 * agent-capable via {@link #setCurrentThreadCanSuspend(boolean)} - normal
 * GaltaJS script execution never is (matching a real browser MAIN thread,
 * which also can't call Atomics.wait()); currently only test262's
 * `$262.agent.start()` (Test262TestLibrary, js-test-test262) marks its
 * spawned agent threads this way - GaltaJS has no JS-facing worker/thread
 * creation API of its own. waitAsync (cross-agent ASYNC wake, resolving a
 * Promise instead of blocking) reuses the same {@link AtomicsWaitRegistry}
 * but blocks on a background thread via {@code asyncFunction} rather than
 * the calling thread - see its own MethodId case and
 * AtomicsWaitRegistry#registerForAsyncWait for the split-phase
 * (register-then-block) design this requires.
 */
public class Atomics extends NativeObject {

	public static final String OBJECTNAME = "Atomics";

	// Per-(real-Java-)thread: may this thread's Atomics.wait() genuinely
	// block? Spec's AgentCanSuspend() - false for ordinary script execution
	// (a ScriptOrModule "main" agent may never suspend, per spec - this is
	// also just the honest answer for GaltaJS, which has no JS-facing
	// worker/thread creation at all), true only for a thread a host hook
	// (currently just test262's $262.agent.start()) has explicitly marked
	// as its own independent agent capable of suspending.
	private static final ThreadLocal<Boolean> AGENT_CAN_SUSPEND = ThreadLocal.withInitial(() -> false);
	public static void setCurrentThreadCanSuspend(boolean canSuspend) {
		AGENT_CAN_SUSPEND.set(canSuspend);
	}

	public Atomics(JSEnvironment env) {
		super(env);
		setOwnProperty(Symbol.TO_STRING_TAG, OBJECTNAME, PropertyDescriptor.DESC_PROP_TOSTRINGTAG);

		setOwnMethod(new Method(env, MethodId.add, 3));
		setOwnMethod(new Method(env, MethodId.and, 3));
		setOwnMethod(new Method(env, MethodId.compareExchange, 4));
		setOwnMethod(new Method(env, MethodId.exchange, 3));
		setOwnMethod(new Method(env, MethodId.isLockFree, 1));
		setOwnMethod(new Method(env, MethodId.load, 2));
		setOwnMethod(new Method(env, MethodId.notify, 3));
		setOwnMethod(new Method(env, MethodId.or, 3));
		setOwnMethod(new Method(env, MethodId.pause, 0));
		setOwnMethod(new Method(env, MethodId.store, 3));
		setOwnMethod(new Method(env, MethodId.sub, 3));
		setOwnMethod(new Method(env, MethodId.wait, 4));
		setOwnMethod(new Method(env, MethodId.waitAsync, 4));
		setOwnMethod(new Method(env, MethodId.xor, 3));
	}

	private static enum MethodId {
		add,
		and,
		compareExchange,
		exchange,
		isLockFree,
		load,
		notify,
		or,
		pause,
		store,
		sub,
		wait,
		waitAsync,
		xor,
	}

	// ValidateIntegerTypedArray: typedArray must be a non-clamped, non-float
	// integer TypedArray (Int8/Uint8/Int16/Uint16/Int32/Uint32/BigInt64/
	// BigUint64Array) - both regular ArrayBuffer- and SharedArrayBuffer-backed
	// views are accepted (per the 2021 "Atomics on non-shared memory" update).
	// `write` mirrors ValidateTypedArray's own accessMode: true for every
	// read-modify-write/compareExchange/store operation, false for load/
	// notify/wait/waitAsync - an immutable buffer only rejects the write
	// ones (ValidateTypedArray step: "If accessMode is ~write~ and
	// IsImmutableBuffer(...) is true, throw a TypeError"), and must be
	// checked here, before index/value are coerced (ToIndex/ToNumber can
	// have observable valueOf() side effects that must never run first).
	private static TypedArray validateIntegerTypedArray(JSEnvironment env, Object obj, boolean write) {
		if(!(obj instanceof TypedArray ta)) {
			throw RuntimeUtil.typeError("{0} is not a TypedArray object", RuntimeUtil.objectTypeName(env,obj));
		}
		if(ta instanceof Uint8ClampedArray || ta instanceof Float16Array || ta instanceof Float32Array || ta instanceof Float64Array) {
			throw RuntimeUtil.typeError("Atomics operation not permitted on this TypedArray");
		}
		if(ta.getArrayBuffer().isDetached()) {
			throw RuntimeUtil.typeError("Cannot perform Atomics operation on a detached ArrayBuffer");
		}
		if(write && ta.getArrayBuffer().isImmutable()) {
			throw RuntimeUtil.typeError("Cannot perform Atomics write operation on an immutable ArrayBuffer");
		}
		return ta;
	}

	private static long validateAtomicAccess(JSEnvironment env, TypedArray ta, Object indexArg) {
		long length = ta.getLength();
		long accessIndex = RuntimeUtil.toIndex(env, indexArg);
		if(accessIndex>=length) {
			throw RuntimeUtil.rangeError("Index {0} is out of bounds", accessIndex);
		}
		return accessIndex;
	}

	private static void checkNotDetached(TypedArray ta) {
		if(ta.getArrayBuffer().isDetached()) {
			throw RuntimeUtil.typeError("Cannot perform Atomics operation on a detached ArrayBuffer");
		}
	}

	// ValidateIntegerTypedArray(typedArray, waitable=true) - wait()/notify()
	// only accept Int32Array/BigInt64Array (unlike the other Atomics
	// operations, which accept any non-clamped integer type).
	private static TypedArray validateWaitableTypedArray(JSEnvironment env, Object obj) {
		TypedArray ta = validateIntegerTypedArray(env, obj, false);
		if(!(ta instanceof Int32Array) && !(ta instanceof BigtInt64Array)) {
			throw RuntimeUtil.typeError("Atomics.wait/notify requires an Int32Array or BigInt64Array");
		}
		return ta;
	}

	// ToIntegerOrInfinity, for notify()'s `count` argument.
	private static double toIntegerOrInfinity(JSEnvironment env, Object v) {
		double d = RuntimeUtil.toNumber(env, v).doubleValue();
		if(Double.isNaN(d)) {
			return 0;
		}
		if(Double.isInfinite(d)) {
			return d;
		}
		return d<0 ? -Math.floor(-d) : Math.floor(d);
	}

	private static boolean isBigIntArray(TypedArray ta) {
		return ta instanceof BigtInt64Array || ta instanceof BigtUint64Array;
	}

	// ToBigInt(value): ToPrimitive(value, number) then dispatch.
	private static BigInteger toBigIntValue(JSEnvironment env, Object value) {
		Object prim = RuntimeUtil.toPrimitive(env, value, RuntimeUtil.HINT.NUMBER);
		return BuiltinBigIntConstructor.toBigInt(env, prim);
	}

	// Masks to the element's own bit width so a signed/unsigned representation
	// difference (e.g. Uint32Array.get() returning a non-negative Long vs.
	// RuntimeUtil.toInt32() returning a signed int for the same bit pattern)
	// never causes a false compareExchange mismatch.
	private static long maskToWidth(long v, int bytesPerElement) {
		int bits = bytesPerElement*8;
		if(bits>=64) {
			return v;
		}
		return v & ((1L<<bits)-1);
	}

	private interface LongOp {
		long apply(long oldValue, long operand);
	}
	private interface BigOp {
		BigInteger apply(BigInteger oldValue, BigInteger operand);
	}

	// Coarse (whole-engine, not per-buffer) real-thread mutual exclusion -
	// every Atomics read-modify-write/compareExchange/load/store operation
	// synchronizes on this SAME lock {@link AtomicsWaitRegistry} itself
	// uses for its own "register as waiter, THEN check the value" step
	// (see its doc comment) - deliberately the SAME monitor, not a
	// per-buffer one, so a store()/add()/etc. on one thread and a wait()'s
	// initial value check on another are ALWAYS properly ordered by Java's
	// monitor happens-before guarantee, never just "both eventually see
	// consistent memory" by accident. (The actual long BLOCKING part of a
	// wait() uses a separate, per-waiter lock - see AtomicsWaitRegistry.
	// await() - so a sleeping waiter never blocks other threads' ordinary
	// Atomics operations.) Needed for correctness the moment more than one
	// REAL thread can touch the same buffer (currently only test262's
	// $262.agent - see Atomics' class doc) - without this, two concurrent
	// Atomics.add() calls on the SAME location can race (both read the
	// same "old" value, both write "old+1", losing an update - confirmed
	// empirically before this fix existed). A non-shared ArrayBuffer's
	// bytes are, by construction, never actually touched by more than one
	// thread, so this lock is always uncontended (cheap) for that far more
	// common case.
	private static Object lockFor(TypedArray ta) {
		return AtomicsWaitRegistry.get();
	}

	private static Number atomicReadModifyWrite(JSEnvironment env, Object obj, Object indexArg, Object valueArg, LongOp longOp, BigOp bigOp) {
		TypedArray ta = validateIntegerTypedArray(env, obj, true);
		long accessIndex = validateAtomicAccess(env, ta, indexArg);
		if(isBigIntArray(ta)) {
			BigInteger operand = toBigIntValue(env, valueArg);
			checkNotDetached(ta);
			synchronized(lockFor(ta)) {
				BigInteger oldValue = (BigInteger)ta.get(accessIndex);
				ta.set(accessIndex, bigOp.apply(oldValue, operand));
				return oldValue;
			}
		} else {
			long operand = RuntimeUtil.toInt32(env, valueArg);
			checkNotDetached(ta);
			synchronized(lockFor(ta)) {
				Number oldRaw = ta.get(accessIndex);
				ta.set(accessIndex, longOp.apply(oldRaw.longValue(), operand));
				return oldRaw;
			}
		}
	}

	private final static class Method extends BaseMethod {
		private MethodId methodId;

		private Method(JSEnvironment env, MethodId methodId, int length) {
			super(env, methodId.name(), length);
			this.methodId = methodId;
		}

		@Override
		protected Object invoke(final Object obj, final Object[] args) {
			switch(methodId) {
				case add -> {
					return atomicReadModifyWrite(getEnvironment(), param(args,0,RuntimeUtil.UNDEFINED), param(args,1,RuntimeUtil.UNDEFINED), param(args,2,RuntimeUtil.UNDEFINED),
						(a,b) -> a+b, (a,b) -> a.add(b));
				}
				case and -> {
					return atomicReadModifyWrite(getEnvironment(), param(args,0,RuntimeUtil.UNDEFINED), param(args,1,RuntimeUtil.UNDEFINED), param(args,2,RuntimeUtil.UNDEFINED),
						(a,b) -> a&b, (a,b) -> a.and(b));
				}
				case or -> {
					return atomicReadModifyWrite(getEnvironment(), param(args,0,RuntimeUtil.UNDEFINED), param(args,1,RuntimeUtil.UNDEFINED), param(args,2,RuntimeUtil.UNDEFINED),
						(a,b) -> a|b, (a,b) -> a.or(b));
				}
				case sub -> {
					return atomicReadModifyWrite(getEnvironment(), param(args,0,RuntimeUtil.UNDEFINED), param(args,1,RuntimeUtil.UNDEFINED), param(args,2,RuntimeUtil.UNDEFINED),
						(a,b) -> a-b, (a,b) -> a.subtract(b));
				}
				case xor -> {
					return atomicReadModifyWrite(getEnvironment(), param(args,0,RuntimeUtil.UNDEFINED), param(args,1,RuntimeUtil.UNDEFINED), param(args,2,RuntimeUtil.UNDEFINED),
						(a,b) -> a^b, (a,b) -> a.xor(b));
				}
				case exchange -> {
					return atomicReadModifyWrite(getEnvironment(), param(args,0,RuntimeUtil.UNDEFINED), param(args,1,RuntimeUtil.UNDEFINED), param(args,2,RuntimeUtil.UNDEFINED),
						(a,b) -> b, (a,b) -> b);
				}
				case compareExchange -> {
					JSEnvironment env = getEnvironment();
					TypedArray ta = validateIntegerTypedArray(env, param(args,0,RuntimeUtil.UNDEFINED), true);
					long accessIndex = validateAtomicAccess(env, ta, param(args,1,RuntimeUtil.UNDEFINED));
					Object expectedArg = param(args,2,RuntimeUtil.UNDEFINED);
					Object replacementArg = param(args,3,RuntimeUtil.UNDEFINED);
					if(isBigIntArray(ta)) {
						BigInteger expected = toBigIntValue(env, expectedArg);
						BigInteger replacement = toBigIntValue(env, replacementArg);
						checkNotDetached(ta);
						synchronized(lockFor(ta)) {
							BigInteger oldValue = (BigInteger)ta.get(accessIndex);
							// Compare on the raw 64-bit pattern (BigInteger.longValue()
							// truncates to it) rather than BigInteger value equality -
							// e.g. BigUint64Array's oldValue is an unsigned BigInteger
							// (0..2^64-1) but a plain ToBigInt(-5n) stays exactly -5,
							// even though storing -5n into that array wraps to 2^64-5.
							if(oldValue.longValue()==expected.longValue()) {
								ta.set(accessIndex, replacement);
							}
							return oldValue;
						}
					} else {
						long expected = RuntimeUtil.toInt32(env, expectedArg);
						long replacement = RuntimeUtil.toInt32(env, replacementArg);
						checkNotDetached(ta);
						synchronized(lockFor(ta)) {
							Number oldRaw = ta.get(accessIndex);
							int bpe = ta.getTypedArrayConstructor().getBytesPerElement();
							if(maskToWidth(oldRaw.longValue(),bpe)==maskToWidth(expected,bpe)) {
								ta.set(accessIndex, replacement);
							}
							return oldRaw;
						}
					}
				}
				case load -> {
					JSEnvironment env = getEnvironment();
					TypedArray ta = validateIntegerTypedArray(env, param(args,0,RuntimeUtil.UNDEFINED), false);
					long accessIndex = validateAtomicAccess(env, ta, param(args,1,RuntimeUtil.UNDEFINED));
					synchronized(lockFor(ta)) {
						return ta.get(accessIndex);
					}
				}
				case notify -> {
					JSEnvironment env = getEnvironment();
					TypedArray ta = validateWaitableTypedArray(env, param(args,0,RuntimeUtil.UNDEFINED));
					long accessIndex = validateAtomicAccess(env, ta, param(args,1,RuntimeUtil.UNDEFINED));
					Object countArg = param(args,2,RuntimeUtil.UNDEFINED);
					double c = Double.POSITIVE_INFINITY;
					if(countArg!=RuntimeUtil.UNDEFINED) {
						// ToIntegerOrInfinity(count), c = max(intCount, 0).
						c = Math.max(toIntegerOrInfinity(env, countArg), 0);
					}
					if(!(ta.getArrayBuffer() instanceof SharedArrayBuffer)) {
						return 0;
					}
					int bpe = ta.getTypedArrayConstructor().getBytesPerElement();
					long byteOffset = ta.getByteOffset() + accessIndex*bpe;
					long count = Double.isInfinite(c) ? Long.MAX_VALUE : (long)c;
					long woken = AtomicsWaitRegistry.get().notify(ta.getArrayBuffer(), byteOffset, count);
					return (double)woken;
				}
				case wait -> {
					JSEnvironment env = getEnvironment();
					TypedArray ta = validateWaitableTypedArray(env, param(args,0,RuntimeUtil.UNDEFINED));
					if(!(ta.getArrayBuffer() instanceof SharedArrayBuffer)) {
						throw RuntimeUtil.typeError("Atomics.wait requires a SharedArrayBuffer-backed view");
					}
					long accessIndex = validateAtomicAccess(env, ta, param(args,1,RuntimeUtil.UNDEFINED));
					// `value`/`timeout` are conceptually required per spec's
					// own parameter list, but JS call semantics never throw
					// for a MISSING trailing argument - it's simply
					// `undefined` (ToInt32(undefined)=0, ToNumber(undefined)
					// =NaN=>+Infinity timeout below) - confirmed needed via
					// atomicsHelper.js's own safeBroadcast(), which calls
					// `Atomics.wait(temp, 0, value)` with NO 4th argument at
					// all.
					Object valueArg = param(args,2,RuntimeUtil.UNDEFINED);
					boolean isBigInt = isBigIntArray(ta);
					BigInteger expectedBig = isBigInt ? toBigIntValue(env, valueArg) : null;
					long expectedLong = isBigInt ? 0 : RuntimeUtil.toInt32(env, valueArg);
					Object timeoutArg = param(args,3,RuntimeUtil.UNDEFINED);
					double q = RuntimeUtil.toNumber(env, timeoutArg).doubleValue();
					double t = Double.isNaN(q) ? Double.POSITIVE_INFINITY : Math.max(q, 0);

					// AgentCanSuspend(): true only for a real Java thread a host
					// hook (currently just test262's $262.agent.start()) has
					// explicitly marked as its own independent agent - ordinary
					// script execution honestly refuses to suspend, matching a
					// real browser main thread's behavior, rather than silently
					// returning a fake result for a wait that never genuinely
					// happened.
					if(!AGENT_CAN_SUSPEND.get()) {
						throw RuntimeUtil.typeError("Atomics.wait cannot suspend this agent");
					}
					checkNotDetached(ta);
					int bpe = ta.getTypedArrayConstructor().getBytesPerElement();
					long byteOffset = ta.getByteOffset() + accessIndex*bpe;
					// Waiters are keyed by the buffer object, not its byte[]: a growable
					// SharedArrayBuffer swaps its byte[] when it grows
					Object bytes = ta.getArrayBuffer();
					AtomicsWaitRegistry.WaitResult r = AtomicsWaitRegistry.get().await(bytes, byteOffset, () -> {
						if(ta.getArrayBuffer().isDetached()) {
							// A detached buffer can no longer hold the expected
							// value - treat as "changed", i.e. don't block.
							return false;
						}
						Number current = ta.get(accessIndex);
						if(isBigInt) {
							return current instanceof BigInteger big && big.longValue()==expectedBig.longValue();
						}
						return maskToWidth(current.longValue(),bpe)==maskToWidth(expectedLong,bpe);
					}, t);
					return switch(r) {
						case OK -> "ok";
						case NOT_EQUAL -> "not-equal";
						case TIMED_OUT -> "timed-out";
					};
				}
				case waitAsync -> {
					JSEnvironment env = getEnvironment();
					TypedArray ta = validateWaitableTypedArray(env, param(args,0,RuntimeUtil.UNDEFINED));
					if(!(ta.getArrayBuffer() instanceof SharedArrayBuffer)) {
						throw RuntimeUtil.typeError("Atomics.waitAsync requires a SharedArrayBuffer-backed view");
					}
					long accessIndex = validateAtomicAccess(env, ta, param(args,1,RuntimeUtil.UNDEFINED));
					Object valueArg = param(args,2,RuntimeUtil.UNDEFINED);
					boolean isBigInt = isBigIntArray(ta);
					BigInteger expectedBig = isBigInt ? toBigIntValue(env, valueArg) : null;
					long expectedLong = isBigInt ? 0 : RuntimeUtil.toInt32(env, valueArg);
					Object timeoutArg = param(args,3,RuntimeUtil.UNDEFINED);
					double q = RuntimeUtil.toNumber(env, timeoutArg).doubleValue();
					double t = Double.isNaN(q) ? Double.POSITIVE_INFINITY : Math.max(q, 0);

					checkNotDetached(ta);
					int bpe = ta.getTypedArrayConstructor().getBytesPerElement();
					long byteOffset = ta.getByteOffset() + accessIndex*bpe;
					// Waiters are keyed by the buffer object, not its byte[]: a growable
					// SharedArrayBuffer swaps its byte[] when it grows
					Object bytes = ta.getArrayBuffer();

					// Same "still expected" check as Atomics.wait() - but here it
					// may run AGAIN later, on a background thread, if we end up
					// actually registering (see below).
					java.util.function.BooleanSupplier stillExpected = () -> {
						if(ta.getArrayBuffer().isDetached()) {
							return false;
						}
						Number current = ta.get(accessIndex);
						if(isBigInt) {
							return current instanceof BigInteger big && big.longValue()==expectedBig.longValue();
						}
						return maskToWidth(current.longValue(),bpe)==maskToWidth(expectedLong,bpe);
					};

					AtomicsWaitRegistry.WaitResult[] shortCircuit = new AtomicsWaitRegistry.WaitResult[1];
					AtomicsWaitRegistry.Waiter waiter;
					synchronized(lockFor(ta)) {
						waiter = AtomicsWaitRegistry.get().registerForAsyncWait(bytes, byteOffset, stillExpected, t, shortCircuit);
					}

					JSObject result = JSObject.create(env);
					if(waiter==null) {
						// Observed synchronously by the CALLING thread - no
						// Promise at all, per spec/test262 (a value mismatch or a
						// zero-length timeout can never actually need waiting).
						result.setOwnProperty("async", false);
						result.setOwnProperty("value", shortCircuit[0]==AtomicsWaitRegistry.WaitResult.NOT_EQUAL ? "not-equal" : "timed-out");
						return result;
					}
					// A real (possibly infinite) wait is needed - block on a
					// background thread via asyncFunction (never the calling
					// thread), reusing the exact same DoWait blocking/cleanup
					// logic Atomics.wait() itself uses. The calling agent need
					// not be suspend-capable for this - that's the whole point of
					// waitAsync existing.
					JSRuntimeContext ctx = JSRuntimeContext.get();
					BuiltinPromise promise = ctx.getGlobalContext().getExecutor().asyncFunction(() -> {
						AtomicsWaitRegistry.WaitResult r = AtomicsWaitRegistry.get().blockUntilWokenOrTimeout(waiter, bytes, byteOffset, t);
						return switch(r) {
							case OK -> "ok";
							case NOT_EQUAL -> "not-equal";
							case TIMED_OUT -> "timed-out";
						};
					});
					result.setOwnProperty("async", true);
					result.setOwnProperty("value", promise);
					return result;
				}
				case store -> {
					JSEnvironment env = getEnvironment();
					TypedArray ta = validateIntegerTypedArray(env, param(args,0,RuntimeUtil.UNDEFINED), true);
					long accessIndex = validateAtomicAccess(env, ta, param(args,1,RuntimeUtil.UNDEFINED));
					Object valueArg = param(args,2,RuntimeUtil.UNDEFINED);
					if(isBigIntArray(ta)) {
						BigInteger v = toBigIntValue(env, valueArg);
						checkNotDetached(ta);
						ta.set(accessIndex, v);
						return v;
					} else {
						int v = RuntimeUtil.toInt32(env, valueArg);
						checkNotDetached(ta);
						ta.set(accessIndex, v);
						return v;
					}
				}
				case isLockFree -> {
					// n==4 is always lock-free; treat 1/2/8 as lock-free too
					// (matches common real-engine behavior) - anything else false.
					int n = RuntimeUtil.toInt32(getEnvironment(), param(args,0,RuntimeUtil.UNDEFINED));
					return n==1 || n==2 || n==4 || n==8;
				}
				case pause -> {
					Object n = param(args,0,RuntimeUtil.UNDEFINED);
					if(n!=RuntimeUtil.UNDEFINED) {
						if(!(n instanceof Number num) || (n instanceof java.math.BigInteger) || (n instanceof java.math.BigDecimal)) {
							throw RuntimeUtil.typeError("Atomics.pause argument must be a Number");
						}
						double d = num.doubleValue();
						if(Double.isNaN(d) || Double.isInfinite(d) || Math.floor(d)!=d || d<0) {
							throw RuntimeUtil.typeError("Atomics.pause argument must be a non-negative integral Number");
						}
					}
					return RuntimeUtil.UNDEFINED;
				}
				default -> {
					throw new IllegalStateException(); // Should never be here
				}
			}
		}
	}
}
