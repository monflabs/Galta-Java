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
package org.monflabs.galtajs.optimizer;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

import org.monflabs.galtajs.node.ASTIdentifier;
import org.monflabs.galtajs.node.ASTNode;
import org.monflabs.galtajs.node.ASTProgram;
import org.monflabs.galtajs.node.ASTVarContainer;
import org.monflabs.galtajs.node.ASTVarContainer.VariableDef;
import org.monflabs.galtajs.node.call.ASTCall;
import org.monflabs.galtajs.node.control.ASTBlock;
import org.monflabs.galtajs.node.control.ASTFor;
import org.monflabs.galtajs.node.control.ASTFor_;
import org.monflabs.galtajs.node.clazz.ASTClassDecl;
import org.monflabs.galtajs.node.control.ASTFunction;
import org.monflabs.galtajs.node.control.ASTTry;
import org.monflabs.galtajs.node.control.ASTWith;
import org.monflabs.galtajs.node.literal.ASTStringTemplate;
import org.monflabs.galtajs.rt.interpreter.JSInterpretedRuntimeContext.VAR_TYPE;

/**
 * Phase 2a: resolve every {@link ASTIdentifier} read against its declaring
 * scope and publish the annotation (scopeHops, slotIndex, resolvedVarType) on
 * the identifier itself.
 *
 * This optimizer is pure metadata - it does not mutate AST structure. The
 * annotation is only meaningful to later phases that opt into reading it; any
 * runtime read path that ignores the annotation keeps its existing behavior
 * unchanged, so this pass is safe to run unconditionally.
 *
 * <p><b>Runtime frame model</b>
 * Only these nodes create a runtime frame that a lexical binding actually
 * lives in:
 * <ul>
 *   <li>{@link ASTProgram} - always the outermost frame.</li>
 *   <li>{@link ASTFunction} - always, one frame per call.</li>
 *   <li>{@link ASTBlock} - <b>only when it declares variables</b>
 *       (see ASTBlock.evaluate: no InterpretedBlockRuntimeContext is created
 *       for a decl-less block; statements execute in the outer frame).</li>
 *   <li>{@link ASTFor} - always, one block frame per for-statement.</li>
 *   <li>{@link ASTFor_} ({@code for-of}/{@code for-in}) - always, a fresh
 *       block frame per iteration (see ASTForOf/ASTForIn.evaluate(): an
 *       unconditional {@code new InterpretedBlockRuntimeContext(context)}
 *       every time round the loop, regardless of var/let/const).</li>
 * </ul>
 * {@link ASTStringTemplate} is NOT a frame: its {@code ${...}} substitutions
 * evaluate directly against the ambient context (see the note in walk()).
 * ASTCatch is intentionally not a frame: it hoists its binding onto its body
 * block during init(), and the body block is what creates the runtime frame.
 *
 * <p><b>Bail-outs (identifier left un-annotated)</b>
 * <ul>
 *   <li>Identifier not resolved by any enclosing frame (true global).</li>
 *   <li>Any enclosing function/program subtree contains a {@code with}
 *       statement or a direct call to the {@code eval} intrinsic - either can
 *       inject a same-named binding at runtime that this static walk cannot
 *       see.</li>
 *   <li>Any enclosing function whose parameter list "hasParameterExpressions"
 *       (a default value, a destructuring pattern, or a rest parameter -
 *       anything beyond a flat list of plain identifiers; see {@code
 *       ASTFunction.isSimpleParameterList()}) - at runtime such a function
 *       splits into TWO chained frames instead of one (the parameter frame,
 *       still modeled by this pass's Scope for the {@code ASTFunction} node,
 *       plus a second body frame the parameter frame doesn't know about -
 *       see {@code InterpretedFunctionBodyRuntimeContext} and
 *       {@code BuiltinFunctionInterpreter.bindParametersAndVars}). This
 *       pass's static one-frame-per-{@code ASTFunction} model (see "Runtime
 *       frame model" above) has no representation for that extra frame, so
 *       any hop count computed through it would be wrong for a body-level
 *       declaration (and every deeper nested closure). Poisoning forces
 *       every identifier that could resolve into either of the two frames
 *       onto the slow, name-based path instead, which walks the ACTUAL
 *       runtime parent chain and is correct regardless of frame count.
 *   <li>Any enclosing function/program subtree directly contains a class
 *       declaration/expression - {@code ASTClassDecl.evaluate()} wraps ALL
 *       class-body element evaluation (methods, getters/setters, fields,
 *       static blocks - both the class-definition-time {@code initClass} pass
 *       and the per-instance {@code initInstance} pass) in its own
 *       unconditional extra {@code InterpretedBlockRuntimeContext}, so every
 *       class member function's parent chain has one MORE hop than this walk
 *       would otherwise count. Unlike the {@code ASTTry}/{@code ASTFor_}
 *       cases above, this extra frame isn't modeled
 *       precisely (class bodies have several different wrapping contexts -
 *       static vs. instance, methods vs. field initializers - that would need
 *       individually correct hop-counting); poisoning is the conservative,
 *       always-correct fallback instead. See {@code accessor-name-*-computed-
 *       yield-expr.js} in test262 (found via a class nested in a plain
 *       function, not generator/yield-specific despite the test name).</li>
 *   <li>A reference inside a function's OWN parameter list (default value
 *       expressions, destructuring pattern defaults) that resolves to a
 *       binding in that SAME parameter list - parameters initialize strictly
 *       left-to-right, so a forward (or self) reference is a genuine TDZ
 *       {@code ReferenceError}, not a value to read. All parameters are
 *       pre-registered in the function's Scope up front (so the static walk
 *       can't tell "already initialized" from "not yet"), unlike a real
 *       runtime slot array, which starts empty and fills in as each
 *       parameter binds - see {@code dflt-params-ref-later.js} in test262.</li>
 * </ul>
 */
public class ScopeResolutionOptimizer extends NodeOptimizer {

	private static final String EVAL_NAME = "eval";
	private static final byte MAX_HOPS = Byte.MAX_VALUE;

	// This optimizer is a shared static singleton (see ScriptOptimizer.
	// DEFAULT_NODE_OPTIMIZER_NODES), reused across every script ever compiled for
	// the life of the JVM - so the poisoned-function cache must NOT be an instance
	// field: an IdentityHashMap that grows by one entry per ASTFunction/ASTProgram
	// node compiled, never evicted, would pin every compiled script's AST in
	// memory forever (a real, confirmed OOM running the full test262 suite in one
	// process). Scoped to a single optimize() call instead, via a parameter.

	@Override
	public void optimize(JSOptimizerContext context, ASTNode node) {
		if(node==null) {
			return;
		}
		walk(null, node, new IdentityHashMap<>(), null);
	}

	private void walk(Scope scope, ASTNode node, Map<ASTNode,Boolean> functionPoisoned, Scope insideParamsOf) {
		if(node==null) {
			return;
		}

		if(node instanceof ASTIdentifier ident) {
			resolve(scope, ident, functionPoisoned, insideParamsOf);
			return;
		}

		Scope inner = enter(scope, node);

		// ASTTry unconditionally wraps each of its try body / catch clause /
		// finally block in a fresh InterpretedBlockRuntimeContext at runtime
		// (see ASTTry.evaluate) - regardless of whether those children declare
		// any variables. Model that as an extra synthetic Scope around each
		// child walk so scopeHops matches the runtime chain length. Without
		// this the walk under-counts by 1, and the identifier fast path lands
		// on the wrong parent frame at runtime - most visibly a slot-fast-path
		// read into a smaller-than-expected slot array, IndexOutOfBounds. The
		// ASTCatch case additionally has its OWN wrapping block frame from the
		// catch parameter being hoisted into the body block, so the outer
		// synthetic Scope compounds with the inner block-with-vars frame.
		if(node instanceof ASTTry tr) {
			walk(syntheticBlock(inner, tr), tr.getBodyNode(), functionPoisoned, null);
			walk(syntheticBlock(inner, tr), tr.getCatchNode(), functionPoisoned, null);
			walk(syntheticBlock(inner, tr), tr.getFinallyNode(), functionPoisoned, null);
			return;
		}

		// ASTForIn/ASTForOf's own collection expression (child 1) evaluates
		// directly against the OUTER context, before any per-iteration frame
		// exists - UNLESS the loop declares LET/CONST/USING variables, in
		// which case ASTForIn/ASTForOf.evaluate() pre-creates an extra
		// `headContext` (an InterpretedBlockRuntimeContext wrapping `context`,
		// populated with this loop's own TDZ bindings) and evaluates the
		// collection expression THROUGH that, specifically so a self-
		// referencing collection (`let x=1; for (let x of [x]) {}`) resolves
		// to the loop's OWN (TDZ'd) `x`, not the outer one. When that extra
		// frame exists, the collection expression must be walked with `inner`
        // (the synthetic per-iteration Scope), matching the runtime chain -
		// otherwise an OUTER-scope reference in the collection expression is
		// under-counted by one hop and the fast path reads a slot in the
		// wrong (too-near) frame instead of falling through correctly to the
		// real outer binding (confirmed via a nested-function reproduction:
		// `for (let [w,x,y,z] of outerVar)` inside a function whose OWN frame
		// happens to have >=2 slots resolves `outerVar` to one of THOSE slots
		// instead of the real outer var - VariableMap.getSlot()
		// ArrayIndexOutOfBoundsException when the wrong frame's slot array is
		// too short, or a silently wrong value when it isn't). No such extra
		// frame exists for a plain `for (x of arr)`/`for (var x of arr)` (no
		// LET/CONST/USING declarations), so `scope` (outer) is still correct
		// there.
		if(node instanceof ASTFor_ fr) {
			Scope collectionScope = declaresLexicalLoopVariable(fr) ? inner : scope;
			walk(inner, node.getChild(0), functionPoisoned, insideParamsOf);
			walk(collectionScope, node.getChild(1), functionPoisoned, insideParamsOf);
			walk(inner, node.getChild(2), functionPoisoned, insideParamsOf);
			return;
		}

		// A function's own parameter list (always child 0 - see
		// ASTFunction.getChild()) is walked with insideParamsOf=inner, marking
		// "any same-scope (hops==0) resolution found while walking this subtree
		// is a forward/self reference within MY OWN parameter list" - see the
		// class doc's bail-out list. Body statements (every other child) are
		// always safe: by the time body code runs, FunctionDeclarationInstantiation
		// has already fully bound every parameter, so insideParamsOf resets to
		// null for them (not inherited from an outer call - being inside body
		// code is never "inside a parameter list" of anything relevant).
		if(node instanceof ASTFunction fn) {
			walk(inner, fn.getParameters(), functionPoisoned, inner);
			int count = node.getChildCount();
			for(int i=1; i<count; i++) {
				walk(inner, node.getChild(i), functionPoisoned, null);
			}
			return;
		}

		// ASTStringTemplate deliberately gets NO special case: its
		// substitution expressions evaluate directly against the ambient
		// context (see ASTStringTemplate.evaluateValue() - a substitution
		// declares nothing, so no InterpretedBlockRuntimeContext is created),
		// exactly like any other expression-position child. Modeling a
		// synthetic per-substitution frame here (as an earlier version of the
		// runtime once had) over-counts every identifier inside a template
		// literal by one hop - invisible at top level (a program-frame miss
		// falls back to the name-based path) but wrong inside any nested
		// function, where the extra hop lands on the ENCLOSING function's
		// slot array: a silently wrong value, or an IndexOutOfBounds when that
		// array is too short (test262 built-ins/Iterator/zip*/*-iteration.js).

		int count = node.getChildCount();
		for(int i=0; i<count; i++) {
			walk(inner, node.getChild(i), functionPoisoned, insideParamsOf);
		}
	}

	// Mirrors ASTForOf/ASTForIn.evaluate()'s own condition for creating the
	// extra `headContext` frame: true only when the loop declares at least
	// one LET/CONST/USING variable (a bare/`var`-headed loop never creates
	// this frame).
	private boolean declaresLexicalLoopVariable(ASTFor_ fr) {
		if(!fr.hasDeclaredVariables()) {
			return false;
		}
		for(VariableDef v: fr.getVariables()) {
			VAR_TYPE t = v.getVarType();
			if(t==VAR_TYPE.LET || t==VAR_TYPE.CONST || t==VAR_TYPE.USING) {
				return true;
			}
		}
		return false;
	}

	private Scope syntheticBlock(Scope outer, ASTNode owner) {
		return new Scope(outer, owner, /*isFunctionOrProgram*/ false, /*noVars*/ true);
	}

	/**
	 * Returns a new Scope framed by {@code node} if the node creates a runtime
	 * frame at execution time; otherwise returns {@code scope} unchanged.
	 */
	private Scope enter(Scope outer, ASTNode node) {
		if(node instanceof ASTProgram p) {
			return new Scope(outer, p, /*isFunctionOrProgram*/ true);
		}
		if(node instanceof ASTFunction f) {
			return new Scope(outer, f, /*isFunctionOrProgram*/ true);
		}
		if(node instanceof ASTFor fr) {
			return new Scope(outer, fr, /*isFunctionOrProgram*/ false);
		}
		if(node instanceof ASTFor_ fr) {
			return new Scope(outer, fr, /*isFunctionOrProgram*/ false);
		}
		if(node instanceof ASTBlock bl && bl.hasDeclaredVariables()) {
			return new Scope(outer, bl, /*isFunctionOrProgram*/ false);
		}
		return outer;
	}

	private void resolve(Scope scope, ASTIdentifier ident, Map<ASTNode,Boolean> functionPoisoned, Scope insideParamsOf) {
		if(scope==null) {
			return;
		}
		String name = ident.getId();
		int hops = 0;
		for(Scope cursor=scope; cursor!=null; cursor=cursor.parent) {
			VariableDef def = cursor.lookup(name);
			if(def!=null) {
				if(hops>MAX_HOPS) {
					return;
				}
				if(hops==0 && cursor==insideParamsOf) {
					// Forward/self reference within the same parameter list - see
					// the class doc's bail-out list. Leave un-annotated so the
					// slow path's real (order-sensitive) lookup decides.
					return;
				}
				if(anyEnclosingFunctionPoisoned(scope, functionPoisoned)) {
					return;
				}
				ident.setScopeResolution((byte)hops, def.getJavaVariableIndex(), def.getVarType());
				return;
			}
			hops++;
		}
	}

	private boolean anyEnclosingFunctionPoisoned(Scope scope, Map<ASTNode,Boolean> functionPoisoned) {
		for(Scope s=scope; s!=null; s=s.parent) {
			if(s.isFunctionOrProgram && isFunctionPoisoned(s.owner, functionPoisoned)) {
				return true;
			}
		}
		return false;
	}

	private boolean isFunctionPoisoned(ASTNode fn, Map<ASTNode,Boolean> functionPoisoned) {
		Boolean v = functionPoisoned.get(fn);
		if(v!=null) {
			return v;
		}
		// hasParameterExpressions (see class doc's bail-out list) splits this
		// function into two runtime frames instead of one - an O(1) check on
		// the node itself, no subtree scan needed like the with/eval/class
		// cases below.
		boolean p = (fn instanceof ASTFunction f && !f.isSimpleParameterList())
				|| scanForPoison(fn, /*insideNestedFn*/ false);
		functionPoisoned.put(fn, p);
		return p;
	}

	private boolean scanForPoison(ASTNode node, boolean insideNestedFn) {
		if(node==null) {
			return false;
		}
		if(node instanceof ASTWith) {
			return true;
		}
		if(node instanceof ASTClassDecl) {
			return true;
		}
		if(node instanceof ASTCall call) {
			ASTNode callee = call.getNode();
			if(callee instanceof ASTIdentifier id && EVAL_NAME.equals(id.getId())) {
				return true;
			}
		}
		boolean descendIntoInnerFn = insideNestedFn;
		if(node instanceof ASTFunction) {
			if(insideNestedFn) {
				return false;
			}
			descendIntoInnerFn = true;
		}
		int count = node.getChildCount();
		for(int i=0; i<count; i++) {
			if(scanForPoison(node.getChild(i), descendIntoInnerFn)) {
				return true;
			}
		}
		return false;
	}

	/** Compile-time analogue of a runtime frame. */
	private static final class Scope {
		final Scope parent;
		final ASTNode owner;
		final boolean isFunctionOrProgram;
		final Map<String,VariableDef> vars;

		Scope(Scope parent, ASTNode owner, boolean isFunctionOrProgram) {
			this(parent, owner, isFunctionOrProgram, /*noVars*/ false);
		}
		Scope(Scope parent, ASTNode owner, boolean isFunctionOrProgram, boolean noVars) {
			this.parent = parent;
			this.owner = owner;
			this.isFunctionOrProgram = isFunctionOrProgram;
			Map<String,VariableDef> m = null;
			if(!noVars && owner instanceof ASTVarContainer vc && vc.hasDeclaredVariables()) {
				m = new HashMap<>();
				for(VariableDef v: vc.getVariables()) {
					m.put(v.getName(), v);
				}
			}
			this.vars = m;
		}

		VariableDef lookup(String name) {
			return vars!=null ? vars.get(name) : null;
		}
	}
}
