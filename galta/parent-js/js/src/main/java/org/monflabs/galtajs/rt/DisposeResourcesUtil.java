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
package org.monflabs.galtajs.rt;

import java.util.List;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.rt.builtins.Callable;
import org.monflabs.galtajs.rt.builtins.errors.SuppressedError;
import org.monflabs.galtajs.rt.builtins.primitives.symbol.Symbol;

/**
 * DisposeResources ( disposeCapability, completion ) - explicit resource
 * management (`using`/`await using`). Called by whatever construct owns a
 * scope's disposal boundary (ASTBlock, a function body, a C-style for
 * loop's head) when that scope exits, in either direction.
 *
 * Usage: catch the scope's own Throwable (if any), call dispose(scope,
 * caught), then either rethrow the returned Throwable (if non-null) or
 * proceed normally.
 */
public class DisposeResourcesUtil {

	private DisposeResourcesUtil() {
	}

	// Returns the Throwable to (re)throw, or null if disposal completed
	// without introducing a new error - in which case `pending` (possibly
	// null itself) is the right thing for the caller to still act on
	// unchanged. Resources are disposed in REVERSE registration order; a
	// disposal that throws while a completion (the original `pending`, or
	// an earlier disposal's own throw) is already in flight wraps both as a
	// SuppressedError({error: new, suppressed: old}), matching spec exactly.
	public static Throwable dispose(JSRuntimeContext scope, Throwable pending) {
		return dispose(scope, scope.getOwnDisposableResources(), pending);
	}

	// Same DisposeResources algorithm, but for a resource list that ISN'T
	// the calling scope's own `using`-declaration stack - specifically,
	// DisposableStack/AsyncDisposableStack's own internal [[DisposeCapability]]
	// list. `activeContext` is still needed for the environment (error
	// construction) and, for an async resource, awaiting the dispose call's
	// result - but it's the CURRENTLY RUNNING context at the call site
	// (JSContext.get()), not an owner of `resources` itself.
	public static Throwable dispose(JSRuntimeContext activeContext, List<DisposableResource> resources, Throwable pending) {
		if(resources.isEmpty()) {
			return pending;
		}
		JSEnvironment env = activeContext.getEnvironment();
		Object currentJsError = pending!=null ? JSRuntimeException.exceptionObject(pending) : null;
		boolean hasError = pending!=null;
		boolean disposalThrew = false;
		// DisposeResources step 4: even when every resource on this boundary
		// turns out to be a null/undefined `await using` value (disposeMethod
		// undefined - never actually calls/awaits anything), the disposal as
		// a whole must still cost the SAME one extra microtask tick a real
		// async disposal's own Await(...) would - see the Await(undefined)
		// call below. needsAwait/hasAwaited track exactly the two step-3
		// branches this depends on (confirmed via built-ins/AsyncDisposableStack/
		// prototype/disposeAsync/explicit-await-for-{null,undefined}.js: a
		// disposeAsync() with only such entries was completing a full tick
		// too early relative to sibling promise chains queued around it).
		boolean needsAwait = false;
		boolean hasAwaited = false;
		for(int i=resources.size()-1; i>=0; i--) {
			DisposableResource r = resources.get(i);
			if(r.disposeMethod()==null) {
				// A null/undefined resource value - no-op entry, nothing to
				// call - but still flags needsAwait for an async hint.
				if(r.isAsync()) {
					needsAwait = true;
				}
				continue;
			}
			try {
				Object result = r.disposeMethod().call(r.value(), RuntimeUtil.EMPTY_PARAMS);
				if(r.isAsync()) {
					hasAwaited = true;
					// An `await using`/async-dispose resource is only valid
					// syntax inside an async context - if THAT context is
					// also a generator (getYielder()!=null), it must be an
					// ASYNC generator (a sync generator can't contain
					// `await`), so disposal must suspend the same
					// Exchanger-safe way a literal `await` in that body
					// would (see AwaitYieldSignal's own doc comment) rather
					// than block the generator's own worker thread with the
					// ordinary await_() - confirmed via a real deadlock in
					// language/statements/await-using/initializer-Symbol.
					// asyncDispose-called-at-end-of-asyncgeneratorbody.js
					// (disposal running at async-generator-body completion,
					// on that body's own worker thread).
					JSFunctionContext fc = activeContext.getFunctionContext();
					if(fc!=null && fc.getYielder()!=null) {
						RuntimeUtil.awaitInGenerator_(activeContext, result);
					} else {
						RuntimeUtil.await_(activeContext, result);
					}
				}
			} catch(Throwable disposeEx) {
				RuntimeUtil.rethrowIfUncatchable(disposeEx);
				if(r.isAsync()) {
					hasAwaited = true;
				}
				disposalThrew = true;
				Object disposeJsError = JSRuntimeException.exceptionObject(disposeEx);
				if(hasError) {
					currentJsError = new SuppressedError(env, disposeJsError, currentJsError);
				} else {
					currentJsError = disposeJsError;
					hasError = true;
				}
			}
		}
		if(needsAwait && !hasAwaited) {
			JSFunctionContext fc = activeContext.getFunctionContext();
			if(fc!=null && fc.getYielder()!=null) {
				RuntimeUtil.awaitInGenerator_(activeContext, RuntimeUtil.UNDEFINED);
			} else {
				RuntimeUtil.await_(activeContext, RuntimeUtil.UNDEFINED);
			}
		}
		if(!disposalThrew) {
			return pending;
		}
		return JSRuntimeException.asJavascriptException(null, currentJsError);
	}

	// GetDisposeMethod(V, hint): for an async hint (`await using`, or
	// DisposableStack.prototype.use() called on an AsyncDisposableStack),
	// try @@asyncDispose first, falling back to @@dispose only when
	// @@asyncDispose is genuinely absent (not merely present-but-uncallable,
	// which throws immediately via getDisposeMethodBySymbol() below) - then
	// CreateDisposableResource step 1.b.iii: an object value with no
	// resolvable dispose method at all is a TypeError. Shared by
	// ASTVariableDeclUsing (`using`/`await using` declarations) and
	// DisposableStack/AsyncDisposableStack's own `use()` method.
	public static Callable resolveDisposeMethod(JSEnvironment env, Object value, boolean isAsync) {
		if(isAsync) {
			Callable m = getDisposeMethodBySymbol(env, value, Symbol.ASYNC_DISPOSE);
			if(m!=null) {
				return m;
			}
		}
		Callable m = getDisposeMethodBySymbol(env, value, Symbol.DISPOSE);
		if(m==null) {
			throw RuntimeUtil.typeError("Property [Symbol.dispose] is not a function, {0}", RuntimeUtil.objectTypeName(env,value));
		}
		return m;
	}

	// GetMethod(V, P): null if V[P] is null/undefined (absent), the resolved
	// Callable otherwise - throws TypeError immediately if V[P] is present
	// but not callable (distinct from "absent", per spec).
	private static Callable getDisposeMethodBySymbol(JSEnvironment env, Object value, Symbol symbol) {
		Object func = RuntimeUtil.getProperty(env, value, symbol);
		if(RuntimeUtil.isNullOrUndefined(func)) {
			return null;
		}
		if(!(func instanceof Callable c)) {
			throw RuntimeUtil.typeError("Property is not a function, {0}", RuntimeUtil.objectTypeName(env,func));
		}
		return c;
	}
}
