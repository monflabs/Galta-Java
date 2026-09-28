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
package org.monflabs.galtajs.node;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.JSParseException;
import org.monflabs.galtajs.node.ASTVarContainer.VariableDef;
import org.monflabs.galtajs.node.control.ASTDoWhile;
import org.monflabs.galtajs.node.control.ASTFor;
import org.monflabs.galtajs.node.control.ASTFunction;
import org.monflabs.galtajs.node.control.ASTIf;
import org.monflabs.galtajs.node.control.ASTWhile;
import org.monflabs.galtajs.node.control.IContextBlockContainer;
import org.monflabs.galtajs.node.control.IVarDeclarator;
import org.monflabs.galtajs.node.unaryop.ASTAbstractIncDec;
import org.monflabs.galtajs.optimizer.JSOptimizerContext;
import org.monflabs.galtajs.optimizer.JSOptimizerContext.ContextVariable;
import org.monflabs.galtajs.parser.Token;
import org.monflabs.galtajs.rt.JSResult;
import org.monflabs.galtajs.rt.JSRuntimeContext;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.builtins.standard.arguments.Arguments;
import org.monflabs.galtajs.rt.interpreter.InterpretedUnitRuntimeContext.Signal;
import org.monflabs.galtajs.rt.interpreter.JSInterpretedRuntimeContext;
import org.monflabs.galtajs.rt.interpreter.JSInterpretedRuntimeContext.VAR_TYPE;
import org.monflabs.galtajs.rt.interpreter.VariableMap;
import org.monflabs.galtajs.rt.transpiler.VarAccessor;
import org.monflabs.galtajs.transpiler.JSTranspiler;
import org.monflabs.galtajs.transpiler.context.JSTranspilerGeneratorContext;
import org.monflabs.galtajs.transpiler.context.TranspilerGeneratorFunctionContext;
import org.monflabs.galtajs.types.JSType;
import org.monflabs.util.StringFormat;


/**
 * Identifier Node.
 */
public class ASTIdentifier extends ASTNode implements IVarDeclarator {

	private String id;

	// Phase 2a: scope resolution annotation, populated by ScopeResolutionOptimizer.
	// - scopeHops = number of runtime frames between the use-site frame and the
	//   frame owning the binding; -1 means "not statically resolved" (leave the
	//   fallback slow path alone).
	// - slotIndex = the owning container's declaration-order slot for this name
	//   (VariableDef.getJavaVariableIndex()).
	// - resolvedVarType = the binding's declared kind, used by future phases to
	//   short-circuit const-write rejection or PREDECLARED-parameter semantics
	//   without a name lookup.
	// This annotation is written once during optimization (single-threaded) and
	// read at runtime from many threads; publish via volatile write of scopeHops
	// LAST so any reader that sees scopeHops>=0 also sees the other two fields.
	private int slotIndex;
	private VAR_TYPE resolvedVarType;
	private volatile byte scopeHops = -1;

	// Phase 2d: monomorphic entry cache for identifier reads. All fields final,
	// the whole record published via a single volatile-ref write - two racing
	// writers just overwrite each other with equally-valid caches. Only used
	// when the identifier resolves in the executing context's own VariableMap
	// (typical case: function parameters, locals). Cross-frame binds (globals,
	// `with`, parent-chain closures) fall through to the slow path and are
	// covered by later phases.
	private static final class IdentIC {
		final VariableMap owner;
		final int         epoch;
		final VarAccessor entry;
		IdentIC(VariableMap o, int e, VarAccessor v) { owner=o; epoch=e; entry=v; }
	}
	private volatile IdentIC ic;

	// True when this identifier appears at a declaration site (parameter name,
	// var/let/const binding position, catch clause parameter, ...). Set by
	// declareVariables(...) which is invoked exactly at those positions by the
	// binding patterns (see ASTVariableDecl, ASTArrayLiteral.Initializer,
	// ASTObjectLiteral.Initializer). Read by ScopeResolutionOptimizer to skip
	// self-marking the binding as "used" from its own declaration.
	private boolean declarationSite;

	/**
	 * Phase 2a: publish a resolved scope binding for this identifier. Written
	 * once by ScopeResolutionOptimizer; hops is stored LAST via a volatile write
	 * so any reader that observes hops!=-1 has also seen the paired slotIndex
	 * and resolvedVarType. Callers must not invoke this after runtime execution
	 * has begun on this AST.
	 */
	public void setScopeResolution(byte hops, int slotIndex, VAR_TYPE varType) {
		this.slotIndex = slotIndex;
		this.resolvedVarType = varType;
		this.scopeHops = hops;
	}
	public byte getScopeHops() {
		return scopeHops;
	}
	public int getSlotIndex() {
		return slotIndex;
	}
	public VAR_TYPE getResolvedVarType() {
		return resolvedVarType;
	}

	public ASTIdentifier(Token t, String id) {
		super(t);
		// We intern the id to save memory and save hashcode computation
		// Since Java8, interned strings are in the heap, so this is not a problem anymore
		this.id = id.intern();
	}

	@Override
	public STATEMENT_TYPE getStatementType() {
		return STATEMENT_TYPE.EXPRESSION;
	}	

    @Override
	public JSType getReturnedType() {
		// Deliberately NOT special-casing the bare global `undefined` here,
		// unlike the transpiler's own codegen (id.equals("undefined") in
		// transpileJavaExpression): that codegen only takes its shortcut
		// after walking the live scope-context chain and confirming no local
		// declaration shadows the name - getReturnedType() takes no context
		// parameter, so it can't reuse that walk. `undefined` is not a
		// reserved word (`function f(undefined){ return undefined; }` is
		// legal in sloppy mode), and a sound static check would need to
		// enumerate every declaration-binding AST shape (var/let/const,
		// params, destructuring, catch, class names and import bindings both
		// use raw Strings instead of ASTIdentifier, etc.) - disproportionate
		// for this one case, so staying at UNKNOWN instead.
    	return JSType.UNKNOWN;
    }

	@Override
	public String getNodeString() {
		return id;
	}

	public String getId() {
		return id;
	}

	@Override
	protected void init(InitContext initContext) {
		if(id.charAt(0)=='@' && !initContext.getEnvironment().supportIdentifierAtSign()) {
			throw new JSParseException(null,this,"Identifiers cannot start with '@'");
		}
		if(id.equals(Arguments.ARGUMENTS)) {
			// `arguments` inside an arrow reads the enclosing non-arrow's binding,
			// not the arrow's own frame - mark the enclosing non-arrow.
			markEnclosingNonArrowUsesArguments();
		} else if(id.equals("eval")) {
			// A direct eval() call in the body could reference `arguments` from
			// source not visible at parse time. Force the enclosing non-arrow to
			// materialize it. (This is over-eager for `eval` used as a plain
			// identifier read, but that pattern is rare and the extra allocation
			// is a rounding error at that point.)
			markEnclosingNonArrowUsesArguments();
		}
		// Mark the resolving VariableDef as "used" so the transpiler can elide
		// initArg(...) for parameters that are never referenced. Skips
		// declaration-site identifiers (those don't count as reads); also skips
		// enclosing functions that contain `with`/direct-eval, since either
		// could reach the binding dynamically - be conservative and treat every
		// same-name binding in a poisoned function as used.
		if(!declarationSite) {
			for(ASTNode n=getParent(); n!=null; n=n.getParent()) {
				if(n instanceof ASTVarContainer vc) {
					VariableDef def = vc.getOwnVariable(id);
					if(def!=null) {
						def.setUsed(true);
						break;
					}
				}
			}
		}
		super.init(initContext);
	}

	private void markEnclosingNonArrowUsesArguments() {
		for(ASTNode n=getParent(); n!=null; n=n.getParent()) {
			if(n instanceof ASTFunction f && !f.isArrow()) {
				f.setUseArguments(true);
				return;
			}
		}
	}

	@Override
    public void declareVariables(IContextBlockContainer varContainer, VAR_TYPE varType) {
		String varName = getId();
		VariableDef def = varContainer.addVarDeclaration(varName, varType, null);
		this.declarationSite = true;
		// Record the declaration site for a lexical (TDZ'd) single-identifier
		// binding so a later read can prove it's definitely-initialized on a
		// straight-line path and drop its checkTDZ guard - see
		// isTdzSafeLinearRead. Only these three kinds are ever TDZ-guarded on
		// read (see getIdentifierReadAccessor); PREDECLARED params are handled
		// separately via tdzExempt.
		if(def!=null && (varType==VAR_TYPE.LET || varType==VAR_TYPE.CONST || varType==VAR_TYPE.USING)) {
			def.setDeclSite(this);
		}
	}

	public boolean isDeclarationSite() {
		return declarationSite;
	}

	
	//
	// Interpreter
	//
	
	@Override
	public Object evaluateValue(JSInterpretedRuntimeContext context, JSResult result) {
		try {
			// Phase 2b fast path: walk the parent chain by the exact number of
			// runtime frames the optimizer counted at parse time (scopeHops),
			// then look up the binding in that target frame's VariableMap. The
			// optimizer refuses to publish scopeHops>=0 for any identifier whose
			// enclosing function/program subtree contains `with` or a direct
			// `eval` call (either of which could inject a shadowing binding at
			// runtime), so if we see scopeHops>=0 the walk is safe. A miss
			// (entry==null) falls through to the slow path - this happens for
			// top-level VAR/FUNCTION bindings that InterpretedGlobalRuntimeContext
			// routes to globalThis instead of the local VariableMap.
			byte hops = scopeHops;
			if(hops>=0) {
				JSRuntimeContext target = context;
				for(int i=0; i<hops && target!=null; i++) {
					target = target.getParent();
				}
				if(target!=null) {
					VariableMap tv = target.getVariableMap();
					if(tv!=null) {
						// Phase 2c: if the target frame has a slot array AND
						// the binding kind is slot-eligible (parameters,
						// hoisted var/function, arguments), read by
						// declaration-order index. The optimizer's slotIndex
						// was captured from VariableDef.getJavaVariableIndex()
						// at parse time; the frame's slots[] covers every
						// VariableDef, so an in-range read is safe. LET/CONST
						// intentionally fall through - their bindings live in
						// the hash table only, not in the pre-filled slot.
						if(tv.hasSlots() && resolvedVarType!=null && resolvedVarType.isSlotEligible()) {
							Object v = tv.getSlot(slotIndex);
							return v!=RuntimeUtil.NOT_AVAILABLE ? v : RuntimeUtil.UNDEFINED;
						}
						VarAccessor e = tv.getAccessor(id);
						if(e!=null) {
							Object v = RuntimeUtil.checkTDZ(e.getValue(), id);
							return v!=RuntimeUtil.NOT_AVAILABLE ? v : RuntimeUtil.UNDEFINED;
						}
					}
				}
			}
			// Phase 2d fast path: single volatile load, identity-guarded entry.
			// The owner check pins the resolving map (which must be the current
			// context's own VariableMap - the RuntimeUtil slow path walks parents
			// and consults globalThis, so caching a hit that resolved further up
			// would produce a wrong answer once a same-name binding was created
			// in a nearer scope). The epoch check catches any structural mutation
			// of the owning map since publication.
			IdentIC snap = ic;
			VariableMap ctxVars = context.getVariableMap();
			if(snap!=null && snap.owner==ctxVars && snap.epoch==ctxVars.getMutationEpoch()) {
				Object v = RuntimeUtil.checkTDZ(snap.entry.getValue(), id);
				return v!=RuntimeUtil.NOT_AVAILABLE ? v : RuntimeUtil.UNDEFINED;
			}
			// Slow path: resolve via the full chain.
			Object v = RuntimeUtil.getIdentifierValue(context,id,true);
			// Publish the cache once, only on a cold cache. Re-publishing on
			// every miss thrashes when the AST is shared across many concurrent
			// frames (recursion or multi-threaded use) - each miss would burn a
			// fresh IdentIC allocation and an extra hash probe while helping
			// nobody. A cold-cache install-once still buys same-frame hot loops
			// their expected speedup; once a snap is present, further misses
			// simply skip publish and pay only the volatile-load + guard.
			if(snap==null && ctxVars!=null) {
				VarAccessor e = ctxVars.getAccessor(id);
				if(e!=null) {
					ic = new IdentIC(ctxVars, ctxVars.getMutationEpoch(), e);
				}
			}
			return v!=RuntimeUtil.NOT_AVAILABLE ? v : RuntimeUtil.UNDEFINED;
		} catch(Throwable ex) {
			throw fillInStackTrace(ex);
		}
	}
	
	@Override
	public Signal evaluateTypeof(JSInterpretedRuntimeContext context, JSResult result) {
		try {
			// Fetch the value once: for an accessor property, re-fetching (as
			// super.evaluateTypeof() would, via evaluateValue()) invokes its getter twice.
			Object v = RuntimeUtil.getIdentifierValue(context,id,false);
			if(v==RuntimeUtil.NOT_AVAILABLE) {
				result.setValue("undefined");
				return Signal.NONE;
			}
			result.setValue(RuntimeUtil.typeof(context.getEnvironment(),v));
			return Signal.NONE;
		} catch(Throwable ex) {
			throw fillInStackTrace(ex);
		}
	}

	
	//
	// Generic evaluation before the JSResult IDENTIFIER optimization
	//

	@Override
	public Signal evaluate(JSInterpretedRuntimeContext context, JSResult result) {
		try {
			result.setValue(RuntimeUtil.getIdentifierValue(context,id,true));
			return Signal.NONE;
		} catch(Throwable ex) {
			throw fillInStackTrace(ex);
		}
	}
	
//	@Override
//	public void evaluateAssign(JSInterpretedRuntimeContext context, Object rightValue, Function<Object, Object> assigner, JSResult result, Function<Object, Object> returnOriginalValue) {
//		VarAccessor e = context.getVariableEntry(id);
//		if(e==null) {
//			JSEnvironment env = context.getEnvironment();
//			// Is the eval context here still needed?
//			// Also see the inc/dec node implementation
//			if(context.getGlobalContext().isEvalExecution()) {
//				e = context.getParent().getVariableEntry(id);
//				if(e==null && !env.mustDeclareAllVariables()) {
//					VariableMap globalVars = context.getGlobalContext().getVariableMap(true);
//					e = globalVars.createVariable(id, RuntimeUtil.UNDEFINED, VAR_TYPE.AUTO);
//				}
//			}
//			if(e==null) {
//				if(env.mustDeclareAllVariables()) {
//					throw RuntimeUtil.syntaxError("Unknown variable {0}", id);
//				}
//				VariableMap globalVars = context.getGlobalContext().getVariableMap(true);
//				e = globalVars.createVariable(id, RuntimeUtil.UNDEFINED, VAR_TYPE.AUTO);
//			}
//		} else {
//			if(e.getType()==VAR_TYPE.CONST) {
//				throw RuntimeUtil.syntaxError("Cannot assign a value to a constant {0}", id);
//			}
//		}
//		if(assigner!=null) {
//			Object oldValue = e.getValue();
//			Object calcValue = assigner.apply(oldValue);
//			context.setVariable(id,calcValue);
//			result.setValue(returnOriginalValue!=null ? returnOriginalValue.apply(oldValue) : calcValue);
//		} else {
//			context.setVariable(id,rightValue);
//			result.setValue(rightValue);
//		}
//	}

	@Override
	public void evaluateAssign(JSInterpretedRuntimeContext context, Object rightValue, Function<Object, Object> assigner, JSResult result, Function<Object, Object> returnOriginalValue) {
		evaluateAssign(context, rightValue, assigner, result, returnOriginalValue, null);
	}

	// ECMAScript 13.15.2 step 1a: a simple assignment's LeftHandSideExpression
	// reference must be resolved BEFORE its RHS is evaluated, so that a
	// `with`/direct-eval side effect during RHS evaluation (deleting the
	// with-object's property, or eval()-declaring a shadowing local binding)
	// cannot retarget where the eventual PutValue actually writes (test262
	// S11.13.1_A5_T1-T3/A6_T1-T3: e.g. "with(scope){ x = (delete scope.x, 2) }"
	// must recreate scope.x, not fall through to an outer x; "x = (eval("var
	// x"),1)" inside a function must write the OUTER x, since that's what `x`
	// resolved to before the eval created a new local shadow). preResolved,
	// when non-null, is that pre-RHS-evaluated VarAccessor (see
	// ASTAssign.evaluate(), which only pre-resolves when scopeHops<0 - the
	// scopeHops>=0 fast path below is only ever published when the optimizer
	// proved no with/eval is reachable, so its own post-RHS resolution is
	// already safe/equivalent and needs no pre-resolution). null means
	// "resolve now", preserving this method's original behavior for every
	// other caller (compound assignment, ++/--, ...), which already achieves
	// correct ordering by deferring RHS evaluation into the assigner callback.
	public void evaluateAssign(JSInterpretedRuntimeContext context, Object rightValue, Function<Object, Object> assigner, JSResult result, Function<Object, Object> returnOriginalValue, VarAccessor preResolved) {
		if(context.isStrictMode() && ("eval".equals(id) || Arguments.ARGUMENTS.equals(id) || isStrictFutureReservedWord(id))) {
			throw RuntimeUtil.syntaxError("Cannot assign to {0} in strict mode", id);
		}
		// Phase 2b fast path for assignment: same walk as evaluateValue. When
		// the target entry is found we can skip the parent-chain scan entirely,
		// but we still perform the same CONST rejection and (assigner!=null)
		// read-modify-write dance as the slow path below - stillExists() only
		// matters for object-backed bindings (globalThis, `with`), which the
		// optimizer excludes from Phase 2a annotation, so it's not needed here.
		VarAccessor e = preResolved;
		byte hops = scopeHops;
		if(e==null && hops>=0) {
			JSRuntimeContext target = context;
			for(int i=0; i<hops && target!=null; i++) {
				target = target.getParent();
			}
			if(target!=null) {
				VariableMap tv = target.getVariableMap();
				if(tv!=null) {
					// Phase 2c: slot fast path only for slot-eligible binding
					// kinds (parameters, funcName, arguments, hoisted
					// var/function) - none of which are CONST or object-backed,
					// and the optimizer's poison check has already excluded
					// `with`/direct-eval that could produce a stillExists
					// mismatch. LET/CONST at function-body top-level are
					// technically inside a slot-backed frame but their
					// binding lives in the hash table only, so they fall
					// through to the normal accessor path (which also
					// preserves the CONST rejection below).
					if(tv.hasSlots() && resolvedVarType!=null && resolvedVarType.isSlotEligible()) {
						if(assigner!=null) {
							Object oldValue = tv.getSlot(slotIndex);
							Object calcValue = assigner.apply(oldValue);
							tv.setSlot(slotIndex, calcValue);
							result.setValue(returnOriginalValue!=null ? returnOriginalValue.apply(oldValue) : calcValue);
						} else {
							tv.setSlot(slotIndex, rightValue);
							result.setValue(rightValue);
						}
						return;
					}
					e = tv.getAccessor(id);
				}
			}
		}
		if(e==null) {
			e = context.getVariableEntry(id);
		}
		if(e==null) {
			// Is the eval context here still needed?
			// Also see the inc/dec node implementation
			if(context.getGlobalContext().isEvalExecution()) {
				e = context.getParent().getVariableEntry(id);
			}

			// Look for a globalThis value
			// Eventually create the variable if permitted
			if(e==null) {
				JSEnvironment env = context.getEnvironment();
				var globalThis = context.getGlobalContext().getGlobalThis();
				// Implicit global creation (sloppy-mode `x = value`) only applies to a
				// plain assignment: a read-modify-write (++x, x+=1, ...) must first
				// GetValue the existing binding, which throws ReferenceError for an
				// unresolvable reference regardless of strict mode - unless the
				// binding already exists. hasProperty() is checked first since it
				// can't trigger a getter (important if the property's own getter
				// deletes it as a side effect - existence must be checked, not read,
				// or the getter would fire twice); the getIdentifierValue() fallback
				// only runs when that fails, to still resolve a library-provided
				// global (eval, isNaN, ...) exposed outside globalThis's own
				// property storage - those are always plain function values, never
				// getter-backed, so reading them here has no side effect to worry about.
				boolean exists = globalThis.hasProperty(id);
				if(!exists) {
					exists = RuntimeUtil.getIdentifierValue(context, id, false)!=RuntimeUtil.NOT_AVAILABLE;
				}
				boolean autoCreate = (assigner==null && !context.isStrictMode() && !env.mustDeclareAllVariables()) || exists;
				e = globalThis.getOwnVariableAccessor(id, autoCreate);
			}
			if(e==null) {
				throw RuntimeUtil.referenceError("{0} is not defined", id);
			}
		}

		if((e.getType()==VAR_TYPE.LET || e.getType()==VAR_TYPE.CONST || e.getType()==VAR_TYPE.USING) && e.getValue()==RuntimeUtil.TDZ) {
			// TDZ (ReferenceError) takes precedence over the const/using
			// reassignment checks below - a still-uninitialized binding
			// hasn't been "assigned" yet in the sense those checks care about.
			throw RuntimeUtil.referenceError("Cannot access '{0}' before initialization", id);
		}
		if(e.getType()==VAR_TYPE.CONST) {
			// Only reachable when ASTAbstractAssign's static same-function-
			// scope check didn't fire (assignment reached across a function
			// boundary from where the const was declared) - per spec this is
			// SetMutableBinding rejecting an immutable binding, a TypeError,
			// not a SyntaxError (test262 language/global-code/decl-lex.js
			// expects assert.throws(TypeError, ...) here).
			throw RuntimeUtil.typeError("Assignment to constant variable '{0}'.", id);
		}
		if(e.getType()==VAR_TYPE.USING) {
			// A using/await-using binding is immutable, same as const, but
			// (per SetMutableBinding on an immutable binding) reassignment is
			// a runtime TypeError rather than CONST's static-flavored
			// SyntaxError above - confirmed against test262's
			// using-invalid-assignment-next-expression-for.js.
			throw RuntimeUtil.typeError("Assignment to constant variable '{0}'.", id);
		}
		if(e.getType()==VAR_TYPE.FUNCTION_SELF) {
			// A named function expression's own self-reference binding is
			// immutable, but - unlike CONST - this is enforced at ASSIGNMENT
			// time (not a static SyntaxError) and is strict-mode-conditional:
			// silently no-op the write in sloppy code, throw TypeError in
			// strict code. Either way the assignment EXPRESSION itself still
			// evaluates to the assigned value, per normal JS semantics -
			// only the store itself is suppressed.
			if(context.isStrictMode()) {
				throw RuntimeUtil.typeError("Assignment to constant variable '{0}'.", id);
			}
			if(assigner!=null) {
				Object oldValue = e.getValue();
				Object calcValue = assigner.apply(oldValue);
				result.setValue(returnOriginalValue!=null ? returnOriginalValue.apply(oldValue) : calcValue);
			} else {
				result.setValue(rightValue);
			}
			return;
		}

		if(assigner!=null) {
			Object oldValue = e.getValue();
			Object calcValue = assigner.apply(oldValue);
			// SetMutableBinding's "stillExists" check: reading an object-backed
			// binding (globalThis property, `with` object) above may have deleted it
			// as a side effect (e.g. a self-deleting getter); in strict mode that
			// must throw ReferenceError rather than silently recreate the property.
			if(context.isStrictMode() && !e.stillExists()) {
				throw RuntimeUtil.referenceError("{0} is not defined", id);
			}
			//context.setVariable(id,calcValue);
			e.setValue(calcValue);
			result.setValue(returnOriginalValue!=null ? returnOriginalValue.apply(oldValue) : calcValue);
		} else {
			// SetMutableBinding's "stillExists" check (spec 9.1.1.2.5, Object
			// Environment Records): a `with`/globalThis-backed binding whose
			// property was deleted (e.g. by the RHS itself, now resolvable
			// thanks to preResolved above locking in the reference beforehand -
			// test262 assignment-operator-calls-putvalue-lref--rval-(-1).js,
			// formerly S11.13.1_A5_T4/T5) must throw ReferenceError in strict
			// mode rather than silently recreate the property - mirrors the
			// identical check already present in the assigner!=null branch
			// above, which this plain-assignment branch was missing entirely.
			if(context.isStrictMode() && !e.stillExists()) {
				throw RuntimeUtil.referenceError("{0} is not defined", id);
			}
			e.setValue(rightValue);
			//context.setVariable(id,rightValue);
			result.setValue(rightValue);
		}
	}
	
	@Override
	public boolean evaluateDelete(JSInterpretedRuntimeContext context, JSResult result) {
		for(JSRuntimeContext c=context; c!=null; c=c.getParent()) {
			if(c.hasVariable(id)) {
				return c.deleteVariable(id);
			}
		}
		if(context.isStrictMode()) {
			throw RuntimeUtil.syntaxError("Cannot delete a local variable in strict mode");
		}
		// Note: not gated on hasOwnProperty() first - deleteProperty() already
		// returns true for a non-existent key (nothing to delete), and correctly
		// checks standardObjects (e.g. NaN, Infinity) for configurability, unlike
		// hasOwnProperty() which only sees globalThis's own storage.
		return context.getGlobalContext().getGlobalThis().deleteProperty(id);
	}
    

	
	//
	// Optimizer
	//
	
	@Override
	public boolean isConstant(JSOptimizerContext context) {
		// The variable must be a 'const'
		// Note: we could enhance that later with stock objects, like Date, Math, ...
		ContextVariable var = context.resolveSymbolVar(getId());
		return var!=null && var.getVar().getVarType()==VAR_TYPE.CONST;
	}	
	
	
    
    //
    // Transpiler
    //
    
    private static abstract class IdentifierReadAccessor {
    	public abstract String getValue();
    }

    // True when this read of a lexical (let/const/using) binding is provably
    // OUT of its Temporal Dead Zone by straight-line control flow, so the
    // checkTDZ guard the caller would otherwise emit is a dead check. The
    // proof is purely structural: the read's own top-level statement executes
    // strictly after the declaration's, in the SAME statement list, without
    // the read sitting inside a nested function body. That rules out the two
    // ways textual "after" can still hit the TDZ:
    //   - a nested function/closure body runs at CALL time, unordered w.r.t.
    //     the declaration (`function g(){return d} g(); const d=1;`) - any
    //     function boundary between the read and the declaration's list => bail;
    //   - a switch CaseBlock can be entered at a later case without running an
    //     earlier case's declaration (`switch{case 1:const d=1;case 2:use(d)}`)
    //     => bail when the declaration's own list IS a CaseBlock.
    // Within one statement list, a statement can't run unless every earlier
    // one did (no other intra-list jumps), so a later statement index is a
    // sound proof of definite assignment. Conservative everywhere else:
    // anything not clearly provable keeps the guard. Deliberately does NOT
    // yet clear the guard for a read captured by a closure DEFINED after the
    // declaration (safe, but needs extra reasoning) - that's a later refinement.
    private static boolean isTdzSafeLinearRead(ASTNode readNode, VariableDef var) {
    	if(readNode==null) {
    		return false;
    	}
    	ASTNode declSite = var.getDeclSite();
    	if(declSite==null) {
    		return false;
    	}
    	// The declaration's own top-level statement, and the statement list it
    	// sits directly in. (For a `for(const d ...)` header the ancestor
    	// statement becomes the whole for-loop, whose body is a different list
    	// - a body read then can't match this list and correctly bails.)
    	ASTNode declStmt = declSite;
    	ASTNode declList = declStmt.getParent();
    	while(declList!=null && !(declList instanceof ASTStatementList)) {
    		if(declList instanceof ASTFunction) {
    			return false;
    		}
    		declStmt = declList;
    		declList = declList.getParent();
    	}
    	if(declList==null) {
    		return false;
    	}
    	if(declList instanceof org.monflabs.galtajs.node.control.ASTSwitchCaseBlock) {
    		return false;
    	}
    	// Walk up from the read to that SAME list, capturing the read's own
    	// top-level statement there. A function boundary in between means the
    	// read runs at call time - bail. Passing THROUGH inner blocks/loops is
    	// fine: entering one already required running every earlier statement
    	// of the outer list, the declaration included.
    	ASTNode readStmt = readNode;
    	ASTNode p = readStmt.getParent();
    	while(p!=null && p!=declList) {
    		if(p instanceof ASTFunction) {
    			return false;
    		}
    		readStmt = p;
    		p = p.getParent();
    	}
    	if(p==null) {
    		return false;
    	}
    	// Same list: safe iff the read's statement is strictly later than the
    	// declaration's. Both are non-hoisted (a hoisted function decl would
    	// have tripped the function-boundary bail above), so their order in the
    	// post-hoist statement array is their execution order.
    	ASTNode[] statements = ((ASTStatementList)declList).getStatements();
    	int declIdx = -1;
    	int readIdx = -1;
    	for(int i=0; i<statements.length; i++) {
    		if(statements[i]==declStmt) {
    			declIdx = i;
    		}
    		if(statements[i]==readStmt) {
    			readIdx = i;
    		}
    	}
    	return declIdx>=0 && readIdx>declIdx;
    }

    // True when this read/write of a `for(let/const i = ...; ...)` loop
    // variable is provably OUT of its Temporal Dead Zone, so the checkTDZ
    // guard is dead. A C-style for-loop's init clause is transpiled ONCE,
    // OUTSIDE the native for(;;) (see ASTFor.transpileForNode), and always
    // runs to completion before the test, increment, or body ever execute -
    // and the per-iteration fresh-binding path only ever Arrays.copyOf /
    // System.arraycopy an ALREADY-initialized value (never re-seeds TDZ, see
    // ASTFor.transpileForNode's perIteration block). So a reference anywhere
    // in the test/increment/body subtree - even inside a closure defined in
    // the body - always sees an initialized binding. The ONE TDZ-live spot is
    // a self/forward reference inside the init expression itself
    // (`for(let i = i; ...)` must still throw), which is reached through the
    // init child and correctly excluded. Unlike isTdzSafeLinearRead this needs
    // no function-boundary bail (init completes before any body call can run)
    // and no statement-order compare (the whole header/body runs post-init).
    // for-in/for-of are deliberately not covered: they extend ASTFor_, not
    // ASTFor, and their iterable expression genuinely evaluates with the name
    // still in TDZ (see TemporalDeadZoneTest case 7).
    private static boolean isTdzSafeForLoopVar(ASTNode readNode, VariableDef var) {
    	if(readNode==null) {
    		return false;
    	}
    	if(!(var.getVarType()==VAR_TYPE.LET || var.getVarType()==VAR_TYPE.CONST)) {
    		return false;
    	}
    	ASTNode declSite = var.getDeclSite();
    	if(declSite==null) {
    		return false;
    	}
    	// The container that owns this binding must be a C-style for-loop
    	// declaring it in its own init clause.
    	ASTNode owner = declSite.getParent();
    	while(owner!=null && !(owner instanceof ASTVarContainer)) {
    		owner = owner.getParent();
    	}
    	if(!(owner instanceof ASTFor forLoop)) {
    		return false;
    	}
    	// Walk up from the read to that for-loop, remembering which of its four
    	// child clauses we entered through - safe iff it wasn't the init clause.
    	ASTNode child = readNode;
    	for(ASTNode p=readNode.getParent(); p!=null; p=p.getParent()) {
    		if(p==forLoop) {
    			return child!=forLoop.getInitNode();
    		}
    		child = p;
    	}
    	return false;
    }
    private static IdentifierReadAccessor getIdentifierReadAccessor(JSTranspilerGeneratorContext jsContext, String id, boolean throwError) {
    	return getIdentifierReadAccessor(jsContext, id, throwError, null);
    }
    // readNode is the actual ASTIdentifier being read (null when the caller
    // has no node - e.g. a synthesized shorthand-property read); when present
    // it enables the linear-flow TDZ-guard elision in isTdzSafeLinearRead.
    private static IdentifierReadAccessor getIdentifierReadAccessor(JSTranspilerGeneratorContext jsContext, String id, boolean throwError, ASTNode readNode) {
    	List<Object> withParams = null;
    	VariableDef varDef = null;
    	// Set once this walk passes THROUGH (not stops at) a
    	// TranspilerEvalShadowContext - i.e. `id` isn't declared in that
    	// function's own scope, so resolution continues out to some outer
    	// slot. See TranspilerEvalShadowContext's own doc: for the
    	// overwhelming common case (no context in the chain is ever an eval-
    	// shadow boundary - true for every function that has no literal
    	// direct eval in its own body, which is nearly all of them) this
    	// stays false and the emitted code is identical to before this
    	// existed.
    	boolean crossedEvalShadow = false;
    	// How many DISTINCT function-level contexts (one per actual nested
    	// JS function, see TranspilerGeneratorFunctionContext) separate the
    	// read site from the function whose OWN eval-shadow boundary was
    	// crossed - 0 means "same function" (the common case: a literal eval
    	// and the read reaching an outer slot both live in ONE function's own
    	// body/params - jsContext.getContextJavaName() below already names
    	// THAT function's own runtime context correctly). A positive count
    	// means the read happens inside a CLOSURE declared within the eval-
    	// owning function's own parameter list (hasNonStrictDirectEvalInOwnParams()) -
    	// jsContext.getContextJavaName() would then name the WRONG (closure's
    	// own) runtime context, since Java text emitted inside the closure's
    	// generated method can't see a different method's local "_ctx" by
    	// that name. Only hop count 1 is resolved below (via the one genuine
    	// runtime handle a closure has back to its constructing function's own
    	// context - BuiltinFunction.getParentContext()); a hop count of 2+
    	// (a closure nested inside ANOTHER closure, both inside the eval-
    	// owning function's params) has no such handle available and falls
    	// back to the pre-existing (still wrong, not newly broken) behavior -
    	// not currently exercised by any known test262 file.
    	int evalShadowHops = -1;
    	int functionBoundariesSeen = 0;
    	JSTranspilerGeneratorContext lastFunctionCtx = null;
    	for(JSTranspilerGeneratorContext ctx=jsContext; ctx!=null; ctx=ctx.getParent()) {
    		if(ctx instanceof TranspilerGeneratorFunctionContext && ctx!=lastFunctionCtx) {
    			lastFunctionCtx = ctx;
    			functionBoundariesSeen++;
    		}
    		varDef = ctx.getOwnVariable(id);
    		if(varDef!=null) {
    			if(withParams==null) {
    				var var = varDef;
    				// A let/const/using slot may still hold the TDZ sentinel if
    				// this read is reached before its own declaration statement
    				// runs (or, for a shadowing name, before THIS container's own
    				// declaration - see ASTVarContainer.transpilerDeclareStatement).
    				// PREDECLARED (function parameters) gets the same treatment:
    				// every parameter slot is pre-filled with TDZ before any
    				// parameter binding runs (see ASTFunction.
    				// transpileParameterBindingPrologue), so a default-value
    				// expression referencing itself or a not-yet-bound later
    				// parameter throws ReferenceError instead of silently
    				// reading a stale/null value (test262 dflt-params-ref-self/
    				// dflt-params-ref-later).
    				boolean tdz = (var.getVarType()==VAR_TYPE.LET || var.getVarType()==VAR_TYPE.CONST || var.getVarType()==VAR_TYPE.USING || var.getVarType()==VAR_TYPE.PREDECLARED)
    						&& !var.isTdzExempt()
    						// A lexical binding read that provably runs after its
    						// own initializer on a straight-line path needs no
    						// guard - see isTdzSafeLinearRead / isTdzSafeForLoopVar.
    						&& !isTdzSafeLinearRead(readNode, var)
    						&& !isTdzSafeForLoopVar(readNode, var);
    				boolean evalShadowed = crossedEvalShadow;
    				int shadowHops = evalShadowHops;
					return new IdentifierReadAccessor() {
						@Override
						public String getValue() {
							// A named import (VariableDef.isLiveImportBinding(),
							// see its own doc comment) re-resolves live on every
							// read instead of reading this slot's own one-time
							// snapshot - a later reassignment of the exported
							// variable in the SOURCE module must be observed
							// here (test262 eval-gtbndng-indirect-update*.js/
							// instn-named-iee-cycle.js). getLiveImportReadExpression()
							// already wraps its own checkTDZ() around the
							// SOURCE's value, so the `tdz` wrap just below
							// becomes a harmless no-op for this case (its input
							// is never the TDZ sentinel - either the inner
							// checkTDZ already threw, or it's a real value).
							String raw = var.isLiveImportBinding() ? var.getLiveImportReadExpression() : var.getJavaVariableValue();
							String value = tdz ? StringFormat.format("checkTDZ({0},\"{1}\")", raw, var.getName()) : raw;
							if(!evalShadowed) {
								return value;
							}
							// This read's static resolution walked OUT of a
							// function that has its own literal, non-strict
							// direct eval() call before landing here - a
							// runtime-created shadow binding (if the eval
							// actually ran and declared this name) must win
							// over the statically-resolved value above. See
							// JSTranspiledUnit.evalShadowRead's own doc.
							// shadowHops==0: same function as the eval - its
							// own pinned context name is directly reachable.
							// shadowHops==1: the read is inside a closure
							// declared in the eval-owning function's OWN
							// parameter list - reach that function's runtime
							// context via the one handle a closure has back to
							// its constructor argument (see evalShadowHops'
							// own doc above for why 2+ isn't handled).
							String evalOwnerCtx = shadowHops==1
									? "((org.monflabs.galtajs.rt.transpiler.JSTranspiledRuntimeContext)getParentContext())"
									: jsContext.getContextJavaName();
							return shadowHops<=1
									? StringFormat.format("evalShadowRead({0},\"{1}\",{2})", evalOwnerCtx, id, value)
									: value;
						}
					};
				}
    			break;
    		} else {
	    		String withVar = ctx.getWithJavaName();
	    		if(withVar!=null) {
	    			if(withParams==null) {
	    				withParams = new ArrayList<>();
	    			}
	    			withParams.add(withVar);
	    		}
	    		if(ctx.isEvalShadowBoundary()) {
	    			crossedEvalShadow = true;
	    			if(evalShadowHops<0) {
	    				// functionBoundariesSeen currently counts function
	    				// contexts already fully passed through (the read
	    				// site's own function, if reached, counts as 1) - the
	    				// eval-owning function's own context is exactly the
	    				// NEXT one the walk will reach, so this already IS the
	    				// hop count from the read site to it (0 when jsContext
	    				// itself is the eval-shadow boundary, i.e. same
	    				// function - see evalShadowHops' own doc above).
	    				evalShadowHops = functionBoundariesSeen;
	    			}
	    		}
    		}
    	}


    	// The variable is not declared - look for the standard ones to avoid looking for an identifier
    	// (but not when an enclosing `with` could shadow it at runtime - fall
    	// through to the getIdentifierValue(...) branch below instead, which
    	// already threads withParams through correctly).
    	StringBuilder b = new StringBuilder();
    	if(withParams==null && id.equals("undefined")) {
	   		b.append("UNDEFINED");
    	} else if(withParams==null && id.equals("NaN")) {
	   		b.append("Double.NaN");
    	} else if(withParams==null && id.equals("Infinity")) {
	   		b.append("Double.POSITIVE_INFINITY");
    	} else {
	   		b.append("getIdentifierValue(");
	   		b.append(JSTranspiler.MAIN_CONTEXT);
	   		b.append(",\"");
	   		b.append(id);
	   		b.append("\"");
	   		if(withParams!=null) {
	   	   		if(varDef!=null) {
	   		   		b.append(StringFormat.format(",{0},{1}",varDef.getJavaVariable(),varDef.getJavaVariableIndex()));
	   	   		} else {
	   		   		b.append(",null,0");
	   	   		}
		   		b.append(",");
		   		b.append( throwError );
		   		for(int i=0; i<withParams.size(); i++) {
		   	   		b.append(",");
		   	   		b.append(withParams.get(i));
		   		}
	   		} else {
		   		b.append(",");
		   		b.append( throwError );
	   		}
	   		b.append(")");
    	}
		return new IdentifierReadAccessor() {
			@Override
			public String getValue() {
				return b.toString();
			}
		};
    }
    
    private static abstract class IdentifierWriteAccessor {
    	public abstract String getAccessor();
    	public String setValue(String value) {
    		return getAccessor()+".setValue("+value+")";
    	}
    	public VariableDef getVariableDef() {
    		return null;
    	}
    }

	private static IdentifierWriteAccessor getIdentifierWriteAccessor(JSTranspilerGeneratorContext jsContext, String id, boolean create, boolean isPlainAssignment) {
		List<Object> withParams = null;
		VariableDef varDef = null;
		for (JSTranspilerGeneratorContext ctx = jsContext; ctx != null; ctx = ctx.getParent()) {
			varDef = ctx.getOwnVariable(id);
			if (varDef!=null) {
				if (withParams == null) {
					var var = varDef;
					return new IdentifierWriteAccessor() {
						@Override
						public String getAccessor() {
							return var.getJavaVariable();
						}
						@Override
						public String setValue(String value) {
							// A plain "id = value" target that's statically known to
							// be const is already rejected earlier, at parse time, as
							// a SyntaxError (ASTAbstractAssign.init()) - that check
							// never looks inside a destructuring pattern, so this is
							// the only place a destructuring-leaf const violation
							// (e.g. "[c] = [1]") gets caught, and it must be a
							// genuine RUNTIME TypeError, not a parse-time failure -
							// see RuntimeUtil.throwAssignmentToConstant()'s own comment.
							if(var.getVarType()==VAR_TYPE.CONST || var.getVarType()==VAR_TYPE.USING) {
								return StringFormat.format("throwAssignmentToConstant({0},\"{1}\")", value, id);
							}
							if(var.getVarType()==VAR_TYPE.FUNCTION_SELF) {
								// A named function expression's own self-reference
								// binding is immutable, but - unlike CONST - this is
								// enforced at ASSIGNMENT time (not a static
								// SyntaxError) and is strict-mode-conditional: throw
								// in strict code, silently no-op the store in sloppy
								// code (the assignment EXPRESSION itself still
								// evaluates to the assigned value either way). The
								// owning function's strict-mode-ness is statically
								// fixed (captured at declaration time via
								// isFunctionSelfStrict(), see ASTFunction.init()), so
								// this branches at CODEGEN time rather than emitting a
								// runtime ambient-context check - mirrors
								// ASTIdentifier.evaluateAssign()'s interpreted-mode
								// equivalent. Confirmed via test262 language/
								// expressions/{function,generators,async-function,
								// async-generator}/named-*reassign-fn-name-in-body*.js.
								if(var.isFunctionSelfStrict()) {
									return StringFormat.format("throwAssignmentToConstant({0},\"{1}\")", value, id);
								}
								return StringFormat.format("identity({0})", value);
							}
							if(var.getVarType()==VAR_TYPE.LET) {
								// A plain method call (not an inline ternary) so
								// `value`'s expression is always evaluated first,
								// matching spec's evaluate-RHS-then-PutValue order.
								return StringFormat.format("putValueTDZCheck({0},{1},{2},\"{3}\")",
										var.getJavaVariable(), var.getJavaVariableIndex(), value, id);
							}
							return StringFormat.format("{0} = {1}", var.getJavaVariableValue(), value);
						}
						@Override
						public VariableDef getVariableDef() {
							return var;
						}
					};
				}
				break;
			} else {
				String withVar = ctx.getWithJavaName();
				if (withVar != null) {
					if (withParams == null) {
						withParams = new ArrayList<>();
					}
					withParams.add(withVar);
				}
			}
		}
		
		if(!create && varDef==null && withParams==null) {
			throw RuntimeUtil.error("Unknown variable {0}", id);
		}
		
		StringBuilder b = new StringBuilder();
		b.append("getIdentifierAccessor(");
		b.append(JSTranspiler.MAIN_CONTEXT);
		b.append(",\"");
		b.append(id);
		b.append("\"");
		if (withParams != null) {
   	   		if(varDef!=null) {
   		   		b.append(StringFormat.format(",{0},{1}",varDef.getJavaVariable(),varDef.getJavaVariableIndex()));
   	   		} else {
   		   		b.append(",null,0");
   	   		}
			b.append(",");
			// A read-modify-write (compound assignment, ++/--) must never
			// auto-vivify a missing global - GetValue on an unresolvable
			// reference throws ReferenceError regardless of strict mode,
			// unlike a plain assignment's sloppy-mode auto-create (see
			// ASTIdentifier.evaluateAssign's identical `assigner==null`
			// distinction on the interpreter side).
			b.append(create && isPlainAssignment);
			for (int i = 0; i < withParams.size(); i++) {
				b.append(",");
				b.append(withParams.get(i));
			}
		} else {
			b.append(",");
			b.append(isPlainAssignment);
		}
		b.append(")");
		return new IdentifierWriteAccessor() {
			@Override
			public String getAccessor() {
				return b.toString();
			}
		};
	}
	
    private static abstract class IdentifierDeleteAccessor {
    	public abstract String delete();
    }
	private static IdentifierDeleteAccessor getIdentifierDeleteAccessor(JSTranspilerGeneratorContext jsContext, String id) {
		List<Object> withParams = null;
		VariableDef varDef = null;
		for (JSTranspilerGeneratorContext ctx = jsContext; ctx != null; ctx = ctx.getParent()) {
			varDef = ctx.getOwnVariable(id);
			if (varDef != null) {
				if (withParams == null) {
					var var = varDef;
					return new IdentifierDeleteAccessor() {
						@Override
						public String delete() {
							return StringFormat.format("deleteIdentifier({0},\"{1}\",VAR_TYPE.{2},{3},{4})",JSTranspiler.MAIN_CONTEXT, id, var.getVarType(), var.getJavaVariable(), var.getJavaVariableIndex());
						}
					};
				}
				break;
			} else {
				String withVar = ctx.getWithJavaName();
				if (withVar != null) {
					if (withParams == null) {
						withParams = new ArrayList<>();
					}
					withParams.add(withVar);
				}
			}
		}
		
		if(varDef==null && withParams==null) {
			return new IdentifierDeleteAccessor() {
				@Override
				public String delete() {
					return StringFormat.format("deleteIdentifier({0},\"{1}\")",JSTranspiler.MAIN_CONTEXT, id);
				}
			};			
		}
		
		StringBuilder b = new StringBuilder();
		b.append("deleteIdentifier(");
		b.append(JSTranspiler.MAIN_CONTEXT);
		b.append(",\"");
		b.append(id);
		b.append("\"");
		if (withParams != null) {
			if(varDef!=null) {
				b.append(StringFormat.format(",VAR_TYPE.{0},{1},{2}", varDef.getVarType(), varDef.getJavaVariable(), varDef.getJavaVariableIndex()));				
			} else {
				b.append(",null,null,0");
			}
			for (int i = 0; i < withParams.size(); i++) {
				b.append(",");
				b.append(withParams.get(i));
			}
		}
		b.append(")");
		return new IdentifierDeleteAccessor() {
			@Override
			public String delete() {
				return b.toString();
			}
		};
	}
	
    @Override
    public String transpileJavaExpression(JSTranspilerGeneratorContext jsContext) {
    	// Pass `this` (the read node) so the linear-flow TDZ-guard elision can
    	// see where this read sits relative to its binding's declaration.
    	return getIdentifierReadAccessor(jsContext,getId(),true,this).getValue();
    }

    public static final String transpileJavaExpression(JSTranspilerGeneratorContext jsContext, String id) {
    	return getIdentifierReadAccessor(jsContext,id,true).getValue();
    }

    
    @Override
    public String transpileTypeofExpression(JSTranspilerGeneratorContext jsContext) {
	   	return StringFormat.format("typeof({0})", 
	   			getIdentifierReadAccessor(jsContext,id,false).getValue());
    }
    
    @Override
    public String transpileDeleteExpression(JSTranspilerGeneratorContext jsContext) {
    	return getIdentifierDeleteAccessor(jsContext,id).delete();
    }

    // A read-modify-write (compound assignment, ++/--) reads the CURRENT slot
    // value directly (bypassing getIdentifierReadAccessor's own TDZ wrap,
    // which only covers a plain read expression) - guard it here too, so
    // e.g. `x += 1` on a still-uninitialized let throws instead of silently
    // operating on the TDZ sentinel.
    private String tdzGuardedRead(VariableDef varDef) {
    	String raw = varDef.getJavaVariableValue();
    	if((varDef.getVarType()==VAR_TYPE.LET || varDef.getVarType()==VAR_TYPE.CONST)
    			// A compound-assign read of a for-loop variable in the loop's
    			// test/increment/body is past the TDZ - same proof as the plain
    			// read path (see isTdzSafeForLoopVar).
    			&& !isTdzSafeForLoopVar(this, varDef)) {
    		return StringFormat.format("checkTDZ({0},\"{1}\")", raw, varDef.getName());
    	}
    	return raw;
    }

    @Override
	public String transpileJavaAssignment(JSTranspilerGeneratorContext jsContext, ASSIGN_TYPE type, String rightValue, boolean sequence, boolean returnOriginalValue) {
    	if(sequence) {
    		rightValue = StringFormat.format("deref({0})", rightValue);
    	}
    	// Variable defined in scope
    	IdentifierWriteAccessor varAccessor = getIdentifierWriteAccessor(jsContext,id,!jsContext.getOptions().isMustDeclareVariables(),type==ASSIGN_TYPE.EQUALS);
    	switch(type) {
    		case EQUALS -> {
				return varAccessor.setValue(rightValue);
    		}
    	
    		case EQUALS_ADD -> {
    			VariableDef varDef = varAccessor.getVariableDef();
    			if(varDef!=null ) { 
    				String ve = tdzGuardedRead(varDef);
    				return varAccessor.setValue(StringFormat.format("add({0},{1})",ve,rightValue)); 
    			}
	    		return StringFormat.format("assignAdd({0},{1})",varAccessor.getAccessor(),rightValue);
    		}
    		case EQUALS_AND -> {
    			VariableDef varDef = varAccessor.getVariableDef();
    			if(varDef!=null ) { 
    				String ve = tdzGuardedRead(varDef);
    				return varAccessor.setValue(StringFormat.format("and({0},()->{1})",ve,rightValue)); 
    			}
	    		return StringFormat.format("assignAnd({0},()->{1})",varAccessor.getAccessor(),rightValue);
    		}
    		case EQUALS_BITAND -> {
    			VariableDef varDef = varAccessor.getVariableDef();
    			if(varDef!=null ) { 
    				String ve = tdzGuardedRead(varDef);
    				return varAccessor.setValue(StringFormat.format("bitAnd({0},{1})",ve,rightValue)); 
    			}
	    		return StringFormat.format("assignBitAnd({0},{1})",varAccessor.getAccessor(),rightValue);
    		}
    		case EQUALS_BITOR -> {
    			VariableDef varDef = varAccessor.getVariableDef();
    			if(varDef!=null ) { 
    				String ve = tdzGuardedRead(varDef);
    				return varAccessor.setValue(StringFormat.format("bitOr({0},{1})",ve,rightValue)); 
    			}
	    		return StringFormat.format("assignBitOr({0},{1})",varAccessor.getAccessor(),rightValue);
    		}
    		case EQUALS_BITXOR -> {
    			VariableDef varDef = varAccessor.getVariableDef();
    			if(varDef!=null ) { 
    				String ve = tdzGuardedRead(varDef);
    				return varAccessor.setValue(StringFormat.format("bitXor({0},{1})",ve,rightValue)); 
    			}
	    		return StringFormat.format("assignBitXor({0},{1})",varAccessor.getAccessor(),rightValue);
    		}
    		case EQUALS_DIV -> {
    			VariableDef varDef = varAccessor.getVariableDef();
    			if(varDef!=null ) { 
    				String ve = tdzGuardedRead(varDef);
    				return varAccessor.setValue(StringFormat.format("div({0},{1})",ve,rightValue)); 
    			}
	    		return StringFormat.format("assignDiv({0},{1})",varAccessor.getAccessor(),rightValue);
    		}
    		case EQUALS_LSHIFT -> {
    			VariableDef varDef = varAccessor.getVariableDef();
    			if(varDef!=null ) { 
    				String ve = tdzGuardedRead(varDef);
    				return varAccessor.setValue(StringFormat.format("lshift({0},{1})",ve,rightValue)); 
    			}
	    		return StringFormat.format("assignLShift({0},{1})",varAccessor.getAccessor(),rightValue);
    		}
    		case EQUALS_MOD -> {
    			VariableDef varDef = varAccessor.getVariableDef();
    			if(varDef!=null ) { 
    				String ve = tdzGuardedRead(varDef);
    				return varAccessor.setValue(StringFormat.format("mod({0},{1})",ve,rightValue)); 
    			}
	    		return StringFormat.format("assignMod({0},{1})",varAccessor.getAccessor(),rightValue);
    		}
    		case EQUALS_MUL -> {
    			VariableDef varDef = varAccessor.getVariableDef();
    			if(varDef!=null ) { 
    				String ve = tdzGuardedRead(varDef);
    				return varAccessor.setValue(StringFormat.format("mul({0},{1})",ve,rightValue)); 
    			}
	    		return StringFormat.format("assignMul({0},{1})",varAccessor.getAccessor(),rightValue);
    		}
    		case EQUALS_NULLCOALESCING -> {
    			VariableDef varDef = varAccessor.getVariableDef();
    			if(varDef!=null ) { 
    				String ve = tdzGuardedRead(varDef);
    				return varAccessor.setValue(StringFormat.format("nullCoalescing({0},()->{1})",ve,rightValue)); 
    			}
	    		return StringFormat.format("assignNullCoalescing({0},()->{1})",varAccessor.getAccessor(),rightValue);
    		}
    		case EQUALS_OR -> {
    			VariableDef varDef = varAccessor.getVariableDef();
    			if(varDef!=null ) { 
    				String ve = tdzGuardedRead(varDef);
    				return varAccessor.setValue(StringFormat.format("or({0},()->{1})",ve,rightValue)); 
    			}
	    		return StringFormat.format("assignOr({0},()->{1})",varAccessor.getAccessor(),rightValue);
    		}
    		case EQUALS_POWER -> {
    			VariableDef varDef = varAccessor.getVariableDef();
    			if(varDef!=null ) { 
    				String ve = tdzGuardedRead(varDef);
    				return varAccessor.setValue(StringFormat.format("power({0},{1})",ve,rightValue)); 
    			}
	    		return StringFormat.format("assignPower({0},{1})",varAccessor.getAccessor(),rightValue);
    		}
    		case EQUALS_RSHIFT -> {
    			VariableDef varDef = varAccessor.getVariableDef();
    			if(varDef!=null ) { 
    				String ve = tdzGuardedRead(varDef);
    				return varAccessor.setValue(StringFormat.format("rshift({0},{1})",ve,rightValue)); 
    			}
	    		return StringFormat.format("assignRShift({0},{1})",varAccessor.getAccessor(),rightValue);
    		}
    		case EQUALS_RUNSHIFT -> {
    			VariableDef varDef = varAccessor.getVariableDef();
    			if(varDef!=null ) { 
    				String ve = tdzGuardedRead(varDef);
    				return varAccessor.setValue(StringFormat.format("runshift({0},{1})",ve,rightValue)); 
    			}
	    		return StringFormat.format("assignRunShift({0},{1})",varAccessor.getAccessor(),rightValue);
    		}
    		case EQUALS_SUB -> {
    			VariableDef varDef = varAccessor.getVariableDef();
    			if(varDef!=null ) { 
    				String ve = tdzGuardedRead(varDef);
    				return varAccessor.setValue(StringFormat.format("sub({0},{1})",ve,rightValue)); 
    			}
	    		return StringFormat.format("assignSub({0},{1})",varAccessor.getAccessor(),rightValue);
    		}

    		case PREINC -> {
    			VariableDef varDef = varAccessor.getVariableDef();
    			if(varDef!=null ) {
    				if(varDef.getVarType()==VAR_TYPE.CONST || varDef.getVarType()==VAR_TYPE.USING) {
    					return StringFormat.format("incDecVarReadOnly({0},{1},\"{2}\")", varDef.getJavaVariable(), varDef.getJavaVariableIndex(), varDef.getName());
    				}
    				boolean tdz = varDef.getVarType()==VAR_TYPE.LET && !isTdzSafeForLoopVar(this, varDef);
    				if(needReturnedValue()) {
    					String fn = tdz ? "preIncVarChecked({0},{1},\"{2}\")" : "preIncVar({0},{1})";
        				return StringFormat.format(fn,varDef.getJavaVariable(),varDef.getJavaVariableIndex(),varDef.getName());
    				} else {
        				String ve = varDef.getJavaVariableValue();
        				return tdz
        					? StringFormat.format("{0}=incNumber(checkTDZ({0},\"{1}\"))",ve,varDef.getName())
        					: StringFormat.format("{0}=incNumber({0})",ve);
    				}
    			}
	    		return StringFormat.format("preInc({0})",varAccessor.getAccessor());
    		}
    		case POSTINC -> {
    			VariableDef varDef = varAccessor.getVariableDef();
    			if(varDef!=null ) {
    				if(varDef.getVarType()==VAR_TYPE.CONST || varDef.getVarType()==VAR_TYPE.USING) {
    					return StringFormat.format("incDecVarReadOnly({0},{1},\"{2}\")", varDef.getJavaVariable(), varDef.getJavaVariableIndex(), varDef.getName());
    				}
    				boolean tdz = varDef.getVarType()==VAR_TYPE.LET && !isTdzSafeForLoopVar(this, varDef);
    				if(needReturnedValue()) {
    					String fn = tdz ? "postIncVarChecked({0},{1},\"{2}\")" : "postIncVar({0},{1})";
        				return StringFormat.format(fn,varDef.getJavaVariable(),varDef.getJavaVariableIndex(),varDef.getName());
    				} else {
        				String ve = varDef.getJavaVariableValue();
        				return tdz
        					? StringFormat.format("{0}=incNumber(checkTDZ({0},\"{1}\"))",ve,varDef.getName())
        					: StringFormat.format("{0}=incNumber({0})",ve);
    				}
    			}
	    		return StringFormat.format("postInc({0})",varAccessor.getAccessor());
    		}
    		case PREDEC -> {
    			VariableDef varDef = varAccessor.getVariableDef();
    			if(varDef!=null ) {
    				if(varDef.getVarType()==VAR_TYPE.CONST || varDef.getVarType()==VAR_TYPE.USING) {
    					return StringFormat.format("incDecVarReadOnly({0},{1},\"{2}\")", varDef.getJavaVariable(), varDef.getJavaVariableIndex(), varDef.getName());
    				}
    				boolean tdz = varDef.getVarType()==VAR_TYPE.LET && !isTdzSafeForLoopVar(this, varDef);
    				if(needReturnedValue()) {
    					String fn = tdz ? "preDecVarChecked({0},{1},\"{2}\")" : "preDecVar({0},{1})";
        				return StringFormat.format(fn,varDef.getJavaVariable(),varDef.getJavaVariableIndex(),varDef.getName());
    				} else {
        				String ve = varDef.getJavaVariableValue();
        				return tdz
        					? StringFormat.format("{0}=decNumber(checkTDZ({0},\"{1}\"))",ve,varDef.getName())
        					: StringFormat.format("{0}=decNumber({0})",ve);
    				}
    			}
	    		return StringFormat.format("preDec({0})",varAccessor.getAccessor());
    		}
    		case POSTDEC -> {
    			VariableDef varDef = varAccessor.getVariableDef();
    			if(varDef != null) {
    				if(varDef.getVarType()==VAR_TYPE.CONST || varDef.getVarType()==VAR_TYPE.USING) {
    					return StringFormat.format("incDecVarReadOnly({0},{1},\"{2}\")", varDef.getJavaVariable(), varDef.getJavaVariableIndex(), varDef.getName());
    				}
    				boolean tdz = varDef.getVarType()==VAR_TYPE.LET && !isTdzSafeForLoopVar(this, varDef);
    				if(needReturnedValue()) {
    					String fn = tdz ? "postDecVarChecked({0},{1},\"{2}\")" : "postDecVar({0},{1})";
        				return StringFormat.format(fn,varDef.getJavaVariable(),varDef.getJavaVariableIndex(),varDef.getName());
    				} else {
        				String ve = varDef.getJavaVariableValue();
        				return tdz
        					? StringFormat.format("{0}=decNumber(checkTDZ({0},\"{1}\"))",ve,varDef.getName())
        					: StringFormat.format("{0}=decNumber({0})",ve);
    				}
    			}
	    		return StringFormat.format("postDec({0})",varAccessor.getAccessor());
    		}
    	}
    	return super.transpileJavaAssignment(jsContext, type, rightValue, sequence, returnOriginalValue);
    }
    private boolean needReturnedValue() {
    	// Parent of the op
    	ASTNode incDec = getParent();
    	if(incDec instanceof ASTAbstractIncDec) {
    		ASTNode parent = incDec.getParent();
	    	if(parent instanceof ASTFor n) {
	    		return incDec==n.getTestNode(); 
	    	}
	    	if(parent instanceof ASTIf n) {
	    		return incDec==n.getTestNode(); 
	    	}
	    	if(parent instanceof ASTWhile n) {
	    		return incDec==n.getTestNode(); 
	    	}
	    	if(parent instanceof ASTDoWhile n) {
	    		return incDec==n.getTestNode(); 
	    	}
	    	if(parent instanceof ASTStatementList) {
	    		return false; 
	    	}
    	}
    	
    	return true;
    }

    @Override
	public String decompileExpression() {
    	return id;
	}

}
