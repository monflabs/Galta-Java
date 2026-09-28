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
import org.monflabs.galtajs.node.ASTNode;
import org.monflabs.galtajs.node.ASTVarContainer.VariableDef;
import org.monflabs.galtajs.node.ASTVarContainer.VariableDefContainer;
import org.monflabs.galtajs.node.control.ASTFunction;
import org.monflabs.galtajs.node.literal.ASTArrayLiteral;
import org.monflabs.galtajs.rt.DisposeResourcesUtil;
import org.monflabs.galtajs.rt.JSFunctionContext;
import org.monflabs.galtajs.rt.JSResult;
import org.monflabs.galtajs.rt.JSRuntimeContext;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.builtins.Constructor;
import org.monflabs.galtajs.rt.builtins.PropertyDescriptor;
import org.monflabs.galtajs.rt.builtins.standard.generator.BuiltinAsyncGeneratorFunctionPrototype;
import org.monflabs.galtajs.rt.builtins.standard.generator.BuiltinAsyncGeneratorPrototype;
import org.monflabs.galtajs.rt.builtins.standard.generator.BuiltinGenerator;
import org.monflabs.galtajs.rt.builtins.standard.generator.BuiltinGeneratorFunctionPrototype;
import org.monflabs.galtajs.rt.builtins.standard.generator.BuiltinGeneratorPrototype;
import org.monflabs.galtajs.rt.interpreter.InterpretedFunctionBodyRuntimeContext;
import org.monflabs.galtajs.rt.interpreter.InterpretedFunctionRuntimeContext;
import org.monflabs.galtajs.rt.interpreter.InterpretedUnitRuntimeContext.Signal;
import org.monflabs.galtajs.rt.interpreter.JSInterpretedRuntimeContext;
import org.monflabs.galtajs.rt.interpreter.JSInterpretedRuntimeContext.VAR_TYPE;
import org.monflabs.galtajs.rt.interpreter.VariableMap;
import org.monflabs.util.generators.Generator;
import org.monflabs.util.generators.GeneratorReturnSignal;
import org.monflabs.util.generators.Yielder;


/**
 * Runtime script function.
 */
public class BuiltinFunctionInterpreter extends BuiltinFunction {
	
	// Interpreted function
	private ASTFunction functionNode;
	private VariableDefContainer variables;
	// Whether this function body directly declares any using/await-using
	// resource at its top level - computed once here (this object is created
	// once per function definition, reused across every call), not per-call,
	// so executeStatements()'s disposal wrapping only pays for a try/catch
	// when actually needed.
	private final boolean hasUsingDeclarations;

	public BuiltinFunctionInterpreter(JSRuntimeContext parentCtx, ASTFunction functionNode, VariableDefContainer variables, int length) {
		super(parentCtx,functionNode.getFunctionName(),functionNode.getModifiers(),length);
		this.functionNode = functionNode;
		this.variables = variables;
		boolean hasUsing = false;
		if(variables!=null) {
			for(VariableDef v: variables) {
				if(v.getVarType()==VAR_TYPE.USING) {
					hasUsing = true;
					break;
				}
			}
		}
		this.hasUsingDeclarations = hasUsing;

		if(isArrow() || (isAsync() && !isGenerator()) || (isMethod() && !isGenerator())) {
			// Arrow functions, a plain/method async variant (NOT an async
			// GENERATOR - see the branch below for that), and a non-generator
			// MethodDefinition (plain method, getter, setter - concise method
			// syntax in an object literal or class body) have no [[Construct]]
			// and no own "prototype" property (a SYNC generator method still
			// needs one, chained to %GeneratorPrototype%, handled by the
			// branch below - confirmed via test262 language/statements/class/
			// definition/methods.js and built-ins/AsyncFunction/
			// instance-prototype-property.js).
		} else if(isGenerator() && isAsync()) {
			// An async generator function's "prototype" is a plain object
			// with no own properties whose own [[Prototype]] is
			// %AsyncGeneratorPrototype% - mirrors the sync generator branch
			// below exactly. See BuiltinAsyncGeneratorPrototype's own javadoc
			// for why this can't just fall into the "no own prototype" branch
			// above the way a plain async function does: a real async
			// generator instance's [[Prototype]] (via BuiltinGenerator's
			// GetPrototypeFromConstructor-style constructor) depends on this
			// property actually being an object.
			JSObject cp = JSObject.createWithPrototype(getEnvironment(), BuiltinAsyncGeneratorPrototype.get(getEnvironment()));
			setOwnProperty(Constructor.PROTOTYPE,cp,PropertyDescriptor.DESC_FUNCTION_PROTOTYPE);
		} else if(isGenerator()) {
			// A generator function's "prototype" is a plain object with no own
			// properties (no constructor back-link) whose own [[Prototype]] is
			// %GeneratorPrototype% - this is what generator instances ultimately
			// chain through to reach next()/throw()/return(). See BuiltinGenerator.
			JSObject cp = JSObject.createWithPrototype(getEnvironment(), BuiltinGeneratorPrototype.get(getEnvironment()));
			setOwnProperty(Constructor.PROTOTYPE,cp,PropertyDescriptor.DESC_FUNCTION_PROTOTYPE);
		} else {
			JSObject cp = JSObject.create(getEnvironment());
			cp.setOwnProperty(Constructor.CONSTRUCTOR,this,PropertyDescriptor.DESC_PROP_CONSTRUCTOR);
			setOwnProperty(Constructor.PROTOTYPE,cp,PropertyDescriptor.DESC_FUNCTION_PROTOTYPE);
		}
	}
	
	public ASTFunction getFunctionNode() {
		return functionNode;
	}

	// Function.prototype.toString() source fidelity in interpreted mode: the
	// exact original source text, sliced on demand from the AST + its containing
	// program's source (ASTFunction.extractOriginalSource()). Falls back to the
	// shape-preserving placeholder when unavailable (source not tracked), same
	// as the transpiled path's BuiltinFunctionTranspiler.getOriginalSource().
	public String getOriginalSource() {
		String source = functionNode.extractOriginalSource();
		return source!=null ? source : unavailableSource();
	}

	// A generator/async function's own [[Prototype]] (NOT its own
	// "prototype" PROPERTY, set above for generators - this is the hidden
	// link Object.getPrototypeOf(fn) reports) is %GeneratorFunction.prototype%/
	// %AsyncFunction.prototype% instead of the base %Function.prototype%,
	// so that Object.prototype.toString's Symbol.toStringTag-based builtinTag
	// ("GeneratorFunction"/"AsyncFunction") is observed on the function
	// itself (confirmed via
	// Object/prototype/toString/symbol-tag-generators-builtin.js/
	// proxy-function.js/-async.js - the latter two via a Proxy wrapping the
	// function, which walks the SAME prototype chain through [[Get]]).
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
	protected Object call(Object _this, Object[] parameters, Constructor newTarget) {
		if(isArrow()) {
			// Ignore _this - an arrow has no `this` binding of its own, it always
			// defers to the lexically enclosing scope's `this`. That resolution
			// must stay LAZY (done by InterpretedFunctionRuntimeContext.getThis()
			// delegating to the parent context on every read), not snapshotted
			// eagerly here at call time: eagerly calling getParentContext().getThis()
			// is itself a validating read that throws "'this' cannot be called
			// before 'super' in a constructor" if the enclosing derived-class
			// constructor hasn't called super() yet - which wrongly fires just from
			// ENTERING the arrow (e.g. `finally { f(); }` where `f = () => super()`),
			// before the arrow's own body (which may itself call super(), and thus
			// legitimately initialize `this`) has even run. See test262
			// derived-class-return-override-{catch,finally,for-of}*-arrow.js.
			//
			// new.target must resolve the same way: LEXICALLY, from the arrow's
			// closure (getParentContext(), fixed at the arrow's own creation
			// time), never from JSRuntimeContext.get() (the AMBIENT/dynamic
			// "currently executing" context at this call site). The two only
			// coincide by accident when an arrow happens to be invoked from
			// within the same call frame that created it; once the arrow
			// escapes that frame (e.g. stored on `this` and called later, as
			// in test262 lexical-new.target-closure-returned.js: an arrow
			// created inside `new F()` but invoked afterwards via a plain
			// `f.af()` call with no enclosing constructor at all), the dynamic
			// lookup silently resolves to whatever unrelated context happens
			// to be executing then (usually null/undefined) instead of the
			// `new.target` captured at the arrow's definition site.
			JSFunctionContext fctContext = getParentContext().getFunctionContext();
			newTarget = fctContext!= null ? fctContext.getNewTarget() : null;
		} else {
	        if(!getParentContext().isStrictMode() && !isForceStrictMode()) {
	        	if(RuntimeUtil.isNullOrUndefined(_this)) {
	        		_this = getParentContext().getGlobalContext().getGlobalThis(); // GlobalThis
	        	} else if(RuntimeUtil.isPrimitiveValue(getEnvironment(), _this)) {
	        		_this = RuntimeUtil.primitiveAsObject(getEnvironment(), _this);
	        	}
	        }
		}
		if(isGenerator()) {
			return callGenerator(_this,parameters,newTarget);
		}
		if(isAsync()) {
			Object __this = _this;
			Constructor __newTarget = newTarget;
			return JSRuntimeContext.get().getGlobalContext().getExecutor().runAsyncBody(() -> doExecute(__this,parameters,null,__newTarget));
		} else {
			return doExecute(_this,parameters,null,newTarget);
		}
	}

	private Object doExecute(Object _this, Object[] parameters, Yielder<Object> yielder, Constructor newTarget) {
		try {
			ASTNode[] statements = functionNode.getStatements();
			if(statements!=null) {
				JSResult result = new JSResult();

				InterpretedFunctionRuntimeContext fctContext = new InterpretedFunctionRuntimeContext((JSInterpretedRuntimeContext)getParentContext(), JSRuntimeContext.get(), this, _this, parameters, newTarget);
				return fctContext.with( () -> {
					fctContext.setYielder(yielder);
					JSInterpretedRuntimeContext bodyContext = bindParametersAndVars(fctContext, parameters, result);
					return executeStatements(bodyContext, statements, result);
				});
			}
			return RuntimeUtil.UNDEFINED;
		} catch(GeneratorReturnSignal grs) {
			// A Generator.prototype.return(value) call forced completion at the
			// currently suspended yield() - treat it as if `return value;` executed there.
			return grs.getValue();
		} catch(Exception t) {
			// We systematically create a new exception that will be stacked, so we'll have a stack trace of
			// the Script calls as well.
			throw RuntimeUtil.wrap(t);
		}
	}

	// FunctionDeclarationInstantiation: binds parameters (running any destructuring
	// defaults) and hoists "var" declarations. Shared by doExecute() and
	// callGenerator(), which - unlike doExecute() - must run this synchronously at
	// call time, before the generator's body (which is genuinely lazy) even starts.
	private JSInterpretedRuntimeContext bindParametersAndVars(InterpretedFunctionRuntimeContext fctContext, Object[] parameters, JSResult result) {
		// Phase 2c: bind every function-frame binding into its declaration-order
		// slot so ASTIdentifier's fast path (see evaluateValue) can read by
		// index without hashing. The slot array was already allocated by
		// InterpretedFunctionRuntimeContext's constructor. When a binding's
		// VariableDef is known (funcName, hoisted var/function), we go through
		// initVariableInSlot; the parameter factory does the same via
		// createInSlot(). Anything not backed by a VariableDef (never expected
		// for a function frame, but defensively kept) still gets a hash-only
		// entry via createVariable.
		VariableMap vm = fctContext.getVariableMap();
		boolean useSlots = vm!=null && vm.hasSlots();

		// We first create the function, which can be overridden later. A
		// named function EXPRESSION's own self-reference is FUNCTION_SELF
		// (spec-immutable), not PREDECLARED (an ordinary declaration's own
		// name, or a parameter) - use whatever type ASTFunction.init()
		// actually registered instead of assuming PREDECLARED. A method/
		// getter/setter's "name" is a PropertyName, never a real self-
		// reference binding (ASTFunction.init() deliberately never
		// registers one for it, matching how e.g. `{ get ownKeys() {...} }`
		// must NOT shadow an outer-scope `ownKeys` variable) - this must
		// NOT fall back to creating one anyway via the VAR_TYPE.PREDECLARED
		// default below, which previously did so unconditionally for any
		// non-null funcName regardless of whether a static declaration
		// backs it (confirmed via a direct repro and
		// Object/keys/proxy-keys.js/property-traps-order-with-proxied-array.js).
		// A non-block-nested DECLARATION (isStatement() && !isBlockNested())
		// is excluded the same way - its own name is NOT a function-local
		// self-reference binding at all (see ASTFunction.init()'s own doc
		// comment); without this exclusion, this call unconditionally
		// created a stray PREDECLARED local shadow copy anyway (since
		// functionNode.getOwnVariable(funcName) is now null for that case,
		// same as the method case), silently reintroducing the exact
		// shadowing bug that fix was meant to remove (confirmed via a
		// direct repro: `function fn(){ fn=2; } fn(); fn` must read back 2,
		// not the original function). A BLOCK-NESTED declaration keeps the
		// funcNameVar-driven PREDECLARED path below (ASTFunction.init()
		// still registers one for that case - see its own doc comment for
		// why).
		String funcName = (functionNode.isMethod() || (functionNode.isStatement() && !functionNode.isBlockNested())) ? null : functionNode.getFunctionName();
		if(funcName!=null) {
			VariableDef funcNameVar = functionNode.getOwnVariable(funcName);
			VAR_TYPE funcNameType = funcNameVar!=null ? funcNameVar.getVarType() : VAR_TYPE.PREDECLARED;
			if(useSlots) {
				int fnSlot = functionNode.getFuncNameSlot();
				if(fnSlot>=0) {
					vm.initVariableInSlot(funcName, fnSlot, funcNameType);
					vm.setSlot(fnSlot, this);
				} else {
					fctContext.createVariable(funcName, this, funcNameType);
				}
			} else {
				fctContext.createVariable(funcName, this, funcNameType);
			}
		}

		// Function parameters - fast path: simple param list (no destructuring,
		// no defaults, no rest) with slot-backed frame. Skips the per-param
		// hash lookup + BiConsumer allocation for the overwhelmingly common
		// call shape. Position-order writes handle f(a,b,a) last-wins naturally
		// (dup names share the same VariableDef, hence the same slot) and a
		// param that shadows the function name naturally overwrites its slot.
		int[] paramSlots = functionNode.getSimpleParamSlots();
		if(useSlots && paramSlots!=null) {
			String[] names = functionNode.getSimpleParameterNames();
			int n = paramSlots.length;
			int argc = parameters!=null ? parameters.length : 0;
			for(int i=0; i<n; i++) {
				int slot = paramSlots[i];
				Object val = i<argc ? parameters[i] : RuntimeUtil.UNDEFINED;
				if(slot>=0) {
					vm.initVariableInSlot(names[i], slot, VAR_TYPE.PREDECLARED);
					vm.setSlot(slot, val);
				} else {
					fctContext.createVariable(names[i], val, VAR_TYPE.PREDECLARED);
				}
			}
		} else {
			// Pre-populate every parameter name with the TDZ sentinel before any
			// default-value initializer runs. Parameters bind strictly left-to-
			// right (declare()/ASTArrayLiteral.assign() below); per spec, a
			// default expression referencing its own or a not-yet-bound later
			// parameter must throw ReferenceError. Without a placeholder entry,
			// such a reference finds no local binding yet and silently falls
			// through to an outer-scope binding of the same name instead of
			// hitting checkTDZ (confirmed via test262's dflt-params-ref-self.js,
			// replicated across every function-like form).
			ASTArrayLiteral params = functionNode.getParameters();
			if(params!=null) {
				params.forEachVarName((name)->{
					VariableDef pv = useSlots ? functionNode.getOwnVariable(name) : null;
					if(pv!=null) {
						vm.initVariableInSlot(name, pv.getJavaVariableIndex(), VAR_TYPE.PREDECLARED);
						vm.setSlot(pv.getJavaVariableIndex(), RuntimeUtil.TDZ);
					} else {
						fctContext.createVariable(name, RuntimeUtil.TDZ, VAR_TYPE.PREDECLARED);
					}
				});
			}
			functionNode.declare(fctContext,
					(k, v)->{
						if(k.equals(funcName)) {
							fctContext.setVariable(k,v); // Override the function name
						} else {
							VariableDef pv = useSlots ? functionNode.getOwnVariable(k) : null;
							if(pv!=null) {
								vm.initVariableInSlot(k, pv.getJavaVariableIndex(), VAR_TYPE.PREDECLARED);
								vm.setSlot(pv.getJavaVariableIndex(), v);
							} else {
								fctContext.createVariable(k, v, VAR_TYPE.PREDECLARED);
							}
						}
					}, parameters, result);
		}

		// Body's own frame: FunctionDeclarationInstantiation (9.2.10) steps
		// 26-27 - when the parameter list "hasParameterExpressions" (anything
		// beyond a flat list of plain identifiers - isSimpleParameterList()
		// is used as the proxy for this spec concept throughout the
		// interpreter, e.g. InterpretedFunctionRuntimeContext's own mapped-
		// arguments-object decision above), the body's own var/let/const/
		// function bindings live in a SEPARATE declarative environment - a
		// child of the parameter frame (fctContext) built above, not merged
		// into it. This is what lets a closure created while evaluating a
		// default-parameter expression (necessarily against fctContext,
		// since this body frame doesn't exist yet at that point) correctly
		// fail to observe anything the body declares afterwards - see
		// InterpretedFunctionBodyRuntimeContext's own javadoc for the full
		// rationale. The common case (simple parameter list, no defaults/
		// destructuring/rest) never takes this branch: bodyContext stays
		// fctContext and every line below behaves exactly as before this
		// split existed (bodyVars==vm, bodyUseSlots==useSlots).
		//
		// NOTE: a body-level var/function declaration that shares its name
		// with a parameter is NOT split out here - ASTVarContainer.
		// _addVarDeclaration() already merges such a declaration into the
		// parameter's own (PREDECLARED-typed) VariableDef at parse time, so
		// it never reaches this loop as a separate hoisted-var entry; it
		// stays a single binding living in the outer/parameter frame, same
		// as before this change. See docs/GaltaJS/KnownGaps.md for why this
		// narrower residual case is out of scope here.
		boolean hasParameterExpressions = !functionNode.isSimpleParameterList();
		JSInterpretedRuntimeContext bodyContext = hasParameterExpressions
				? new InterpretedFunctionBodyRuntimeContext(fctContext)
				: fctContext;

		// "var"/let/const declared variables
		if(variables!=null) {
			VariableMap bodyVars = bodyContext.getVariableMap(true);
			boolean bodyUseSlots = bodyContext==fctContext && useSlots;
			for(VariableDef v: variables) {
				// In case of a function, don't override with undefined -
				// EXCEPT when this entry is the function EXPRESSION's own
				// name, genuinely re-declared as a body-level `var` (per
				// ASTVarContainer's FUNCTION_SELF exemption, that merges
				// into this SAME VariableDef at parse time, overwriting its
				// type from FUNCTION_SELF to VAR - so by the time this runs,
				// `v.getVarType()` here already reads VAR, not FUNCTION_SELF;
				// the ONLY way to still recognize this case is by name,
				// against the funcName pre-populated above). Per spec these
				// are genuinely separate environments - the function
				// expression's own self-reference lives in an OUTER scope
				// from the body's var/let/const declarations - so a
				// body-level `var` of that name is really an independent
				// binding that must still be (re-)initialized to undefined,
				// not skipped just because the self-reference already
				// occupies this name (confirmed via test262 expressions/
				// call/scope-var-open.js: `var f = function n(){ var n; ...
				// }` must observe the body's own `n` as undefined).
				boolean isFuncSelfVarOverride = funcName!=null && funcName.equals(v.getName()) && v.getVarType()==VAR_TYPE.VAR;
				if(isFuncSelfVarOverride || bodyVars.getEntry(v.getName())==null) {
					VAR_TYPE t = v.getVarType();
					if(t.isHoisted()) {
						if(bodyUseSlots) {
							int slot = v.getJavaVariableIndex();
							bodyVars.initVariableInSlot(v.getName(), slot, t);
							// initVariableInSlot() only registers the name/
							// type/index mapping - it does NOT touch the
							// underlying slot array value, unlike every other
							// caller here (parameters/funcName always pair it
							// with an explicit setSlot()). Normally that's
							// fine (a fresh function-frame slot array starts
							// all-undefined already) - EXCEPT for
							// isFuncSelfVarOverride, where this slot was
							// already written by the funcName pre-population
							// above (the function-self value) and must be
							// explicitly reset here.
							if(isFuncSelfVarOverride) {
								bodyVars.setSlot(slot, RuntimeUtil.UNDEFINED);
							}
						} else {
							bodyContext.createVariable(v.getName(), RuntimeUtil.UNDEFINED, t);
						}
					} else if(t==VAR_TYPE.LET || t==VAR_TYPE.CONST || t==VAR_TYPE.USING) {
						// Top-level let/const/using in the function body
						// shares this same body frame (no separate block
						// context wraps the body's own statement list) -
						// pre-populate the Temporal Dead Zone placeholder
						// here, same as ASTBlock does for nested blocks.
						bodyContext.createVariable(v.getName(), RuntimeUtil.TDZ, t);
					}
				}
			}
		}
		return bodyContext;
	}

	private Object executeStatements(JSInterpretedRuntimeContext bodyContext, ASTNode[] statements, JSResult result) {
		if(!hasUsingDeclarations) {
			return executeStatementsCore(bodyContext, statements, result);
		}
		// RS: FunctionBody evaluation - DisposeResources(funcEnv.[[DisposeCapability]],
		// result) runs on EVERY exit path (normal return, or an exception) -
		// only paid for when this function body actually declares a
		// using/await-using resource at its own top level (see
		// hasUsingDeclarations, computed once at function-definition time -
		// same reasoning ASTBlock's own disposal wrapping uses). Resources are
		// registered against whichever context actually executes the `using`
		// statement (bodyContext - the same frame as every other body-level
		// declaration, see bindParametersAndVars), so disposal must target
		// that same context too.
		Object v = null;
		Throwable pending = null;
		try {
			v = executeStatementsCore(bodyContext, statements, result);
		} catch(Throwable t) {
			pending = t;
		}
		Throwable toThrow = DisposeResourcesUtil.dispose(bodyContext, pending);
		if(toThrow!=null) {
			if(toThrow instanceof RuntimeException re) {
				throw re;
			}
			if(toThrow instanceof Error e) {
				throw e;
			}
			throw new RuntimeException(toThrow);
		}
		return v;
	}

	@SuppressWarnings("incomplete-switch")
	private Object executeStatementsCore(JSInterpretedRuntimeContext bodyContext, ASTNode[] statements, JSResult result) {
		int count = statements.length;
		for(int i=0; i<count; i++) {
			Signal s = statements[i].evaluate(bodyContext,result);
			if(s!=Signal.NONE) {
				switch(s.getType()) {
					case RETURN -> {
						Object v = result.getValue();
						if(getClassConstructor()!=null && !RuntimeUtil.isObject(getEnvironment(),v)) {
							// Per [[Construct]] (10.2.2 step 10): a non-object
							// return value from a BASE constructor is always
							// ignored (fall back to `this`, regardless of
							// whether it was undefined or some other
							// primitive) - but a DERIVED constructor may only
							// silently fall back to `this` when the value is
							// genuinely undefined (a bare `return;` or
							// `return undefined;`); returning any OTHER
							// defined primitive (null/number/string/boolean/
							// symbol/bigint) must throw TypeError instead
							// (confirmed via test262 language/statements/
							// class/subclass/derived-class-return-override-
							// with-*.js).
							if(getClassConstructor().getSuperClass()!=null && v!=RuntimeUtil.UNDEFINED) {
								throw RuntimeUtil.typeError("Derived constructor may only return object or undefined");
							}
							return bodyContext.getThis();
						}
						return v;
					}
					case BREAK -> {
						result.setValue(RuntimeUtil.UNDEFINED);
						throw RuntimeUtil.syntaxError("Unsyntactic break");
					}
					case CONTINUE -> {
						result.setValue(RuntimeUtil.UNDEFINED);
						throw RuntimeUtil.syntaxError("Unsyntactic continue");
					}
				}
			}
		}

		if(getClassConstructor()!=null) {
			return bodyContext.getThis();
		}
		return RuntimeUtil.UNDEFINED;
	}

	// Generator calls must perform FunctionDeclarationInstantiation (parameter
	// binding + var hoisting) synchronously at call time, per spec - a default
	// parameter expression that throws must reject the call itself, before any
	// generator object is returned. Only the body's statements are genuinely lazy,
	// deferred until the generator is first resumed via next().
	private Object callGenerator(Object _this, Object[] parameters, Constructor newTarget) {
		try {
			ASTNode[] statements = functionNode.getStatements();
			JSResult result = new JSResult();
			InterpretedFunctionRuntimeContext fctContext = new InterpretedFunctionRuntimeContext((JSInterpretedRuntimeContext)getParentContext(), JSRuntimeContext.get(), this, _this, parameters, newTarget);

			// bindParametersAndVars() must run synchronously here (before the
			// generator object is even returned), but the body statements it
			// resolves an execution context FOR only run later, lazily, inside
			// the generator's own resumption lambda below - a separate
			// fctContext.with() call. Stash the resolved body context (==
			// fctContext itself in the common case, or the new split body
			// frame when hasParameterExpressions - see bindParametersAndVars)
			// in a one-element holder so the later lambda can see it.
			JSInterpretedRuntimeContext[] bodyContextHolder = new JSInterpretedRuntimeContext[1];
			fctContext.with( () -> {
				bodyContextHolder[0] = bindParametersAndVars(fctContext, parameters, result);
				return null;
			});

			Generator<Object,Object> gen = JSRuntimeContext.get().getGlobalContext().getExecutor().generator( (yielder) -> {
				try {
					return fctContext.with( () -> {
						fctContext.setYielder(yielder);
						if(statements==null) {
							return RuntimeUtil.UNDEFINED;
						}
						return executeStatements(bodyContextHolder[0], statements, result);
					});
				} catch(GeneratorReturnSignal grs) {
					return grs.getValue();
				} catch(Exception t) {
					throw RuntimeUtil.wrap(t);
				}
			});
			return new BuiltinGenerator(getEnvironment(),gen,this);
		} catch(Exception t) {
			throw RuntimeUtil.wrap(t);
		}
	}
}
