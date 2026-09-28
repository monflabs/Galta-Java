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

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.builtins.Constructor;
import org.monflabs.galtajs.rt.builtins.standard.function.BuiltinFunction;
import org.monflabs.galtajs.rt.builtins.standard.iterator.BuiltinIterator;
import org.monflabs.galtajs.rt.builtins.standard.iterator.IteratorEx;
import org.monflabs.util.generators.Generator;

/**
 *
 */
public class BuiltinGenerator extends BuiltinIterator implements IteratorEx<Object> {

	private boolean consumedReturnedValue;
	private final boolean async;

	// Per-instance [[AsyncGeneratorQueue]]/[[AsyncGeneratorState]] (spec) -
	// only ever populated for async generators (see
	// BuiltinAsyncGeneratorPrototype's drive()/pumpQueue()). A next()/
	// throw()/return() call arriving while a PREVIOUS call on this SAME
	// generator is still suspended mid-`await` (not yet back to a real
	// yield/completion) must be queued rather than immediately resumed -
	// resuming right away would race GeneratorImpl's own Exchanger-paired
	// resume protocol directly, incorrectly delivering the new call's own
	// argument as the resume value for the STILL-PENDING earlier await.
	private final java.util.ArrayDeque<Runnable> asyncResumeQueue = new java.util.ArrayDeque<>();
	private boolean asyncResuming;

	// Spec's [[AsyncGeneratorState]], collapsed to the two facts
	// BuiltinAsyncGeneratorPrototype actually needs to distinguish: has the
	// body ever been resumed at all (false = "suspendedStart"), and has it
	// reached a terminal outcome (true = "completed", whether via a normal
	// return, an injected return(), or an uncaught exception). "suspendedYield"
	// and "executing" are otherwise indistinguishable here and don't need to
	// be - see BuiltinAsyncGeneratorPrototype.drive()'s own doc comment for
	// why only suspendedStart/completed need special AsyncGeneratorAwaitReturn
	// handling (a return() while suspendedYield/executing still resumes the
	// body normally, same as today).
	private boolean asyncStarted;
	private boolean asyncCompleted;

	java.util.ArrayDeque<Runnable> getAsyncResumeQueue() {
		return asyncResumeQueue;
	}
	boolean isAsyncResuming() {
		return asyncResuming;
	}
	void setAsyncResuming(boolean asyncResuming) {
		this.asyncResuming = asyncResuming;
	}
	boolean isAsyncStarted() {
		return asyncStarted;
	}
	void setAsyncStarted(boolean asyncStarted) {
		this.asyncStarted = asyncStarted;
	}
	boolean isAsyncCompleted() {
		return asyncCompleted;
	}
	void setAsyncCompleted(boolean asyncCompleted) {
		this.asyncCompleted = asyncCompleted;
	}

	public BuiltinGenerator(JSEnvironment env, Generator<Object,Object> gen) {
		super(env,gen,"Generator");
		this.async = false;
	}

	// GetPrototypeFromConstructor(generatorFunction, "%GeneratorPrototype%"/
	// "%AsyncGeneratorPrototype%"): a generator instance's [[Prototype]] is
	// the generator function's own "prototype" property, falling back to
	// the intrinsic default (getDefaultPrototype() below, which must pick
	// the ASYNC-aware default too - see language/expressions/async-generator/
	// default-proto.js) when that property isn't (or is no longer, by the
	// time of the call) an object - e.g. `g.prototype = null; g()`.
	public BuiltinGenerator(JSEnvironment env, Generator<Object,Object> gen, BuiltinFunction generatorFunction) {
		super(env,gen,generatorFunction.isAsync() ? "AsyncGenerator" : "Generator");
		this.async = generatorFunction.isAsync();
		Object proto = generatorFunction.getProperty(Constructor.PROTOTYPE,RuntimeUtil.NOT_AVAILABLE);
		if(RuntimeUtil.isObject(env,proto)) {
			setPrototype(proto);
		}
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public Generator<Object,Object> getGenerator() {
		return (Generator)getIterator();
	}

	public boolean isAsync() {
		return async;
	}

	@Override
	protected Object getDefaultPrototype() {
		if(async) {
			return BuiltinAsyncGeneratorPrototype.get(getEnvironment());
		}
		return BuiltinGeneratorPrototype.get(getEnvironment());
	}

	// A `.return(x)` call that lands the generator in (or keeps it in) the
	// completed state is a genuinely NEW completion event with its own
	// value `x` - distinct from a later redundant `.next()` call re-
	// observing the SAME already-reported completion (which correctly
	// degrades to `undefined` via the consumedReturnedValue latch below).
	// Called right before getDoneValue() so that read isn't short-circuited
	// by a stale latch left over from an earlier natural completion.
	public void resetConsumedReturnedValue() {
		consumedReturnedValue = false;
	}

	@Override
	public Object getDoneValue() {
		if(consumedReturnedValue) {
			return RuntimeUtil.UNDEFINED;
		}
		consumedReturnedValue = true;
		// If the generator body terminated via an uncaught exception rather
		// than a normal return, GeneratorImpl's returnValue field is never
		// assigned and stays raw Java null - normalize that to JS undefined.
		Object returnValue = getGenerator().getReturnValue();
		return returnValue==null ? RuntimeUtil.UNDEFINED : returnValue;
	}
}
