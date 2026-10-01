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
package org.monflabs.galtajs.rt.builtins.standard;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.util.BitSet;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.JSParseException;
import org.monflabs.galtajs.library.GlobalLibrary;
import org.monflabs.galtajs.modules.JSInterpretedUnit;
import org.monflabs.galtajs.node.ASTIdentifier;
import org.monflabs.galtajs.node.ASTNode;
import org.monflabs.galtajs.node.call.ASTCall;
import org.monflabs.galtajs.node.clazz.ASTClassField;
import org.monflabs.galtajs.node.control.ASTFunction;
import org.monflabs.galtajs.node.control.IContextRootContainer;
import org.monflabs.galtajs.rt.JSEvalRuntimeContext;
import org.monflabs.galtajs.rt.JSGlobalContext;
import org.monflabs.galtajs.rt.JSFunctionContext;
import org.monflabs.galtajs.rt.JSRuntimeContext;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.builtins.BaseMethod;
import org.monflabs.galtajs.rt.builtins.Callable;
import org.monflabs.galtajs.rt.builtins.PropertyDescriptor;
import org.monflabs.galtajs.rt.builtins.WithClosure;
import org.monflabs.galtajs.rt.builtins.primitives.number.BuiltinNumberConstructor;
import org.monflabs.galtajs.rt.builtins.primitives.symbol.Symbol;
import org.monflabs.galtajs.rt.builtins.standard.JSON.JSON;
import org.monflabs.galtajs.rt.builtins.standard.atomics.Atomics;
import org.monflabs.galtajs.rt.builtins.standard.console.Console;
import org.monflabs.galtajs.rt.builtins.standard.function.BuiltinFunction;
import org.monflabs.galtajs.rt.builtins.standard.global.StandardObjects;
import org.monflabs.galtajs.rt.builtins.standard.math.MathObject;
import org.monflabs.galtajs.rt.builtins.standard.performance.Performance;
import org.monflabs.galtajs.rt.builtins.standard.reflect.Reflect;
import org.monflabs.galtajs.rt.builtins.standard.temporal.TemporalNamespace;
import org.monflabs.galtajs.rt.interpreter.InterpretedFunctionRuntimeContext;
import org.monflabs.galtajs.rt.interpreter.InterpretedRuntimeContext;
import org.monflabs.galtajs.rt.interpreter.JSInterpretedRuntimeContext;
import org.monflabs.galtajs.rt.interpreter.VariableMap;
import org.monflabs.galtajs.rt.transpiler.JSTranspiledRuntimeContext;
import org.monflabs.galtajs.rt.transpiler.VarAccessor;
import org.monflabs.util.StringUtil;


/**
 * Standard JS like library.
 */
public class StandardLibrary extends GlobalLibrary {

	public StandardLibrary() {
	}
	
	@Override
	public void configureStandardObjects(JSEnvironment env, StandardObjects standardObjects) {
		standardObjects.setOwnProperty(Console.OBJECTNAME, new Console(env));
		standardObjects.setOwnProperty(Performance.OBJECTNAME, new Performance(env));
		// JSON/Reflect/Math are spec'd as {writable:true, enumerable:false,
		// configurable:true} global data properties.
		standardObjects.setOwnProperty(JSON.OBJECTNAME, new JSON(env), PropertyDescriptor.DESC_METHOD);
		standardObjects.setOwnProperty(Reflect.OBJECTNAME, new Reflect(env), PropertyDescriptor.DESC_METHOD);
		standardObjects.setOwnProperty(MathObject.OBJECTNAME, new MathObject(env), PropertyDescriptor.DESC_METHOD);
		standardObjects.setOwnProperty(Atomics.OBJECTNAME, new Atomics(env), PropertyDescriptor.DESC_METHOD);
		standardObjects.setOwnProperty(TemporalNamespace.OBJECTNAME, new TemporalNamespace(env), PropertyDescriptor.DESC_METHOD);
		
		// Global functions
		BuiltinNumberConstructor ctor = (BuiltinNumberConstructor)standardObjects.getConstructor(BuiltinNumberConstructor.CLASSNAME);
		standardObjects.setOwnMethod((BaseMethod)ctor.getProperty("parseInt", null));
		standardObjects.setOwnMethod((BaseMethod)ctor.getProperty("parseFloat", null));

		// Note: isFinite/isNaN are different from Number.isFinite/isNaN
		// we cannot just delegate to these methods
		//
		// These are registered via setOwnMethod() (not setOwnProperty()) so
		// they get the spec-correct {writable:true, configurable:true,
		// enumerable:false} shape for a built-in global function - the same
		// descriptor already used for parseInt/parseFloat above.
		standardObjects.setOwnMethod(new GlobalFunction(env,"isNaN", FunctionIndex.isNaN,1));
		standardObjects.setOwnMethod(new GlobalFunction(env,"isFinite", FunctionIndex.isFinite,1));
		standardObjects.setOwnMethod(new GlobalFunction(env,"encodeURI", FunctionIndex.encodeURI,1));
		standardObjects.setOwnMethod(new GlobalFunction(env,"encodeURIComponent", FunctionIndex.encodeURIComponent,1));
		standardObjects.setOwnMethod(new GlobalFunction(env,"decodeURI", FunctionIndex.decodeURI,1));
		standardObjects.setOwnMethod(new GlobalFunction(env,"decodeURIComponent", FunctionIndex.decodeURIComponent,1));

		standardObjects.setOwnMethod(new GlobalFunction(env,"eval", FunctionIndex.eval,1));

		if(env.isDeprecatedApis()) {
			standardObjects.setOwnMethod(new GlobalFunction(env,"escape", FunctionIndex.escape,1));
			standardObjects.setOwnMethod(new GlobalFunction(env,"unescape", FunctionIndex.unescape,1));
		}
	}

	
	static enum FunctionIndex {
		eval,
		isNaN,
		isFinite,
		encodeURI,
		encodeURIComponent,
		decodeURI,
		decodeURIComponent,
		// Deprecated
		escape,
		unescape
	}
	
	private static abstract class BaseEvalContext extends InterpretedRuntimeContext implements JSEvalRuntimeContext {
		private boolean forceStrictMode;
		// Set once, right before executeForEval() (mirroring forceStrictMode's
		// own setter pattern), from the SAME static-AST fact already computed
		// for either caller mode - see the local variable of this name in the
		// eval() built-in below for its own doc. Read back by ASTProgram.
		// evaluate()'s sub-case-B collision check (via the JSEvalRuntimeContext
		// interface method this overrides) at declaration time.
		private boolean callerInAnyParameterExpressionScope;
		BaseEvalContext(JSRuntimeContext parent) {
			super(parent);
		}
		@Override
		public JSRuntimeContext getVarDeclContext() {
			return this;
		}
		@Override
		public void setForceStrictMode(boolean forceStrictMode) {
			this.forceStrictMode = forceStrictMode;
		}
		public void setCallerInAnyParameterExpressionScope(boolean callerInAnyParameterExpressionScope) {
			this.callerInAnyParameterExpressionScope = callerInAnyParameterExpressionScope;
		}
		@Override
		public boolean isCallerInAnyParameterExpressionScope() {
			return callerInAnyParameterExpressionScope;
		}
		@Override
		public boolean isStrictMode() {
			// forceStrictMode alone is already the complete, spec-correct
			// answer: it's set once (executeForEval(), from JSInterpretedUnit
			// .isForceStrictMode() = program.isGenuinelyStrictMode()) to this
			// eval's own directive prologue OR'd with - for a DIRECT eval
			// only - the calling context's genuine strictness, folded in at
			// PARSE time via ASTProgram's callerForcedStrict (the
			// createEvalScript "forceStrict" argument, itself computed from
			// the caller's nearest IContextRootContainer.isGenuinelyStrictMode()
			// / the transpiler's bundled flag - see the eval() built-in
			// above). That already matches PerformEval's strictEval formula
			// exactly. Falling back to getSurroundingContext().isStrictMode()
			// additionally leaked the ENCLOSING GLOBAL CONTEXT's OWN
			// strictness (the outermost script/module's directive, wholly
			// unrelated once any eval nesting is involved) into every eval
			// context - wrongly forcing an INDIRECT eval of non-strict text
			// to run strict whenever the outermost script/module happened to
			// have "use strict", even though indirect eval must never
			// inherit ANY caller strictness. See test262
			// language/eval-code/indirect/always-non-strict.js.
			return forceStrictMode;
		}
		public abstract JSRuntimeContext getSurroundingContext();
		@Override
		public VarAccessor createVariable(String varName, Object value, VAR_TYPE type) {
			if(type==VAR_TYPE.VAR || type==VAR_TYPE.FUNCTION || type==VAR_TYPE.AUTO) {
				// TODO: when can this happen for the types above?
				// If it is already declared in this scope, use it - must be a
				// LOCAL-only check (getLocalVariableEntry), not the inherited
				// getVariableEntry()'s full ancestor walk: this eval context's
				// parent chain IS the real calling scope, so a full walk would
				// incorrectly find (and mutate) an unrelated same-named
				// binding declared outside this eval entirely.
				//
				// A STRICT eval additionally must not match
				// getLocalVariableEntry()'s own resolveOwnIdentifierEntry()
				// fallback either: for a TRANSPILED caller, TranspiledEvalContext
				// overrides that hook to resolve the eval call site's static
				// free-variable VarAccessor bundle (needed so ordinary reads of
				// outer identifiers - e.g. "assert" - work inside eval'd text),
				// and an outer var this eval itself is about to (re)declare is
				// always present in that bundle too. EvalDeclarationInstantiation's
				// strict path (18.2.1.3 step 8) always creates a FRESH binding in
				// the eval's own VariableEnvironment regardless of what any outer
				// scope already has - it never even consults the outer scope - so
				// "already declared in this scope" must check ONLY this context's
				// own true local VariableMap here (a repeat `var x` within the
				// SAME eval body still correctly reuses that same local slot; a
				// bundle-only hit is never treated as already-local). See
				// ASTVariableDecl.resolveVarWithinDeclContext's matching fix for
				// the initializer-assignment path (`var x = 1`), which resolves
				// the same way but is reached BEFORE this method for an
				// initialized declaration - both were needed together, confirmed
				// via test262 language/eval-code/direct/var-env-var-strict-
				// {caller,caller-3,source}.js.
				VarAccessor var;
				if(forceStrictMode) {
					VariableMap ownMap = getVariableMap(false);
					var = ownMap!=null ? ownMap.getEntry(varName) : null;
				} else {
					var = getLocalVariableEntry(varName);
				}
				if(var!=null) {
					//if(var.getType()!=VAR_TYPE.VAR || var.getType()!=VAR_TYPE.AUTO) {
					//	throw new IllegalStateException(); // TODO for now!
					//}
					var.setValue(value);
					return var;
				}
				if(!forceStrictMode) {
					// Non strict mode, we use the parent context
					var = findParentAccessor(varName);
					if(var!=null) {
						// CreateGlobalFunctionBinding (unlike CreateGlobalVarBinding, which
						// leaves an existing property's descriptor untouched - the plain
						// setValue() below) always REDEFINES a pre-existing CONFIGURABLE
						// global property back to the standard writable:true/enumerable:true
						// descriptor. Reusing the found accessor as-is would silently keep a
						// stale descriptor from e.g. a prior Object.defineProperty() call.
						// Route through the real global context's own createVariable()
						// (InterpretedGlobalRuntimeContext's VAR_TYPE.FUNCTION branch already
						// implements that redefinition correctly) whenever this eval's
						// declarations actually land on the global object (test262
						// language/eval-code/{direct,indirect}/var-env-func-init-global-
						// update-configurable.js).
						if(type==VAR_TYPE.FUNCTION) {
							JSGlobalContext target = resolveGlobalDeclarationTarget(varName);
							if(target!=null) {
								return target.createVariable(varName, value, type);
							}
						}
						// NOT_AVAILABLE is passed by the program context, so we do not initialized the var in this case as we use the parent one.
						if(value!=RuntimeUtil.NOT_AVAILABLE) {
							var.setValue(value);
						}
						return var;
					}
					// This binding is being hoisted into an already-existing outer scope by a
					// direct eval; EvalDeclarationInstantiation creates such bindings deletable,
					// unlike the outer function's own declared vars/functions (non-configurable).
					JSRuntimeContext target = getSurroundingContext().getVarDeclContext();
					// EvalDeclarationInstantiation (ECMA-262 19.2.1.3 step 5): walking
					// from this eval's own lexical environment out to its variableEnv,
					// a SyntaxError is thrown if that walk passes through a DIFFERENT,
					// intervening environment that already binds this name. For a
					// function whose parameter list hasParameterExpressions, the spec
					// keeps the parameter environment and the body's variable
					// environment as two SEPARATE environments - so a direct eval
					// called while a parameter's own default-value expression is still
					// being evaluated (the only time GaltaJS's own declContext
					// resolution can land on the parameter frame itself: once the body
					// frame exists, every later declContext resolution targets it
					// instead - see InterpretedFunctionRuntimeContext.
					// hasParameterExpressions()) always has its variableEnv be some
					// OTHER, outer environment, never this parameter frame - so finding
					// the name already bound here must be this SyntaxError, UNLESS the
					// existing entry is one this SAME eval's own program already
					// created here itself (see below) - never a collision with an
					// UNRELATED binding. Confirmed via test262's
					// eval-var-scope-syntax-err.js family (function, generator,
					// async-generator, method/getter/setter forms).
					if(target instanceof InterpretedFunctionRuntimeContext fnCtx
							&& fnCtx.hasParameterExpressions()) {
						VarAccessor existing = fnCtx.getLocalVariableEntry(varName);
						// VAR_TYPE.SYSTEM here is NOT a genuine declared parameter -
						// it's the internal "arguments" placeholder ASTFunction.init()
						// unconditionally pre-declares in EVERY function's (including
						// an ARROW's, which never has its own arguments object at all)
						// static var set, purely so a body/parameter reference to
						// "arguments" resolves through the normal variable-lookup
						// machinery (falling through to the enclosing scope's real
						// arguments - see InterpretedFunctionRuntimeContext's own
						// SYSTEM-slot init). A direct eval's `var arguments = ...`
						// inside such a function's parameter-expression scope must NOT
						// be treated as colliding with this placeholder (test262
						// arrow-fn-body-cntns-arguments-*.js: an arrow's default-
						// parameter eval declaring `var arguments` must succeed,
						// landing in the parameter frame, distinct from the body's own
						// `function arguments(){}` declaration).
						//
						// VAR_TYPE.VAR here is likewise NOT a collision: this exact
						// eval'd program's own ASTProgram var-hoisting phase runs
						// BEFORE its statements execute, and calls this same
						// createVariable() itself - for a name with no OTHER existing
						// binding anywhere, hoisting already reaches this branch once
						// and creates the VAR_TYPE.VAR entry checked here; the var
						// declaration STATEMENT then calls createVariable() a SECOND
						// time (to assign the real value) and must find its OWN
						// earlier-hoisted entry, not treat it as a foreign collision
						// (test262 language/expressions/assignment/S11.13.1_A6_T1.js:
						// `eval("var x;")` inside a function whose OWN parameter-
						// expression scope has no "x" of its own must still succeed).
						// A genuine parameter is always VAR_TYPE.PREDECLARED (see
						// BuiltinFunctionInterpreter.bindParametersAndVars), never VAR -
						// so this can't accidentally let a real parameter collision
						// through.
						if(existing!=null && existing.getType()!=VAR_TYPE.SYSTEM && existing.getType()!=VAR_TYPE.VAR) {
							throw RuntimeUtil.syntaxError("Identifier '{0}' has already been declared", varName);
						}
					}
					return target.createVariable(varName, value, type, true);
				}
				// We declare it in this scope
				return super.createVariable(varName, value, type);
			}
			
			// Regular variable
			return super.createVariable(varName, value, type);
		}

		protected abstract VarAccessor findParentAccessor(String varName);

		@Override
		public JSGlobalContext resolveGlobalDeclarationTarget(String varName) {
			// Deliberately does NOT use getVariableEntry()/findParentAccessor()
			// (as createVariable() itself does): those walk the FULL identifier
			// resolution chain, which for a name like "NaN" finds a non-null
			// accessor simply because it's an existing GLOBAL OBJECT property -
			// exactly the collision scenario CanDeclareGlobalFunction/
			// CanDeclareGlobalVar need to inspect, not a reason to skip
			// validation. The only thing that actually determines where this
			// eval's var/function declarations land is the nearest enclosing
			// variable-declaration context: an intermediate function scope
			// (real shadowing - the global is never touched) vs. the global
			// itself (this eval's declaration collides there, whether or not
			// "varName" already exists as a property).
			if(forceStrictMode) {
				return null;
			}
			JSRuntimeContext surroundingVarDecl = getSurroundingContext().getVarDeclContext();
			if(surroundingVarDecl instanceof JSEvalRuntimeContext nestedEval) {
				return nestedEval.resolveGlobalDeclarationTarget(varName);
			}
			if(surroundingVarDecl instanceof JSGlobalContext gctx) {
				return gctx;
			}
			return null;
		}
	}
	private static final class InterpretedEvalContext extends BaseEvalContext {
		InterpretedEvalContext(JSInterpretedRuntimeContext parent) {
			super(parent);
		}
		@Override
		public JSRuntimeContext getSurroundingContext() {
			return getParent();
		}
		@Override
		protected VarAccessor findParentAccessor(String varName) {
			//return getParent().getVariableEntry(varName);
			return getParent().resolveOwnIdentifierEntry(varName);
		}
	}
	private static final class TranspiledEvalContext extends BaseEvalContext {
		private static final Object[] NO_WITH_OBJECTS = new Object[0];
		private VarAccessor[] variables;
		// with-object(s) LEXICALLY enclosing this eval call site, nearest first -
		// see ASTCall.collectEnclosingWithJavaNames' own doc and
		// resolveOwnIdentifierEntry() below. Empty (NO_WITH_OBJECTS) for every
		// caller that doesn't bundle any (indirect eval, or a direct eval not
		// inside a `with` - the overwhelming common case), matching this class's
		// pre-existing 2-arg constructor exactly.
		private final Object[] withObjects;
		TranspiledEvalContext(JSTranspiledRuntimeContext parent, VarAccessor[] variables) {
			this(parent, variables, NO_WITH_OBJECTS);
		}
		TranspiledEvalContext(JSTranspiledRuntimeContext parent, VarAccessor[] variables, Object[] withObjects) {
			super(parent);
			this.variables = variables;
			this.withObjects = withObjects!=null ? withObjects : NO_WITH_OBJECTS;
		}
		@Override
		public JSRuntimeContext getSurroundingContext() {
			//return getParent().getParent();
			return getParent();
		}
		// BaseEvalContext.createVariable()'s NON-strict var/function/auto path
		// checks getLocalVariableEntry(varName) FIRST, before ever reaching
		// findParentAccessor below - the inherited default (AbstractRuntimeContext.
		// getLocalVariableEntry) falls through to resolveOwnIdentifierEntry(...),
		// which for THIS class is deliberately unrestricted (see that method's
		// own doc - ordinary reads/writes of an already-existing identifier
		// inside eval'd text must see the full lexical chain). Left unoverridden,
		// that same unrestricted search would ALSO answer this "is a var/function
		// declaration already declared in the scope EvalDeclarationInstantiation
		// targets" question - silently matching an outer-ancestor-only bundle
		// entry (isOwnScope() false) and short-circuiting createVariable's first
		// check before findParentAccessor's own (already own-scope-restricted)
		// logic is ever reached. Overriding here, specifically for this one
		// "already locally declared" question, closes that: a genuine repeat
		// declaration within the SAME eval text (this context's own dynamic
		// VariableMap) still matches, and so does a bundle entry actually
		// declared in the eval call site's own function/global scope - only an
		// outer-ancestor-only bundle entry is excluded, exactly like
		// findParentAccessor below.
		@Override
		public VarAccessor getLocalVariableEntry(String varName) {
			VariableMap own = getVariableMap(false);
			VarAccessor local = own!=null ? own.getEntry(varName) : null;
			if(local!=null) {
				return local;
			}
			return findTranspiledAccessor(varName, true);
		}
		@Override
		protected VarAccessor findParentAccessor(String varName) {
			// Own-scope-only here (unlike resolveOwnIdentifierEntry below): this
			// is BaseEvalContext.createVariable()'s "is this var/function
			// declaration already declared in the scope EvalDeclarationInstantiation
			// actually targets" check - per spec (19.2.1.3 step 5) that's ONE
			// specific environment record (the calling function's own
			// VariableEnvironment), never an outer ancestor just because it
			// happens to share the name. A bundled entry only reachable by
			// walking OUT past the eval call site's own function (isOwnScope()
			// false - see VarAccessor's own doc) must NOT be reused/overwritten
			// here; falling through (both here and via the surrounding
			// getParent().resolveOwnIdentifierEntry() check, which is itself
			// already own-context-only) lets createVariable's own fallback
			// correctly create a genuinely NEW binding local to the calling
			// function instead, shadowing the outer one for the rest of that
			// invocation. Confirmed via test262 S11.13.2_A6.*_T1.js/
			// S11.13.1_A6_T2.js (KnownGaps.md's "a read AFTER a same-function
			// direct-eval doesn't see the eval's shadowing var" entry).
			VarAccessor v = findTranspiledAccessor(varName, true);
			if(v!=null) {
				return v;
			}
			// Skip eval() function context
			// ctx -> Function context -> parent
			//return parent.getParent().getVariableEntry(varName);
			return getParent().resolveOwnIdentifierEntry(varName);
		}

		@Override
		public VarAccessor resolveOwnIdentifierEntry(String varName) {
			// Object Environment Record HasBinding, the [[IsWithEnvironment]]==
			// true branch (spec 9.1.1.2.1): each with-object LEXICALLY enclosing
			// this eval call site (nearest first) must shadow the eval call
			// site's own static free-variable bundle below, exactly the
			// priority InterpretedWithRuntimeContext.resolveOwnIdentifierEntry()
			// already gives an ordinary (non-eval) identifier read/write in
			// INTERPRETED mode. A TRANSPILED direct eval never reaches an
			// actual with-backed runtime context via getParent() - ASTWith's
			// transpiled codegen represents `with` purely as a plain Java local
			// (`with_N`), never a runtime context object (see ASTWith.
			// transpileJavaStatement) - so ASTCall bundles each enclosing
			// with_N's CURRENT value directly into this eval call's own
			// Object[] argument bundle instead (see ASTCall.
			// collectEnclosingWithJavaNames/transpileSpecialFunctions).
			// withObjects is empty (NO_WITH_OBJECTS) in the overwhelming common
			// case - eval not lexically inside a `with` - leaving this
			// completely unchanged from before this loop existed. See test262
			// language/eval-code/direct/global-env-rec-with.js.
			for(int i=0; i<withObjects.length; i++) {
				Object with = withObjects[i];
				if(RuntimeUtil.hasProperty(getEnvironment(),with,varName) && !isUnscopable(with,varName)) {
					return withVarAccessor(with, varName);
				}
			}
			// Unrestricted (any bundled entry, own-scope or not): an ORDINARY
			// read/write of an already-existing identifier inside eval'd text
			// must still see the full lexical chain - only
			// the var/function-DECLARATION reuse decision above is scoped
			// tighter.
			VarAccessor v = findTranspiledAccessor(varName, false);
			if(v!=null) {
				return v;
			}
			return super.resolveOwnIdentifierEntry(varName);
		}

		// Object Environment Record's HasBinding (spec 9.1.1.2.1), the
		// [[IsWithEnvironment]]==true branch: a with-object's own @@unscopables
		// property, if it's an object, can mark individual names as "not
		// visible through this with" by mapping them to a truthy value. Mirrors
		// InterpretedWithRuntimeContext.isUnscopable()/JSTranspiledUnit.
		// isUnscopable() exactly.
		private boolean isUnscopable(Object withObj, String varName) {
			Object unscopables = RuntimeUtil.getProperty(getEnvironment(), withObj, Symbol.UNSCOPABLES, RuntimeUtil.UNDEFINED);
			if(RuntimeUtil.isObject(getEnvironment(), unscopables)) {
				return RuntimeUtil.toBoolean(getEnvironment(), RuntimeUtil.getProperty(getEnvironment(), unscopables, varName, RuntimeUtil.UNDEFINED));
			}
			return false;
		}

		// Mirrors InterpretedWithRuntimeContext.resolveOwnIdentifierEntry()'s
		// own inline VarAccessor exactly (GetBindingValue/SetMutableBinding's
		// own independent HasProperty re-checks, Proxy-observable; S is the
		// REFERENCING code's own strictness, read from JSRuntimeContext.get() -
		// the eval'd text currently executing - not this with-object's own,
		// always-non-strict, syntactic origin).
		private VarAccessor withVarAccessor(Object with, String varName) {
			return new VarAccessor() {
				@Override
				public String getKey() {
					return varName;
				}
				@Override
				public Object getValue() {
					if(!RuntimeUtil.hasProperty(getEnvironment(),with,varName)) {
						if(JSRuntimeContext.get().isStrictMode()) {
							throw RuntimeUtil.referenceError("{0} is not defined", varName);
						}
						return RuntimeUtil.UNDEFINED;
					}
					Object v = RuntimeUtil.getProperty(getEnvironment(),with,varName,RuntimeUtil.UNDEFINED);
					if(v instanceof Callable c) {
						return WithClosure.of(with,c);
					}
					return v;
				}
				@Override
				public Object setValue(Object value) {
					boolean stillExists = RuntimeUtil.hasProperty(getEnvironment(),with,varName);
					if(!stillExists && JSRuntimeContext.get().isStrictMode()) {
						throw RuntimeUtil.referenceError("{0} is not defined", varName);
					}
					RuntimeUtil.setProperty(getEnvironment(),with,varName,value);
					return value;
				}
				@Override
				public boolean stillExists() {
					return RuntimeUtil.hasProperty(getEnvironment(),with,varName);
				}
			};
		}
		// No deleteVariable() override here (unlike findParentAccessor/
		// resolveOwnIdentifierEntry above): a prior version gated deletion on
		// the name being found in the static `variables` bundle (the eval call
		// site's free-variable snapshot) and returned false outright otherwise.
		// That wrongly blocked deleting a binding the eval itself just CREATED
		// (e.g. `eval('delete f; function f(){}')`'s `f`) - such a binding is
		// never part of the pre-existing bundle (it didn't exist yet when the
		// bundle was built at transpile time), but it IS a real, configurable
		// entry in the surrounding TranspiledRuntimeContext's own VariableMap
		// (see BaseEvalContext.createVariable's `target.createVariable(...,
		// true)` call). The inherited AbstractRuntimeContext.deleteVariable()
		// already walks the real parent chain and checks each entry's actual
		// isConfigurable()/VAR_TYPE - correctly refusing to delete a bundled,
		// non-configurable local (JSVarRef.isConfigurable() defaults to false;
		// see test262 language/expressions/delete/11.4.1-4.a-7.js) while
		// correctly allowing deletion of an eval-created configurable binding
		// not present in the bundle at all (test262 language/eval-code/direct/
		// var-env-{func,var}-init-local-new-delete.js). No override needed -
		// same as InterpretedEvalContext, which never had one.
		private VarAccessor findTranspiledAccessor(String varName, boolean requireOwnScope) {
			for(int i=0; i<variables.length; i++) {
				VarAccessor v = variables[i];
				if(v.getKey().equals(varName) && (!requireOwnScope || v.isOwnScope())) {
					return v;
				}
			}
			return null;
		}
	}

	private final static class GlobalFunction extends BaseMethod {
		private FunctionIndex index;

		GlobalFunction(JSEnvironment env, String functionName, FunctionIndex index, int length) {
			super(env,functionName,length);
			this.index = index;
		}

		@Override
		protected Object invoke(Object _this, Object[] args) {
			switch(index) {
				case eval -> {
					JSRuntimeContext context = JSRuntimeContext.get();
					InterpretedRuntimeContext evalContext;
					// Whether the CALLING context is genuinely strict (a real "use
					// strict" directive, never JSEnvironment's parse-dialect toggle) -
					// forces the eval'd text to parse as strict too, regardless of its
					// own directive prologue. Only ever true for a direct eval; an
					// indirect eval never inherits caller strictness.
					boolean forceStrict = false;
					// Caller-derived facts for the `new.target`/`super()` early-SyntaxError
					// checks (PerformEval's inFunc/inMethod/inDerivedCtor). An indirect
					// eval (any caller mode) always gets all-false (matches spec: direct=
					// false skips computing these entirely) - correctly rejecting
					// `new.target`/`super` unconditionally in indirectly-eval'd text. A
					// direct eval with an INTERPRETED caller computes these from the real
					// runtime context chain (findNearestNonArrowFunction(), below). A
					// direct eval with a TRANSPILED caller bakes these in as static-AST
					// literals at transpile time instead (StandardLibrary.
					// isCallerInFunctionScope()/isCallerInMethod()/
					// isCallerInDerivedClassConstructor(), read from the bundled eval-args
					// array below) - same static-AST-walk approach already used for
					// callerInParameterExpressionScope/callerInFieldInitializer. Declared
					// false here purely as the safe/spec-correct fallback; every real path
					// below explicitly assigns all three.
					boolean callerHasNewTarget = false;
					boolean callerIsMethod = false;
					boolean callerIsDerivedCtor = false;
					// Whether the eval call site sits lexically inside the nearest
					// enclosing function's OWN default parameter-value expression
					// (never the body) - a plain static AST property of the call
					// site itself, unlike the new.target/super facts above (no
					// eval-within-eval runtime-chain concern here: whatever program
					// this call site's AST belongs to already fully represents its
					// own immediately-enclosing structure). Being purely static, a
					// direct eval with a TRANSPILED caller computes this exactly
					// (ASTCall.transpileSpecialFunctions() bakes it in as a literal
					// at transpile time, via StandardLibrary's own
					// isCallerInParameterExpressionScope() made public for that
					// purpose) - unlike callerHasNewTarget/callerIsMethod/
					// callerIsDerivedCtor above, which genuinely still need runtime
					// caller-context shape the transpiler doesn't yet expose. Stays
					// false (permissive, correctly - indirect eval never has an
					// enclosing parameter scope at all) for indirect eval.
					boolean callerInParameterExpressionScope = false;
					// Broader sibling of the flag above - see StandardLibrary.
					// isCallerInAnyParameterExpressionScope()'s own doc (no arrow
					// exemption). Used only by ASTProgram.evaluate()'s sub-case-B
					// collision check, via BaseEvalContext.
					// setCallerInAnyParameterExpressionScope() below.
					boolean callerInAnyParameterExpressionScope = false;
					// Whether the eval call site sits lexically inside a class
					// field's own Initializer expression - same static-AST-property
					// nature (and same transpiled-direct-eval fix) as
					// callerInParameterExpressionScope above.
					boolean callerInFieldInitializer = false;
					// The set of private names ("#name") visible from any class
					// lexically enclosing the eval call site (spec:
					// EvalDeclarationInstantiation's privateIdentifiers, collected
					// from the caller's PrivateEnvironment chain) - see
					// PrivateNameValidator's class doc. A direct eval (either
					// caller mode) enforces with the real set of #names declared
					// by any class lexically enclosing the eval call site -
					// INTERPRETED via a runtime context-chain walk
					// (collectEnclosingPrivateNames, below), TRANSPILED via a
					// static-AST equivalent baked in at transpile time
					// (PrivateNameValidator.collectEnclosingPrivateNames(ASTNode),
					// bundled into the eval-args array by ASTCall). Indirect eval
					// (either mode) always enforces with an EMPTY set (the
					// declared-below default) - it never has any enclosing
					// PrivateEnvironment at all, matching Script semantics
					// exactly. This does NOT close the separate gap where a
					// transpiled direct eval literally INSIDE a class field
					// initializer can VALIDATE a private name but still can't
					// actually READ it (needs the real minted PrivateName
					// tokens, not just their string names) - see KnownGaps.md.
					java.util.Set<String> callerPrivateNames = java.util.Collections.emptySet();
					// Number of REAL (non-metadata) arguments - equals args.length
					// normally, but one less when the trailing element turns out to
					// be the direct-eval metadata (set below). Needed because a
					// spread call that expands to zero values (eval(...emptyIter))
					// leaves the metadata as the ONLY element, and args[0] must then
					// be treated as absent (spec: "if argList has no elements,
					// return undefined"), not read as the metadata object itself.
					int realArgCount = args.length;
					if(context instanceof JSTranspiledRuntimeContext tc) {
						// The direct-eval codegen bundles the VarAccessor[], the
						// caller's genuine-strictness flag, and the static-AST
						// caller-scope facts below into a single Object[8] (see
						// ASTCall.transpileSpecialFunctions) rather than passing
						// them as separate arguments.
						// The metadata is always the LAST synthesized argument
						// (see ASTCall.transpileSpecialFunctions/
						// ASTBaseCall.transpileParams), not fixed at args[1] -
						// for a spread call (eval(...iter)), the spread's own
						// runtime-expanded value count shifts the metadata's
						// actual index (test262 eval-spread.js: iter yielding
						// 2 values put the metadata at args[2], not args[1],
						// so this direct-eval call was wrongly falling through
						// to the indirect-eval branch below).
						// The trailing `tc.getEnvironment()==getEnvironment()` mirrors the
						// interpreted branch below: the codegen bundles direct-eval
						// metadata for ANY literal `eval(...)` call, but one that
						// reaches another realm's eval function is an INDIRECT eval
						// in that realm, not a direct one here.
						if(args.length>=1 && args[args.length-1] instanceof Object[] evalArgs && evalArgs.length>=1 && evalArgs[0] instanceof VarAccessor[] jsVariables && tc.getEnvironment()==getEnvironment()) {
							realArgCount = args.length-1;
							// Prefer the caller's own LEXICAL context (the
							// literal `_ctx` Java variable in scope at the
							// call site, bundled as this array's last
							// element - see ASTCall.transpileSpecialFunctions'
							// own comment) over the AMBIENT `tc` from
							// JSRuntimeContext.get() above: for a direct eval
							// inside a static/instance field initializer,
							// `_ctx` was reassigned to a
							// TranspiledFieldInitializerRuntimeContext (a
							// pure Java-local rename, invisible to the
							// ambient/thread-local context this method
							// otherwise reads) so `this`/[[HomeObject]]
							// resolve against the field's own context
							// instead of whatever ambient context happens to
							// be active (test262
							// static-field-init-with-this.js). Falls back to
							// `tc` when absent (e.g. an older/shorter bundle
							// shape) or not a JSTranspiledRuntimeContext.
							JSTranspiledRuntimeContext lexicalCtx = (evalArgs.length>=9 && evalArgs[8] instanceof JSTranspiledRuntimeContext lc) ? lc : tc;
							// with-object(s) LEXICALLY enclosing this eval call site
							// (nearest first), bundled at index 10 - see ASTCall.
							// collectEnclosingWithJavaNames/transpileSpecialFunctions
							// and TranspiledEvalContext.resolveOwnIdentifierEntry()'s
							// own doc. Absent (index not present at all) for the
							// overwhelming common case of an eval not lexically
							// inside any `with` - TranspiledEvalContext's own 2-arg
							// constructor already defaults to "no with-objects" then.
							Object[] withObjects = (evalArgs.length>=11 && evalArgs[10] instanceof Object[] ws) ? ws : null;
							evalContext = withObjects!=null
									? new TranspiledEvalContext(lexicalCtx,jsVariables,withObjects)
									: new TranspiledEvalContext(lexicalCtx,jsVariables);
							forceStrict = evalArgs.length>=2 && Boolean.TRUE.equals(evalArgs[1]);
							callerInParameterExpressionScope = evalArgs.length>=3 && Boolean.TRUE.equals(evalArgs[2]);
							callerInAnyParameterExpressionScope = evalArgs.length>=10 && Boolean.TRUE.equals(evalArgs[9]);
							callerInFieldInitializer = evalArgs.length>=4 && Boolean.TRUE.equals(evalArgs[3]);
							callerHasNewTarget = evalArgs.length>=5 && Boolean.TRUE.equals(evalArgs[4]);
							callerIsMethod = evalArgs.length>=6 && Boolean.TRUE.equals(evalArgs[5]);
							callerIsDerivedCtor = evalArgs.length>=7 && Boolean.TRUE.equals(evalArgs[6]);
							// Closes the AllPrivateNamesValid pre-scan gap for a
							// transpiled direct-eval caller - previously always
							// null here ("skip the check entirely"); now a real
							// (possibly empty) set baked in by ASTCall at
							// transpile time. Does NOT close the separate "can a
							// direct eval inside a field initializer actually
							// READ the enclosing class's private names" gap -
							// that needs the real minted PrivateName tokens
							// threaded through, not just their string names;
							// still documented in KnownGaps.md as unfixed.
							callerPrivateNames = (evalArgs.length>=8 && evalArgs[7] instanceof String[] names)
									? new java.util.LinkedHashSet<>(java.util.Arrays.asList(names))
									: java.util.Collections.emptySet();
						} else {
							// Indirect eval (e.g. `var s = eval; s(...)`, `(0,eval)(...)`):
							// the transpiler's direct-eval codegen (which injects the
							// calling scope's VarAccessor[] as a 2nd argument) only fires
							// for a literal `eval(...)` call - any other invocation shape
							// reaches here as an ordinary function call. Per spec, indirect
							// eval runs in the GLOBAL scope only, with no access to the
							// caller's locals - an empty VarAccessor[] against the global
							// context gives exactly that (every lookup falls through to
							// the global context, same as interpreted mode's indirect-eval
							// branch below).
							if(context.getEnvironment()!=getEnvironment()) {
								// Another realm's eval reached from transpiled code:
								// it runs in THAT realm's global scope (its root
								// context is interpreted - see JSEnvironment.
								// getRealmContext()), same as the interpreted
								// branch below.
								evalContext = new InterpretedEvalContext(getEnvironment().getRealmContext());
							} else {
								evalContext = new TranspiledEvalContext((JSTranspiledRuntimeContext)context.getGlobalContext(), new VarAccessor[0]);
							}
							// Indirect eval never depends on the caller's context, in either
							// execution mode - matches spec unconditionally.
							callerHasNewTarget = false;
							callerIsMethod = false;
							callerIsDerivedCtor = false;
						}
					} else {
						JSInterpretedRuntimeContext ic = (JSInterpretedRuntimeContext)context;
						// A direct eval is only a call that resolves to the CALLER
						// realm's own %eval% - `var eval = other.eval; eval(src)`
						// is syntactically eval-shaped but reaches a different
						// realm's eval function, which spec treats as an INDIRECT
						// eval in that other realm's global scope (test262
						// language/expressions/call/eval-realm-indirect.js).
						if(isDirectEval(ic) && ic.getEnvironment()==getEnvironment()) {
							evalContext = new InterpretedEvalContext(ic);
							forceStrict = ic.getCallerNode().findParentNodeByClass(IContextRootContainer.class).isGenuinelyStrictMode();
							// Nearest enclosing NON-ARROW function at the eval call site, found
							// by walking the RUNTIME context chain (JSFunctionContext), not the
							// static AST - a static AST walk from the caller node breaks for
							// eval-within-eval (each eval'd text is its own freshly-parsed,
							// AST-disconnected program), while the runtime context chain
							// naturally threads through any number of eval layers back to the
							// real enclosing function, same as GetThisEnvironment() does per
							// spec. Same arrow-skipping loop as RuntimeUtil.getSuper()/superCtor().
							BuiltinFunction callerFn = findNearestNonArrowFunction(ic);
							callerHasNewTarget = callerFn!=null;
							callerIsMethod = callerFn!=null && callerFn.getHomeObject()!=null;
							callerIsDerivedCtor = callerFn!=null && callerFn.getClassConstructor()!=null && callerFn.getClassConstructor().getSuperClass()!=null;
							callerInParameterExpressionScope = isCallerInParameterExpressionScope(ic.getCallerNode());
							callerInAnyParameterExpressionScope = isCallerInAnyParameterExpressionScope(ic.getCallerNode());
							callerInFieldInitializer = isCallerInFieldInitializer(ic.getCallerNode());
							callerPrivateNames = new java.util.LinkedHashSet<>();
							ic.collectEnclosingPrivateNames(callerPrivateNames);
						} else {
							// An indirect eval reached from INTERPRETED code (a
							// direct eval's dynamically-parsed body always runs
							// through the interpreter regardless of the
							// ORIGINAL caller's own execution mode - see this
							// whole branch's own top comment) whose own global
							// context is actually the TRANSPILED program's real
							// global context - i.e. this interpreted code is
							// itself nested inside a transpiled program via an
							// enclosing direct eval (test262 language/eval-code/
							// indirect/global-env-rec-eval.js: an indirect eval
							// called from within a direct eval, where the
							// OUTERMOST caller is transpiled). Casting
							// unconditionally to JSInterpretedRuntimeContext
							// threw ClassCastException (TranspiledGlobalRuntimeContext
							// cannot be cast to JSInterpretedRuntimeContext) -
							// construct a TranspiledEvalContext against the real
							// transpiled global instead, exactly mirroring the
							// TRANSPILED-caller indirect-eval branch above (same
							// empty VarAccessor[], since indirect eval never
							// sees the caller's locals in either mode).
							JSGlobalContext gctx = context.getGlobalContext();
							if(gctx.getEnvironment()!=getEnvironment()) {
								// An indirect eval through ANOTHER realm's own
								// `eval` (`other.eval("...")`): the code runs in
								// the global scope of the realm the eval function
								// belongs to (the "current realm" while it runs),
								// not the caller's - see JSEnvironment.getRealmContext().
								gctx = getEnvironment().getRealmContext();
							}
							if(gctx instanceof JSTranspiledRuntimeContext tgc) {
								evalContext = new TranspiledEvalContext(tgc, new VarAccessor[0]);
							} else {
								evalContext = new InterpretedEvalContext((JSInterpretedRuntimeContext)gctx);
							}
							callerHasNewTarget = false;
							callerIsMethod = false;
							callerIsDerivedCtor = false;
						}
					}
					if(realArgCount>=1) {
						Object script = param(args, 0, RuntimeUtil.UNDEFINED);
						if(script instanceof CharSequence && !RuntimeUtil.isBoxedString(getEnvironment(), script)) {
							String sscript = script.toString();
							if(StringUtil.isNotEmpty(sscript)) {
								// Should we compile the eval with debug information?
								try {
									// Threaded onto evalContext itself (not via createEvalScript()
									// below/__init, unlike forceStrict/callerInFieldInitializer) so
									// ASTProgram.evaluate()'s own sub-case-B collision check can read
									// it back at DECLARATION time, via the JSEvalRuntimeContext
									// interface method this overrides - see that field's own doc.
									((BaseEvalContext)evalContext).setCallerInAnyParameterExpressionScope(callerInAnyParameterExpressionScope);
									// executeForEval() set the current context
									JSInterpretedUnit evalScript = getEnvironment().createEvalScript(sscript,"eval",forceStrict,callerHasNewTarget,callerIsMethod,callerIsDerivedCtor,callerInParameterExpressionScope,callerInFieldInitializer,callerPrivateNames);
									return evalScript.executeForEval(evalContext);
								} catch(JSParseException ex) {
									throw RuntimeUtil.syntaxError(ex.getLocalizedMessage());
								}
							}
						} else {
							return script;
						}
					}
					return RuntimeUtil.UNDEFINED;
				}
				case isNaN -> {
	        		Object o = param(args, 0, RuntimeUtil.UNDEFINED);
	        		if(o!=null) {
	        			double d = RuntimeUtil.toDouble(getEnvironment(),o);
        				return Double.isNaN(d);
	        		}
	        		return false;
					
				}
				case isFinite -> {
	        		Object o = param(args, 0, RuntimeUtil.UNDEFINED);
	        		if(o!=null) {
	        			double d = RuntimeUtil.toDouble(getEnvironment(),o);
        				return Double.isFinite(d);
	        		}
	        		return true;
					
				}
				case encodeURI -> {
					String s = paramString(args, 0, "undefined");
	        		return URIHandler.encode(s,true);
				}
				case encodeURIComponent -> {
					String s = paramString(args, 0, "undefined");
	        		return URIHandler.encode(s,false);
				}
				case decodeURI -> {
					String s = paramString(args, 0, "undefined");
	        		return URIHandler.decode(s,true);
				}
				case decodeURIComponent -> {
					String s = paramString(args, 0, "undefined");
	        		return URIHandler.decode(s,false);
				}
				case escape -> {
	                if (args.length == 0) {
	                    return "undefined";
	                }
	                StringBuilder b = new StringBuilder();
	                String s = paramString(args,0);
	                int length = s.length();
	                for (int i = 0; i < length; i++) {
	                    char c = s.charAt(i);
	                    if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')
	                            || (c >= '0' && c <= '9') || c == '@' || c == '*'
	                            || c == '_' || c == '+' || c == '-' || c == '.'
	                            || c == '/') {
	                        b.append(c);
	                    } else {
	                        if (c >= 256) {
	                            b.append("%u");
	                            b.append(StringUtil.toUnsignedHex4(c).toUpperCase());
	                        } else {
	                            b.append("%");
	                            b.append(StringUtil.toUnsignedHex2(c).toUpperCase());
	                        }
	                    }
	                }
	                return b.toString();
	            }
	            case unescape -> {
	                if (args.length == 0) {
	                    return "undefined";
	                }
	                StringBuilder b = new StringBuilder();
	                String s = paramString(args,0);
	                int length = s.length();
	                for (int i = 0; i < length; i++) {
	                    char c = s.charAt(i);
	                    if (c == '%' && i + 1 < length) {
	                        if (s.charAt(i + 1) == 'u') {
	                            if (i + 6 <= length) {
		                            try {
		                                String val = s.substring(i + 2, i + 6);
		                                b.append((char) parseHexDigits(val));
		                                i += 5;
		                                continue;
		                            }catch(NumberFormatException ex) {}
	                            }
	                        } else if (i + 3 <= length) {
	                            try {
		                            String val = s.substring(i + 1, i + 3);
		                            b.append((char) parseHexDigits(val));
		                            i += 2;
		                            continue;
	                            }catch(NumberFormatException ex) {}
	                        }
	                    }
	                    b.append(c);
	                }
	                return b.toString();
	            }
				default -> {
				    throw new IllegalStateException(); 
				}
			}
		}
	}
	
	private static boolean isDirectEval(JSInterpretedRuntimeContext context) {
		ASTNode callerNode = context.getCallerNode();
		if(callerNode instanceof ASTCall call && !call.isNullOp()) {
			ASTNode fn = call.getNode();
			if(fn instanceof ASTIdentifier id && "eval".equals(id.getId())) {
				return true;
			}
		}
		return false;
	}

	// Nearest enclosing (skipping arrows) function object at a direct eval's call
	// site, found by walking the RUNTIME context chain (JSFunctionContext), the
	// same way RuntimeUtil.getSuper()/superCtor() do - NOT a static AST walk: a
	// static walk from the caller's ASTCall node would break for eval-within-eval
	// (each eval'd text is its own freshly-parsed, AST-disconnected program with
	// no static link back to whatever called IT), whereas the runtime context
	// chain naturally threads through any number of eval layers back to the real
	// enclosing function - BaseEvalContext is not itself a JSFunctionContext, so
	// getFunctionContext()'s walk transparently passes through it, same as
	// GetThisEnvironment() passes through eval's Declarative Environment Records
	// per spec. Returns null when there is no enclosing function at all (the eval
	// call site is at true top-level: global or module code).
	private static BuiltinFunction findNearestNonArrowFunction(JSRuntimeContext context) {
		JSFunctionContext fc = context.getFunctionContext();
		while(fc!=null && fc.getFunction().isArrow()) {
			JSRuntimeContext parent = fc.getParent();
			fc = parent!=null ? parent.getFunctionContext() : null;
		}
		return fc!=null ? fc.getFunction() : null;
	}

	// Unlike findNearestNonArrowFunction() above, this IS a plain static AST
	// walk (not runtime-context-chain-based) - and that's correct here: this
	// call site's own lexical structure (parameter list vs. body) is fully
	// represented within whatever program/eval'd-text this ASTCall node
	// itself belongs to, regardless of how many eval layers sit above it, so
	// there's no eval-within-eval cross-boundary concern to worry about (see
	// findNearestNonArrowFunction()'s own comment for why THAT one needs the
	// runtime chain instead). Finds the nearest enclosing ASTFunction and
	// checks whether the path back up to it passes through that function's
	// OWN parameters node before reaching the function itself - a nested
	// function's own parameter list "resets" this (its own ASTFunction is
	// found first, and that inner function's body/parameters split is what
	// gets checked, not the outer one's).
	// Made public so ASTCall.transpileSpecialFunctions() can call it directly
	// at TRANSPILE time (this method has zero runtime dependency - it's a
	// pure static AST walk from the eval call site's own node, identical
	// whether that node is later reached by the interpreter or transpiled to
	// Java - see that call site for how the resulting boolean gets baked in
	// as a literal, closing the deliberately-deferred permissive-default gap
	// documented above for the transpiled-caller case).
	// Whether callerNode sits lexically inside SOME enclosing function's own
	// parameter list, with no exemption for arrows - the raw structural
	// fact, shared by isCallerInParameterExpressionScope() (which layers the
	// arrow/"arguments" exemption described there on top, for that method's
	// own "arguments" restriction purpose) and the general parameter-name
	// collision check in ASTProgram.evaluate() (which needs the UNEXEMPTED
	// fact - an arrow's own default-parameter eval declaring a var with the
	// SAME NAME as one of the arrow's own OTHER parameters is a genuine
	// collision regardless of whether "arguments" is involved at all; only
	// the "arguments" restriction itself has arrow-specific carve-outs).
	private static boolean isCallerLexicallyInParameterList(ASTNode callerNode) {
		// Deliberately does NOT use ASTNode.findParentNodeByClass() - that
		// method THROWS ("script is missing a container node...") when no
		// matching ancestor exists at all, since every one of ITS callers
		// elsewhere is only ever invoked from somewhere already known to be
		// inside some root container. A direct eval at true top-level script
		// scope (the common case) has no enclosing ASTFunction at all, which
		// is a perfectly normal, expected outcome here, not an error.
		ASTFunction fn = null;
		for(ASTNode n=callerNode.getParent(); n!=null; n=n.getParent()) {
			if(n instanceof ASTFunction f) {
				fn = f;
				break;
			}
		}
		if(fn==null) {
			return false;
		}
		for(ASTNode n=callerNode; n!=null && n!=fn; n=n.getParent()) {
			if(n==fn.getParameters()) {
				return true;
			}
		}
		return false;
	}

	// See isCallerLexicallyInParameterList()'s own doc for why this exists
	// as a separate, broader fact - used by ASTProgram.evaluate()'s general
	// parameter-name collision check, NOT by
	// checkParameterExpressionArgumentsRestriction() (which still needs the
	// narrower, arrow-exempted isCallerInParameterExpressionScope() below).
	public static boolean isCallerInAnyParameterExpressionScope(ASTNode callerNode) {
		return isCallerLexicallyInParameterList(callerNode);
	}

	public static boolean isCallerInParameterExpressionScope(ASTNode callerNode) {
		ASTFunction fn = null;
		for(ASTNode n=callerNode.getParent(); n!=null; n=n.getParent()) {
			if(n instanceof ASTFunction f) {
				fn = f;
				break;
			}
		}
		if(fn==null) {
			return false;
		}
		if(fn.isArrow()) {
			// Arrow functions never have their own IMPLICIT "arguments" object
			// at all (they inherit "arguments" lexically from whatever non-
			// arrow scope encloses them, same as any other identifier) - so
			// there is normally no reserved parameter-scope binding to protect
			// here, and declaring "arguments" via eval inside an arrow's own
			// default parameter expression is NOT a SyntaxError: it's expected
			// to simply create a normal binding visible to the arrow's own
			// body too (confirmed via language/eval-code/direct/arrow-fn-
			// {no-pre-existing,body-cntns-arguments}-*.js). EXCEPT when the
			// arrow's OWN parameter list happens to ALSO declare a REAL,
			// EXPLICIT parameter literally named "arguments" - that's a
			// genuine same-scope sibling binding (not an implicit placeholder)
			// which eval's own "var arguments" collides with exactly like any
			// other named-parameter collision, regardless of arrow-ness
			// (confirmed via arrow-fn-a-{following,preceding}-parameter-is-
			// named-arguments-*.js, which - unlike every OTHER arrow-fn case -
			// DOES expect a throw).
			boolean[] hasArgumentsParam = {false};
			fn.getParameters().forEachVarName((name) -> {
				if("arguments".equals(name)) {
					hasArgumentsParam[0] = true;
				}
			});
			if(!hasArgumentsParam[0]) {
				return false;
			}
		}
		for(ASTNode n=callerNode; n!=null && n!=fn; n=n.getParent()) {
			if(n==fn.getParameters()) {
				return true;
			}
		}
		return false;
	}

	// Whether the eval call site sits lexically inside a class field's own
	// Initializer expression, through any number of transparent arrow
	// functions - stops (returns false) at the first nested NON-arrow
	// function encountered first (that establishes its own genuine
	// "arguments" scope, making the field-initializer restriction moot for
	// code inside it). A field initializer's only child is its own body
	// expression (ASTClassField.getChildCount()==1), so reaching an
	// ASTClassField as callerNode's (or an arrow-transparent ancestor's)
	// parent unambiguously means we're inside that field's own Initializer.
	// Public for the same reason as isCallerInParameterExpressionScope above.
	public static boolean isCallerInFieldInitializer(ASTNode callerNode) {
		for(ASTNode n=callerNode; n!=null; n=n.getParent()) {
			if(n instanceof ASTFunction fn && !fn.isArrow()) {
				return false;
			}
			if(n.getParent() instanceof ASTClassField) {
				return true;
			}
		}
		return false;
	}

	// Static AST-walk equivalent of findNearestNonArrowFunction() above, for
	// the same reason isCallerInParameterExpressionScope() is one: the eval
	// call site's own enclosing function shape is fully known at TRANSPILE
	// time from the AST alone (no eval-within-eval runtime-chain concern -
	// see findNearestNonArrowFunction()'s own comment for why THAT one needs
	// the runtime context chain instead). Skips through any number of
	// enclosing ARROW functions (lexically transparent to new.target/super,
	// per spec) to find the nearest non-arrow one; null means the eval call
	// site is at true top-level (script/module), matching
	// findNearestNonArrowFunction()'s own null case.
	private static ASTFunction findEnclosingNonArrowFunction(ASTNode callerNode) {
		for(ASTNode n=callerNode.getParent(); n!=null; n=n.getParent()) {
			if(n instanceof ASTFunction f && !f.isArrow()) {
				return f;
			}
		}
		return null;
	}

	// The three static-AST equivalents of callerHasNewTarget/callerIsMethod/
	// callerIsDerivedCtor (computed at RUNTIME for an interpreted caller, via
	// findNearestNonArrowFunction()+BuiltinFunction state, above) - public so
	// ASTCall.transpileSpecialFunctions() can bake them in as literals at
	// transpile time, closing the previously deliberately-deferred
	// permissive-default gap for a transpiled-caller direct eval (see this
	// class's own eval-handling comments on callerHasNewTarget's declaration).
	// A class field Initializer's own body is a plain expression child
	// (ASTClassField), never wrapped in an ASTFunction node - so
	// findEnclosingNonArrowFunction() walks straight past it to whatever
	// lexically encloses the CLASS itself (or null, for a top-level class),
	// wrongly treating a direct eval reached from there as running at true
	// top level. Per spec (sec-performeval-rules-in-initializer), such an
	// eval must be treated "as outside a constructor, inside a method, and
	// inside a function" - checked first, before falling back to the
	// ordinary AST walk, in all three helpers below (test262
	// language/{statements,expressions}/class/elements/*-contains-
	// superproperty-*.js/*-contains-newtarget.js: interpreted mode already
	// gets this right via a dedicated runtime frame,
	// InterpretedFieldInitializerRuntimeContext, whose HomeObject is set
	// (isMethod) but whose class-constructor/newTarget are not
	// (not-derived-ctor, new.target reads undefined) - this mirrors that
	// same shape for the transpiled, static-AST-computed path).
	public static boolean isCallerInFunctionScope(ASTNode callerNode) {
		if(isCallerInFieldInitializer(callerNode)) {
			return true;
		}
		return findEnclosingNonArrowFunction(callerNode)!=null;
	}
	public static boolean isCallerInMethod(ASTNode callerNode) {
		if(isCallerInFieldInitializer(callerNode)) {
			return true;
		}
		ASTFunction fn = findEnclosingNonArrowFunction(callerNode);
		return fn!=null && fn.isMethod();
	}
	public static boolean isCallerInDerivedClassConstructor(ASTNode callerNode) {
		if(isCallerInFieldInitializer(callerNode)) {
			return false;
		}
		ASTFunction fn = findEnclosingNonArrowFunction(callerNode);
		return fn!=null && fn.isDerivedClassConstructor();
	}

	private static class URIHandler {
	    private static final String ENCODING = "UTF-8";
	    private static final BitSet UNESCAPED_SET = new BitSet();
	    private static final BitSet RESERVED_SET = new BitSet();
	    private static final BitSet UNESCAPED_AND_RESERVED_SET = new BitSet();
	    private static final BitSet EMPTY_SET = new BitSet();

	    static {
	        for (int i = 'a'; i <= 'z'; i++) {
	            UNESCAPED_SET.set(i);
	        }
	        for (int i = 'A'; i <= 'Z'; i++) {
	            UNESCAPED_SET.set(i);
	        }
	        for (int i = '0'; i <= '9'; i++) {
	            UNESCAPED_SET.set(i);
	        }
	        UNESCAPED_SET.set('-');
	        UNESCAPED_SET.set('_');
	        UNESCAPED_SET.set('.');
	        UNESCAPED_SET.set('!');
	        UNESCAPED_SET.set('~');
	        UNESCAPED_SET.set('*');
	        UNESCAPED_SET.set('\'');
	        UNESCAPED_SET.set('(');
	        UNESCAPED_SET.set(')');

	        RESERVED_SET.set(';');
	        RESERVED_SET.set('/');
	        RESERVED_SET.set('?');
	        RESERVED_SET.set(':');
	        RESERVED_SET.set('@');
	        RESERVED_SET.set('&');
	        RESERVED_SET.set('=');
	        RESERVED_SET.set('+');
	        RESERVED_SET.set('$');
	        RESERVED_SET.set(',');
	        RESERVED_SET.set('#');

	        UNESCAPED_AND_RESERVED_SET.or(UNESCAPED_SET);
	        UNESCAPED_AND_RESERVED_SET.or(RESERVED_SET);
	    }
	    
	    public static String encode(String s, boolean escapeReserved) {
	        int len = s.length();

	        // 6 bytes should cater for all surrogate pairs
	        ByteArrayOutputStream buf = new ByteArrayOutputStream(6);
	        OutputStreamWriter writer = null;
	        try {
	            writer = new OutputStreamWriter(buf, ENCODING);
	        } catch (UnsupportedEncodingException e) {
	            throw RuntimeUtil.uriError(e.getMessage());
	        }
	        StringBuilder sb = new StringBuilder(len);
	        
	        for (int i = 0; i < len; i++) {
	            int c = s.charAt(i);
	            if (escapeReserved ? UNESCAPED_AND_RESERVED_SET.get(c)
	                    : UNESCAPED_SET.get(c)) {
	                sb.append((char) c);
	            } else {
	                if (c >= 0xDC00 && c <= 0xDFFF) {
	                    throw RuntimeUtil.uriError("Invalid Unicode Character");
	                }
	                try {
	                    writer.write(c);
	                    if (c >= 0xD800 && c <= 0xDBFF) {
	                        if ((i + 1) < len) {
	                            /*
	                             * 'c' may be the first code unit of a Unicode
	                             * surrogate pair (high surrogate). 'c2' should be
	                             * the other half (low surrogate), but only if it is
	                             * in range. Otherwise, just continue and pick it up
	                             * as the next character.
	                             */
	                            int c2 = s.charAt(i + 1);
	                            if (c2 >= 0xDC00 && c2 <= 0xDFFF) {
	                                writer.write(c2);
	                                i++;
	                            } else {
	                                throw RuntimeUtil.uriError("Invalid unicode surrogate pair");
	                            }
	                        } else {
	                            throw RuntimeUtil.uriError("Unterminated unicode surrogate pair");
	                        }
	                    }
	                    writer.flush();
	                } catch (IOException e) {
	                    buf.reset();
	                    continue;
	                }
	               
	                // Convert to hex
	                for (byte b : buf.toByteArray()) {
	                    sb.append("%"+String.format("%02X", Byte.valueOf(b)));
	                }
	                buf.reset();
	            }
	        }
	        return sb.toString();
	    }

	    
	    private static class Decoder {
	        private StringBuilder sb = new StringBuilder();
	        private char[] string;
	        private final BitSet reservedSet;
	        private int k;
	        
	        public Decoder(String s, BitSet reservedSet) {
	            this.reservedSet = reservedSet;
	            string = s.toCharArray();
	            k = 0;
	        }
	        
	        public String decode() {
	            while (k < string.length) {
	                char c = string[k];
	                if (c != '%') {
	                    sb.append(c);
	                } else {
	                    int start = k;
	                    int b = decodeHexEscape();
	                    k += 2;
	                    if ((b & 0x80) == 0) {
	                        if (reservedSet.get(b)) {
	                            sb.append(string,start,k-start+1);
	                        } else {
	                            sb.append((char)b);
	                        }
	                    } else {
	                        int n = 1;
	                        while ( ((b << n) & 0x80) != 0) {
	                            n++;
	                        }
	                        if (n == 1 || n > 4) {
	                            throw RuntimeUtil.uriError("Invalid UTF sequence");
	                        }
	                        byte [] octets = new byte[n];
	                        octets[0] = (byte)b;
	                        if (k + (3 * (n - 1)) >= string.length) {
	                            throw RuntimeUtil.uriError("Incomplete multi-byte escape");
	                        }
	                        for(int j=1; j<n; j++) {
	                            k++;
	                            if (string[k] != '%') {
	                                throw RuntimeUtil.uriError("Incomplete multi-byte escape");
	                            }
	                            b = decodeHexEscape();
	                            if ( (b & 0xc0) != 0x80) {
	                                throw RuntimeUtil.uriError("Invalid UTF sequence");
	                            }
	                            octets[j] = (byte)b;
	                            k += 2;
	                        }
	                        int v = utf8transformFrom(octets);
	                        if (v < 0x10000) {
	                            // A 3-(or-more)-byte UTF-8 sequence decoding to a lone
	                            // surrogate code point is an invalid (overlong/illegal)
	                            // encoding - must be rejected, not silently accepted.
	                            if (v >= 0xD800 && v <= 0xDFFF) {
	                                throw RuntimeUtil.uriError("Invalid UTF sequence");
	                            }
	                            if (reservedSet.get(v)) {
	                                sb.append(string,start,k-start+1);
	                            } else {
	                                sb.append((char)v);
	                            }
	                        } else {
	                            int l = ((v - 0x10000) & 0x3ff) | 0xDC00;
	                            int h = (((v - 0x10000)>>10) & 0x3ff) | 0xD800;
	                            sb.append((char)h).append((char)l);
	                        }
	                    }
	                }
	                k++;
	            }
	            return sb.toString();
	        }

	        /*
	           Char. number range  |        UTF-8 octet sequence
	              (hexadecimal)    |              (binary)
	           --------------------+---------------------------------------------
	           0000 0000-0000 007F | 0xxxxxxx
	           0000 0080-0000 07FF | 110xxxxx 10xxxxxx
	           0000 0800-0000 FFFF | 1110xxxx 10xxxxxx 10xxxxxx
	           0001 0000-0010 FFFF | 11110xxx 10xxxxxx 10xxxxxx 10xxxxxx
	 

	         */
	        private int utf8transformFrom(byte[] octets) {
	            byte b = octets[0];
	            if ( (b & 0x80) == 0) {
	                return b;
	            }
	            if ((b & 0xE0) == 0xC0) {
	                int h = b & 0x1f;
	                int l = bits(octets[1]);
	                int v = (h<<6)+l;
	                if (v < 0x80) {
	                    // Overlong encoding - a code point that should have used
	                    // fewer bytes.
	                    throw RuntimeUtil.uriError("Invalid UTF-8 encoding");
	                }
	                return v;
	            }
	            if ((b & 0xF0) == 0xE0) {
	                int h = b & 0xf;
	                int m = bits(octets[1]);
	                int l = bits(octets[2]);
	                int v = (h<<12)+(m<<6)+l;
	                if (v < 0x800) {
	                    throw RuntimeUtil.uriError("Invalid UTF-8 encoding");
	                }
	                return v;
	            }
	            if ((b & 0xf8) != 0xF0) {
	                throw RuntimeUtil.uriError("Invalid UTF-8 encoding");
	            }
	            int h = b & 7;
	            int m1 = bits(octets[1]);
	            int m2 = bits(octets[2]);
	            int l = bits(octets[3]);
	            int v = (h<<18)+(m1<<12)+(m2<<6)+l;
	            if (v < 0x10000 || v > 0x10FFFF) {
	                throw RuntimeUtil.uriError("Invalid UTF-8 encoding");
	            }
	            return v;
	        }
	        
	        int bits(int b) {
	            if ((b & 0xc0) != 0x80) {
	                throw RuntimeUtil.uriError("Invalid UTF-8 encoding");
	            }
	            return b & 0x3f;
	        }

	        public int decodeHexEscape() {
	            if ((k+2) >= string.length) {
	                throw RuntimeUtil.uriError("Incomplete hex literal found at LOOP_COUNT of URI");
	            }
	            int k1 = toHexDigit(string[k+1]);
	            int k2 = toHexDigit(string[k+2]);
	            if (k1 == -1 || k2 == -1){
	                throw RuntimeUtil.uriError("Invalid hex literal found in URI "+(new String(string))+" position "+k);
	            }
	            int b = 16*k1 + k2;
	            return b;
	        }

	        private int toHexDigit(char c) {
	            switch(c) {
	            case '0':
	                return 0;
	            case '1':
	                return 1;
	            case '2':
	                return 2;
	            case '3':
	                return 3;
	            case '4':
	                return 4;
	            case '5':
	                return 5;
	            case '6':
	                return 6;
	            case '7':
	                return 7;
	            case '8':
	                return 8;
	            case '9':
	                return 9;
	            case 'a':
	            case 'A':
	                return 10;
	            case 'b':
	            case 'B':
	                return 11;
	            case 'c':
	            case 'C':
	                return 12;
	            case 'd':
	            case 'D':
	                return 13;
	            case 'e':
	            case 'E':
	                return 14;
	            case 'f':
	            case 'F':
	                return 15;
	            }
	            return -1;
	        }
	    }
	    public static String decode(String s, boolean unescapeReserved) {
	        return new Decoder(s,unescapeReserved?RESERVED_SET:EMPTY_SET).decode();
	    }

	}


	// Integer.parseInt(s,16) accepts a sign: "%+1" and "%u-001" are not escapes
	private static int parseHexDigits(String val) {
		for(int j=0; j<val.length(); j++) {
			if(Character.digit(val.charAt(j),16)<0) {
				throw new NumberFormatException(val);
			}
		}
		return Integer.parseInt(val, 16);
	}
}