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
package org.monflabs.galtajs.rt.builtins.standard.function;

import org.monflabs.galtajs.jsonfactory.JSObject;
import org.monflabs.galtajs.rt.JSFunctionContext;
import org.monflabs.galtajs.rt.JSRuntimeContext;
import org.monflabs.galtajs.rt.JSUnitContext;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.builtins.Callable;
import org.monflabs.galtajs.rt.builtins.Constructor;
import org.monflabs.galtajs.rt.builtins.PropertyDescriptor;
import org.monflabs.galtajs.rt.builtins.standard.generator.BuiltinAsyncGeneratorFunctionPrototype;
import org.monflabs.galtajs.rt.builtins.standard.generator.BuiltinAsyncGeneratorPrototype;
import org.monflabs.galtajs.rt.builtins.standard.generator.BuiltinGenerator;
import org.monflabs.galtajs.rt.builtins.standard.generator.BuiltinGeneratorFunctionPrototype;
import org.monflabs.galtajs.rt.builtins.standard.generator.BuiltinGeneratorPrototype;
import org.monflabs.galtajs.rt.transpiler.JSTranspiledFunctionRuntimeContext;
import org.monflabs.galtajs.rt.transpiler.JSTranspiledUnit;
import org.monflabs.galtajs.rt.transpiler.TranspiledFunctionRuntimeContext;
import org.monflabs.util.generators.Generator;
import org.monflabs.util.generators.GeneratorReturnSignal;
import org.monflabs.util.generators.Yielder;


/**
 * Runtime script function.
 */
public class BuiltinFunctionTranspiler extends BuiltinFunction {

	protected int index;
	// Phase C: shared fctContext for elidable functions - allocated once per
	// function and reused across calls, so doExecute doesn't allocate one per
	// invocation. Only observed by the emitted callVoid body, which - being
	// elidable - never reads _this/newTarget/yielder/variable-map from it.
	// See ASTFunction.isTranspiledContextElidable().
	private volatile TranspiledFunctionRuntimeContext elidedFctContext;
	// Function.prototype.toString() source fidelity: the [start,end) character
	// offsets of this function's exact original source text within its unit's
	// source, computed ONCE at transpile time from the AST
	// (ASTFunction.extractOriginalSourceRange()) and baked in as two int
	// literals by the generated subclass's constructor call - see
	// ASTFunction.transpileJavaExpression(). Storing offsets rather than the
	// substring itself avoids duplicating a slice of a source string the
	// JSTranspiledUnit already carries in full (see getOriginalSource()); a
	// value of -1 means no valid range was available at transpile time (mirrors
	// extractOriginalSourceRange()'s own null case).
	private final int srcStart;
	private final int srcEnd;

	public BuiltinFunctionTranspiler(JSRuntimeContext parentCtx, int flags, String name, int length) {
		this(parentCtx, flags, name, length, -1, -1, -1);
	}
	public BuiltinFunctionTranspiler(JSRuntimeContext parentCtx, int flags, String name, int length, int index) {
		this(parentCtx, flags, name, length, index, -1, -1);
	}
	public BuiltinFunctionTranspiler(JSRuntimeContext parentCtx, int flags, String name, int length, int index, int srcStart, int srcEnd) {
		super(parentCtx, name, flags, length);
		this.index = index;
		this.srcStart = srcStart;
		this.srcEnd = srcEnd;

		// Mirrors BuiltinFunctionInterpreter's constructor exactly (see its own
		// comments for the full rationale of each branch) - this used to only
		// ever check isArrow()/isGenerator(), missing two cases: (1) a plain
		// async (non-generator) function/method and a non-generator
		// MethodDefinition have no [[Construct]]/no own "prototype" at all,
		// same as an arrow - previously fell into the "else" branch below,
		// wrongly getting a real prototype object with a constructor back-link;
		// (2) an ASYNC generator's own "prototype" must chain to
		// %AsyncGeneratorPrototype%, not %GeneratorPrototype% - previously
		// matched the plain isGenerator() branch unconditionally, so
		// BuiltinGenerator's constructor (which reads THIS property to derive
		// the actual generator instance's [[Prototype]]) picked up the wrong
		// chain, breaking next()/throw()/return() resolution for every async
		// generator function/method invoked as a plain function value
		// (confirmed via test262's language/expressions/class/async-gen-method
		// dflt-params-* files: "ref(...).next()" - not callable).
		if(isArrow() || (isAsync() && !isGenerator()) || (isMethod() && !isGenerator())) {
			// No own "prototype" property.
		} else if(isGenerator() && isAsync()) {
			JSObject cp = JSObject.createWithPrototype(getEnvironment(), BuiltinAsyncGeneratorPrototype.get(getEnvironment()));
			setOwnProperty(Constructor.PROTOTYPE,cp,PropertyDescriptor.DESC_PROP_PROTOTYPE);
		} else if(isGenerator()) {
			JSObject cp = JSObject.createWithPrototype(getEnvironment(), BuiltinGeneratorPrototype.get(getEnvironment()));
			setOwnProperty(Constructor.PROTOTYPE,cp,PropertyDescriptor.DESC_PROP_PROTOTYPE);
		} else {
			JSObject cp = JSObject.create(getEnvironment());
			cp.setOwnProperty(Constructor.CONSTRUCTOR,this,PropertyDescriptor.DESC_PROP_CONSTRUCTOR);
			setOwnProperty(Constructor.PROTOTYPE,cp,PropertyDescriptor.DESC_PROP_PROTOTYPE);
		}
	}
	
	@Override
	public String toString() {
		return "Function";
	}

	// See `srcStart`/`srcEnd` field doc. Consumed by BuiltinFunctionPrototype's
	// `case toString ->`. Reslices the unit's full source (reached through the
	// runtime context chain: the function's parent context -> its unit context
	// -> the JSTranspiledUnit) with the baked offsets. When the source or a
	// valid range is unavailable at runtime - offsets were -1 at transpile time,
	// the unit didn't retain its source, the parent context isn't a transpiled
	// unit, or the offsets fall out of bounds - return a shape-preserving
	// placeholder rather than null, so toString() still yields a well-formed
	// `function name() { [unavailable] }` instead of the [native code] form.
	public String getOriginalSource() {
		if(srcStart>=0 && srcEnd>=0) {
			JSRuntimeContext parentCtx = getParentContext();
			JSUnitContext mainContext = parentCtx!=null ? parentCtx.getMainContext() : null;
			if(mainContext!=null && mainContext.getScriptUnit() instanceof JSTranspiledUnit unit) {
				String source = unit.getOriginalSourceSlice(srcStart, srcEnd);
				if(source!=null) {
					return source;
				}
			}
		}
		return unavailableSource();
	}

	// Mirrors BuiltinFunctionInterpreter.getDefaultPrototype() exactly - a
	// generator/async function's own [[Prototype]] (NOT its own "prototype"
	// PROPERTY, set above for generators) is %GeneratorFunction.prototype%/
	// %AsyncGeneratorFunction.prototype%/%AsyncFunction.prototype% instead of
	// the base %Function.prototype%, so that Object.getPrototypeOf(fn).prototype
	// reaches %GeneratorPrototype%/%AsyncGeneratorPrototype% (built-ins/
	// GeneratorPrototype/*, built-ins/GeneratorFunction/prototype/*) and
	// Object.prototype.toString's Symbol.toStringTag-based builtinTag is
	// observed on the function itself.
	@Override
	protected Object getDefaultPrototype() {
		if(isGenerator() && isAsync()) {
			return BuiltinAsyncGeneratorFunctionPrototype.get(getEnvironment());
		}
		if(isGenerator()) {
			return BuiltinGeneratorFunctionPrototype.get(getEnvironment());
		}
		if(isAsync()) {
			return BuiltinAsyncFunctionPrototype.get(getEnvironment());
		}
		return super.getDefaultPrototype();
	}

	@Override
	public Object call(Object _this, Object[] parameters, Constructor newTarget) {
		JSRuntimeContext ctx = JSRuntimeContext.get();
		if(isArrow()) {
			// Ignore _this and use the this from the context
			_this = getParentContext().getThis();
			// new.target is likewise inherited LEXICALLY from the arrow's
			// closure (getParentContext(), fixed at creation time), not from
			// `ctx` (JSRuntimeContext.get(), the ambient dynamic caller at
			// THIS call site) - see the matching fix/comment in
			// BuiltinFunctionInterpreter.call() for the full rationale
			// (test262 lexical-new.target-closure-returned.js: an arrow
			// escaping its defining call frame, invoked later with no
			// enclosing constructor at all, must still see the new.target
			// captured where it was DEFINED).
			JSFunctionContext fctContext = getParentContext().getFunctionContext();
			newTarget = fctContext!=null ? fctContext.getNewTarget() : null;
		} else {
	        if(!getParentContext().isStrictMode() && !isForceStrictMode()) {
	        	if(RuntimeUtil.isNullOrUndefined(_this)) {
	        		_this = ctx.getGlobalContext().getGlobalThis();
	        	} else if(RuntimeUtil.isPrimitiveValue(ctx.getEnvironment(), _this)) {
	        		_this = RuntimeUtil.primitiveAsObject(ctx.getEnvironment(), _this);
	        	}
	        }
		}
		if(isGenerator()) {
			Object __this = _this;
			Constructor __newTarget = newTarget;
			// FunctionDeclarationInstantiation (parameter binding + var/function
			// hoisting) must run synchronously here, before the generator object
			// is even returned - a default/destructuring error must reject the
			// call itself. Only the body's statements are genuinely lazy,
			// deferred until the generator is first resumed via next() - see
			// initGeneratorParams()/ASTFunction's codegen split.
			TranspiledFunctionRuntimeContext fctContext = new TranspiledFunctionRuntimeContext(getParentContext(),ctx,this,__this,__newTarget);
			fctContext.with( () -> {
				initGeneratorParams(fctContext, fctContext.getThis(), parameters);
				return null;
			});
			Generator<Object,Object> gen = (Generator<Object,Object>)ctx.getGlobalContext().getExecutor().generator( (yielder) -> {
				return doExecuteGeneratorBody(fctContext,parameters,(Yielder<Object>)yielder);
			});
			return new BuiltinGenerator(getEnvironment(),gen,this);
		}
		if(isAsync()) {
			Object __this = _this;
			Constructor __newTarget = newTarget;
			return ctx.getGlobalContext().getExecutor().runAsyncBody(() -> doExecute(__this,parameters,null,__newTarget));
		} else {
			return doExecute(ctx,_this,parameters,newTarget);
		}
	}
	
	protected Object doExecute(JSRuntimeContext ctx, Object _this, Object[] parameters, Constructor newTarget) {
		if(isContextElidable()) {
			// Fast path: skip both `new TranspiledFunctionRuntimeContext(...)` and
			// the ScopedValue push. Elidability guarantees the emitted body neither
			// observes nor mutates per-call state on fctContext, so a single shared
			// fctContext per function is sufficient - see ASTFunction.isTranspiledContextElidable().
			TranspiledFunctionRuntimeContext fctContext = elidedFctContext;
			if(fctContext==null) {
				fctContext = new TranspiledFunctionRuntimeContext(getParentContext(),ctx,this,RuntimeUtil.UNDEFINED,null);
				elidedFctContext = fctContext;
			}
			return callVoid(fctContext, RuntimeUtil.UNDEFINED, parameters);
		}
		TranspiledFunctionRuntimeContext fctContext = new TranspiledFunctionRuntimeContext(getParentContext(),ctx,this,_this,newTarget);
		return fctContext.with( () -> {
			Object returnValue = callVoid(fctContext, fctContext.getThis(), parameters);
			if(getClassConstructor()!=null && !RuntimeUtil.isObject(getEnvironment(),returnValue)) {
				return fctContext.getThis();
			}
			return returnValue;
		});
	}

	// Phase C: overridden to `return true` in the generated F1 class when the
	// underlying ASTFunction passes isTranspiledContextElidable() - lets the
	// fast path in doExecute skip both fctContext allocation and the
	// ScopedValue push. Default is false, so unless codegen sets it, the
	// original slow path is taken (semantically unchanged).
	protected boolean isContextElidable() {
		return false;
	}
	// Async functions (isAsync(), non-generator) still create fctContext and
	// run the whole callVoid (parameter binding + body) inline here - that's
	// spec-correct for them, since JSAsyncExecutor.runAsyncBody() runs this
	// lambda's synchronous prefix eagerly (blocking the caller up to the
	// first await/completion), unlike the fully-lazy generator() primitive.
	protected Object doExecute(Object _this, Object[] parameters, Yielder<Object> yielder, Constructor newTarget) {
		TranspiledFunctionRuntimeContext fctContext = new TranspiledFunctionRuntimeContext(getParentContext(),JSRuntimeContext.get(),this,_this,newTarget);
		return fctContext.with( () -> {
			fctContext.setYielder(yielder);
			Object returnValue;
			try {
				returnValue = callVoid(fctContext, fctContext.getThis(), parameters);
			} catch(GeneratorReturnSignal grs) {
				// A Generator.prototype.return(value) call forced completion at the
				// currently suspended yield() - treat it as if `return value;` executed there.
				return grs.getValue();
			}
			if(getClassConstructor()!=null && !RuntimeUtil.isObject(getEnvironment(),returnValue)) {
				return fctContext.getThis();
			}
			return returnValue;
		});
	}

	// The (lazy) body half of a generator call - fctContext was already
	// created and eagerly parameter-bound by call()'s isGenerator() branch
	// (via initGeneratorParams()), so this only needs to run callVoid, which
	// reads its locals back via fctContext.getLocals() instead of allocating
	// them - see ASTFunction's codegen split.
	protected Object doExecuteGeneratorBody(TranspiledFunctionRuntimeContext fctContext, Object[] parameters, Yielder<Object> yielder) {
		return fctContext.with( () -> {
			fctContext.setYielder(yielder);
			Object returnValue;
			try {
				returnValue = callVoid(fctContext, fctContext.getThis(), parameters);
			} catch(GeneratorReturnSignal grs) {
				// A Generator.prototype.return(value) call forced completion at the
				// currently suspended yield() - treat it as if `return value;` executed there.
				return grs.getValue();
			}
			if(getClassConstructor()!=null && !RuntimeUtil.isObject(getEnvironment(),returnValue)) {
				return fctContext.getThis();
			}
			return returnValue;
		});
	}

	public Object callVoid(JSTranspiledFunctionRuntimeContext context, Object _this, Object[] parameters) {
		throw new IllegalStateException("Only in transpiled mode");
	}

	// Overridden by generated generator function classes to perform eager
	// parameter binding (FunctionDeclarationInstantiation) - see
	// ASTFunction.transpileFunctionBody's codegen split. No-op by default;
	// only ever invoked from call()'s isGenerator() branch.
	protected void initGeneratorParams(JSTranspiledFunctionRuntimeContext ctx, Object _this, Object[] parameters) {
	}

	// Arity-specific direct-arg dispatch (see Callable.MAX_DIRECT_ARITY):
	// - Caller-side: transpiled call sites emit `call(_this, p0, ..., pN)` when
	//   the call has <= MAX_DIRECT_ARITY positional args and no spread, saving
	//   the per-call `new Object[]{...}` allocation.
	// - Callee-side: BuiltinFunctionTranspiler's Callable arity overloads
	//   (below) route straight into an elidable fast path when possible; the
	//   generated F1 class overrides the matching arity-N callVoid variant to
	//   read parameters directly instead of via initArg(_args, i).
	// - Mismatches (caller-arity != callee-arity) still work correctly: base
	//   callVoidN default (also below) boxes into Object[] and delegates to
	//   the Object[]-form callVoid, which the generated F1 also overrides.

	private TranspiledFunctionRuntimeContext acquireElidedContext() {
		TranspiledFunctionRuntimeContext fctContext = elidedFctContext;
		if(fctContext==null) {
			fctContext = new TranspiledFunctionRuntimeContext(getParentContext(),JSRuntimeContext.get(),this,RuntimeUtil.UNDEFINED,null);
			elidedFctContext = fctContext;
		}
		return fctContext;
	}

	// Arity-N call overrides. When the function is elidable, skip all of the
	// slow-path setup (strict-mode `this` wrapping, generator/async boxing,
	// ScopedValue push, per-call fctContext allocation) and jump directly to
	// callVoidN. Otherwise fall back to the Object[] path, which handles the
	// full semantics.
	@Override
	public Object call(Object _this) {
		if(isContextElidable()) return resolveTailCalls(callVoid0(acquireElidedContext(), RuntimeUtil.UNDEFINED));
		return call(_this, RuntimeUtil.EMPTY_PARAMS);
	}
	@Override
	public Object call(Object _this, Object p1) {
		if(isContextElidable()) return resolveTailCalls(callVoid1(acquireElidedContext(), RuntimeUtil.UNDEFINED, p1));
		return call(_this, new Object[]{p1});
	}
	@Override
	public Object call(Object _this, Object p1, Object p2) {
		if(isContextElidable()) return resolveTailCalls(callVoid2(acquireElidedContext(), RuntimeUtil.UNDEFINED, p1, p2));
		return call(_this, new Object[]{p1,p2});
	}
	@Override
	public Object call(Object _this, Object p1, Object p2, Object p3) {
		if(isContextElidable()) return resolveTailCalls(callVoid3(acquireElidedContext(), RuntimeUtil.UNDEFINED, p1, p2, p3));
		return call(_this, new Object[]{p1,p2,p3});
	}
	@Override
	public Object call(Object _this, Object p1, Object p2, Object p3, Object p4) {
		if(isContextElidable()) return resolveTailCalls(callVoid4(acquireElidedContext(), RuntimeUtil.UNDEFINED, p1, p2, p3, p4));
		return call(_this, new Object[]{p1,p2,p3,p4});
	}
	@Override
	public Object call(Object _this, Object p1, Object p2, Object p3, Object p4, Object p5) {
		if(isContextElidable()) return resolveTailCalls(callVoid5(acquireElidedContext(), RuntimeUtil.UNDEFINED, p1, p2, p3, p4, p5));
		return call(_this, new Object[]{p1,p2,p3,p4,p5});
	}
	@Override
	public Object call(Object _this, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6) {
		if(isContextElidable()) return resolveTailCalls(callVoid6(acquireElidedContext(), RuntimeUtil.UNDEFINED, p1, p2, p3, p4, p5, p6));
		return call(_this, new Object[]{p1,p2,p3,p4,p5,p6});
	}
	@Override
	public Object call(Object _this, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6, Object p7) {
		if(isContextElidable()) return resolveTailCalls(callVoid7(acquireElidedContext(), RuntimeUtil.UNDEFINED, p1, p2, p3, p4, p5, p6, p7));
		return call(_this, new Object[]{p1,p2,p3,p4,p5,p6,p7});
	}
	@Override
	public Object call(Object _this, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6, Object p7, Object p8) {
		if(isContextElidable()) return resolveTailCalls(callVoid8(acquireElidedContext(), RuntimeUtil.UNDEFINED, p1, p2, p3, p4, p5, p6, p7, p8));
		return call(_this, new Object[]{p1,p2,p3,p4,p5,p6,p7,p8});
	}
	@Override
	public Object call(Object _this, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6, Object p7, Object p8, Object p9) {
		if(isContextElidable()) return resolveTailCalls(callVoid9(acquireElidedContext(), RuntimeUtil.UNDEFINED, p1, p2, p3, p4, p5, p6, p7, p8, p9));
		return call(_this, new Object[]{p1,p2,p3,p4,p5,p6,p7,p8,p9});
	}
	@Override
	public Object call(Object _this, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6, Object p7, Object p8, Object p9, Object p10) {
		if(isContextElidable()) return resolveTailCalls(callVoid10(acquireElidedContext(), RuntimeUtil.UNDEFINED, p1, p2, p3, p4, p5, p6, p7, p8, p9, p10));
		return call(_this, new Object[]{p1,p2,p3,p4,p5,p6,p7,p8,p9,p10});
	}

	// Arity-N callVoid stubs. Generated code overrides the ONE that matches the
	// function's declared parameter count (when eligible). The default here
	// boxes into an Object[] and delegates to callVoid(ctx, _this, Object[]) so
	// that arity mismatches between caller and callee still land in the
	// generated body correctly. The bridge array comes from Callable.ArrayPool
	// and is released after callVoid returns - transpiled bodies never retain
	// _args (initArg only reads, createArguments copies), so a scoped borrow
	// is sound.
	public Object callVoid0(JSTranspiledFunctionRuntimeContext ctx, Object _this) {
		return callVoid(ctx, _this, RuntimeUtil.EMPTY_PARAMS);
	}
	public Object callVoid1(JSTranspiledFunctionRuntimeContext ctx, Object _this, Object p1) {
		Object[] a = Callable.ArrayPool.acquire(1);
		a[0] = p1;
		try { return callVoid(ctx, _this, a); } finally { Callable.ArrayPool.release(a); }
	}
	public Object callVoid2(JSTranspiledFunctionRuntimeContext ctx, Object _this, Object p1, Object p2) {
		Object[] a = Callable.ArrayPool.acquire(2);
		a[0] = p1; a[1] = p2;
		try { return callVoid(ctx, _this, a); } finally { Callable.ArrayPool.release(a); }
	}
	public Object callVoid3(JSTranspiledFunctionRuntimeContext ctx, Object _this, Object p1, Object p2, Object p3) {
		Object[] a = Callable.ArrayPool.acquire(3);
		a[0] = p1; a[1] = p2; a[2] = p3;
		try { return callVoid(ctx, _this, a); } finally { Callable.ArrayPool.release(a); }
	}
	public Object callVoid4(JSTranspiledFunctionRuntimeContext ctx, Object _this, Object p1, Object p2, Object p3, Object p4) {
		Object[] a = Callable.ArrayPool.acquire(4);
		a[0] = p1; a[1] = p2; a[2] = p3; a[3] = p4;
		try { return callVoid(ctx, _this, a); } finally { Callable.ArrayPool.release(a); }
	}
	public Object callVoid5(JSTranspiledFunctionRuntimeContext ctx, Object _this, Object p1, Object p2, Object p3, Object p4, Object p5) {
		Object[] a = Callable.ArrayPool.acquire(5);
		a[0] = p1; a[1] = p2; a[2] = p3; a[3] = p4; a[4] = p5;
		try { return callVoid(ctx, _this, a); } finally { Callable.ArrayPool.release(a); }
	}
	public Object callVoid6(JSTranspiledFunctionRuntimeContext ctx, Object _this, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6) {
		Object[] a = Callable.ArrayPool.acquire(6);
		a[0] = p1; a[1] = p2; a[2] = p3; a[3] = p4; a[4] = p5; a[5] = p6;
		try { return callVoid(ctx, _this, a); } finally { Callable.ArrayPool.release(a); }
	}
	public Object callVoid7(JSTranspiledFunctionRuntimeContext ctx, Object _this, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6, Object p7) {
		Object[] a = Callable.ArrayPool.acquire(7);
		a[0] = p1; a[1] = p2; a[2] = p3; a[3] = p4; a[4] = p5; a[5] = p6; a[6] = p7;
		try { return callVoid(ctx, _this, a); } finally { Callable.ArrayPool.release(a); }
	}
	public Object callVoid8(JSTranspiledFunctionRuntimeContext ctx, Object _this, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6, Object p7, Object p8) {
		Object[] a = Callable.ArrayPool.acquire(8);
		a[0] = p1; a[1] = p2; a[2] = p3; a[3] = p4; a[4] = p5; a[5] = p6; a[6] = p7; a[7] = p8;
		try { return callVoid(ctx, _this, a); } finally { Callable.ArrayPool.release(a); }
	}
	public Object callVoid9(JSTranspiledFunctionRuntimeContext ctx, Object _this, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6, Object p7, Object p8, Object p9) {
		Object[] a = Callable.ArrayPool.acquire(9);
		a[0] = p1; a[1] = p2; a[2] = p3; a[3] = p4; a[4] = p5; a[5] = p6; a[6] = p7; a[7] = p8; a[8] = p9;
		try { return callVoid(ctx, _this, a); } finally { Callable.ArrayPool.release(a); }
	}
	public Object callVoid10(JSTranspiledFunctionRuntimeContext ctx, Object _this, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6, Object p7, Object p8, Object p9, Object p10) {
		Object[] a = Callable.ArrayPool.acquire(10);
		a[0] = p1; a[1] = p2; a[2] = p3; a[3] = p4; a[4] = p5; a[5] = p6; a[6] = p7; a[7] = p8; a[8] = p9; a[9] = p10;
		try { return callVoid(ctx, _this, a); } finally { Callable.ArrayPool.release(a); }
	}
}
