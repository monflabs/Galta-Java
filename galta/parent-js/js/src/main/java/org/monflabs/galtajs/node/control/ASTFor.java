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
package org.monflabs.galtajs.node.control;

import org.monflabs.galtajs.node.ASTExpression;
import org.monflabs.galtajs.node.ASTIdentifier;
import org.monflabs.galtajs.node.ASTNode;
import org.monflabs.galtajs.node.ASTVarContainer;
import org.monflabs.galtajs.node.assignop.ASTAbstractAssign;
import org.monflabs.galtajs.node.assignop.ASTAssignAdd;
import org.monflabs.galtajs.node.assignop.ASTAssignSub;
import org.monflabs.galtajs.node.binaryop.ASTBinaryOp;
import org.monflabs.galtajs.node.binaryop.ASTGe;
import org.monflabs.galtajs.node.binaryop.ASTGt;
import org.monflabs.galtajs.node.binaryop.ASTLe;
import org.monflabs.galtajs.node.binaryop.ASTLt;
import org.monflabs.galtajs.node.call.ASTCall;
import org.monflabs.galtajs.node.debug.DebuggableNode;
import org.monflabs.galtajs.node.literal.ASTLiteral;
import org.monflabs.galtajs.node.unaryop.ASTAbstractIncDec;
import org.monflabs.galtajs.node.unaryop.ASTPostDec;
import org.monflabs.galtajs.node.unaryop.ASTPostInc;
import org.monflabs.galtajs.node.unaryop.ASTPreDec;
import org.monflabs.galtajs.node.unaryop.ASTPreInc;
import org.monflabs.galtajs.node.variable.ASTVariableDecl;
import org.monflabs.galtajs.parser.Token;
import org.monflabs.galtajs.rt.DisposeResourcesUtil;
import org.monflabs.galtajs.rt.JSGlobalContext;
import org.monflabs.galtajs.rt.JSResult;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.transpiler.HeadClosureSnapshotHolder;
import org.monflabs.util.StringFormat;
import org.monflabs.galtajs.rt.interpreter.InterpretedBlockRuntimeContext;
import org.monflabs.galtajs.rt.interpreter.InterpretedUnitRuntimeContext.Signal;
import org.monflabs.galtajs.rt.interpreter.JSInterpretedRuntimeContext;
import org.monflabs.galtajs.rt.interpreter.JSInterpretedRuntimeContext.VAR_TYPE;
import org.monflabs.galtajs.transpiler.JSTranspiler;
import org.monflabs.galtajs.transpiler.TranspilerJavaBuilder;
import org.monflabs.galtajs.transpiler.context.JSTranspilerGeneratorContext;
import org.monflabs.galtajs.transpiler.context.TranspilerGeneratorBlockContext;
import org.monflabs.galtajs.types.JSType;
import org.monflabs.galtajs.util.JavaBuilder;
import org.monflabs.util.StringUtil;




/**
 * for() statement node.
 */
public class ASTFor extends ASTVarContainer implements ILabeledNode, DebuggableNode {

	private String label;
	private ASTNode initNode;
	private ASTNode testNode;
	private ASTNode incNode;
	private ASTNode bodyNode;
	
	public ASTFor(Token t, ASTNode initNode, ASTNode testNode, ASTNode incNode, ASTNode bodyNode) {
		super(t);
		if(initNode!= null) {
			this.initNode = assignParent(initNode);
		}
		if(testNode!=null) {
			this.testNode = assignParent(testNode);
		}
		if(incNode!=null) {
			this.incNode = assignParent(incNode);
		}
		if(bodyNode!=null) {
			this.bodyNode = assignParent(wrapBareBodyIfNeeded(t, bodyNode));
		}
	}

	// See ASTForOf's identical helper for the full rationale: a
	// bare-statement body has no block container of its own for a closure
	// inside it to hoist to at parse time, so (unwrapped) it hoists to THIS
	// node's own container instead - outside the per-iteration redirect
	// window in transpileForNode() below. Wrapping it in a synthetic
	// single-statement ASTBlock here, at construction time (before init()
	// walks the parent chain), gives it its own block container instead,
	// routing it through the same already-correct `bodyNode instanceof
	// ASTBlock` mechanism a real `{ ... }` body already uses. Scoped to
	// bodies that actually contain a closure/eval, so the common
	// closure-free bare-body loop is unaffected.
	private static ASTNode wrapBareBodyIfNeeded(Token t, ASTNode bodyNode) {
		if(bodyNode!=null && !(bodyNode instanceof ASTBlock) && mayContainClosure(bodyNode)) {
			return new ASTBlock(t, java.util.Collections.singletonList(bodyNode));
		}
		return bodyNode;
	}

	// Same shape as mayCaptureAcrossIterations() below - duplicated (not
	// reused) since this one runs at CONSTRUCTION time (before this node's
	// own getVariables() exists).
	private static boolean mayContainClosure(ASTNode node) {
		if(node==null) {
			return false;
		}
		if(node instanceof ASTFunction) {
			return true;
		}
		if(node instanceof ASTCall call && !call.isNullOp()) {
			ASTNode fn = call.getNode();
			if(fn instanceof ASTIdentifier id && "eval".equals(id.getId())) {
				return true;
			}
		}
		int count = node.getChildCount();
		for(int i=0; i<count; i++) {
			if(mayContainClosure(node.getChild(i))) {
				return true;
			}
		}
		return false;
	}
	
	@Override
	public String getLabel() {
		return label;
	}
	
	@Override
	public void setLabel(String label) {
		this.label = label;
	}


	public ASTNode getInitNode() {
		return initNode;
	}

	public ASTNode getTestNode() {
		return testNode;
	}

	public ASTNode getIncNode() {
		return incNode;
	}

	public ASTNode getBodyNode() {
		return bodyNode;
	}

	@Override
	public int getChildCount() {
		return super.getChildCount()+4;
	}
	@Override
	public ASTNode getChild(int index) {
		switch(index) {
			case 0 ->		{ return initNode; }
			case 1 ->		{ return testNode; }
			case 2 ->		{ return incNode; }
			case 3 ->		{ return bodyNode; }
			default ->		{ return super.getChild(index-4); }
		}
	}
	@Override
	protected void _setChild(int index, ASTNode node) {
		switch(index) {
			case 0 ->	{ this.initNode = node; }
			case 1 ->	{ this.testNode  = node; }
			case 2 ->	{ this.incNode  = node; }
			case 3 ->	{ this.bodyNode  = node; }
			default ->  { super._setChild(index-4,node); }
		}
	}

	@Override
	public HighlightPosition getHighlightPosition() {
		return getHighlightPositionWithoutChildren();
	}

	// The for-loop's OWN LexicalDeclaration bound names ONLY - NOT every
	// LET/CONST/USING var incidentally registered into this same container
	// by something nested in the init/test/inc/body clauses with no closer
	// block container of its own. Concretely: a class expression used as a
	// default value (e.g. `for (const [cls = class {}] = []; ...)`)
	// registers its own internal self-reference binding
	// (ASTClassDecl.selfBindingVarDef, "9classSelf$N") into whatever
	// IContextBlockContainer is nearest - which is THIS ASTFor when nothing
	// closer exists - via the exact same generic addVarDeclaration() API
	// used for genuine loop variables. That binding is TRANSPILER ONLY
	// (see selfBindingVarDef's own doc comment): in interpreted mode it's
	// declared (TDZ) but never actually written via context.setVariable(),
	// so treating it as one of THIS loop's own per-iteration bindings threw
	// a spurious "Cannot access '9classSelf$N' before initialization" the
	// moment CreatePerIterationEnvironment tried to read its "current
	// value" (test262 accessor-name-inst-computed-in.js and the
	// for/dstr/*-init-fn-name-class.js family). initNode.getDeclaredVariables()
	// walks ONLY the declaration's own binding pattern, correctly excluding
	// any nested construct's incidental registrations - computed once and
	// cached, same as the AST facts above/below it.
	private java.util.List<VariableDef> loopDeclaredVariables;
	private java.util.List<VariableDef> getLoopDeclaredVariables() {
		if(loopDeclaredVariables==null) {
			if(!hasDeclaredVariables() || !(initNode instanceof ASTVariableDecl decl)) {
				loopDeclaredVariables = java.util.Collections.emptyList();
			} else {
				java.util.List<VariableDef> result = new java.util.ArrayList<>();
				for(String name: decl.getDeclaredVariables()) {
					VariableDef v = getVariables().get(name);
					if(v!=null) {
						result.add(v);
					}
				}
				loopDeclaredVariables = result;
			}
		}
		return loopDeclaredVariables;
	}

	// A C-style for-loop's lexical loop variables live in a dedicated per-loop
	// array touched by nothing but this loop's own clauses, and the init clause
	// - transpiled ONCE, before the native for(;;) - always assigns every one
	// of them (even a bare `for(let i;;)` emits `i = UNDEFINED`). So the `= TDZ`
	// seed the base container would emit is a dead store: no code can observe
	// the slot before the init assigns it. The ONE exception is the init clause
	// itself - a self/forward reference (`for(let i = i;...)`,
	// `for(let a = b, b = 1;...)`) must still throw, and a direct call to the JS
	// `eval` builtin in the init could read the name dynamically - keep the seed
	// then. Anything outside the init (test/increment/body) is already proven
	// safe by ASTIdentifier.isTdzSafeForLoopVar, which drops those reads' guards.
	@Override
	protected boolean needsTdzSeed(VariableDef v) {
		if(v.getVarType()==VAR_TYPE.USING) {
			// using/await-using disposal is a separate cold path; leave its
			// seed untouched rather than reason about it here.
			return true;
		}
		if(!(initNode instanceof ASTVariableDecl)) {
			return true;
		}
		return initClauseMayReadLoopVar(initNode, v.getName());
	}

	// Conservatively true if the init clause could observe the loop variable
	// `name` before it is assigned: a read identifier of that name anywhere in
	// the init, or a direct call to the JS `eval` builtin (which could read it
	// dynamically). Never descends into a nested function/class - those run at
	// call time, by which point the init has completed - matching ASTFunction's
	// own direct-eval-detection scoping.
	private static boolean initClauseMayReadLoopVar(ASTNode node, String name) {
		if(node==null) {
			return false;
		}
		if(node instanceof ASTFunction || node instanceof org.monflabs.galtajs.node.clazz.ASTBaseClass) {
			return false;
		}
		if(node instanceof ASTIdentifier id) {
			// A declaration-site identifier is the binding itself, not a read.
			if(!id.isDeclarationSite() && name.equals(id.getId())) {
				return true;
			}
		}
		if(node instanceof ASTCall call && call.getNode() instanceof ASTIdentifier callee
				&& "eval".equals(callee.getId()) && !call.isNullOp()) {
			return true;
		}
		int count = node.getChildCount();
		for(int i=0; i<count; i++) {
			if(initClauseMayReadLoopVar(node.getChild(i), name)) {
				return true;
			}
		}
		return false;
	}

	// True when this for-loop's OWN init clause declares its loop variable(s)
	// with `let`/`const` - per spec (CreatePerIterationEnvironment), such a
	// loop must give each iteration (and any closure created within it) a
	// FRESH copy of that binding, rather than the one shared/mutated binding
	// a `var`-declared (or externally-declared) loop variable correctly gets.
	private boolean hasPerIterationBindings() {
		for(VariableDef v: getLoopDeclaredVariables()) {
			VAR_TYPE t = v.getVarType();
			if(t==VAR_TYPE.LET || t==VAR_TYPE.CONST) {
				return true;
			}
		}
		return false;
	}

	// True when this for-loop's own init clause declares its loop variable(s)
	// with `using`/`await using` - such a resource is disposed once, at LOOP
	// EXIT (whether normal completion, break, return, or an exception),
	// NOT per-iteration - contrast with the let/const per-iteration binding
	// handling above, which this is deliberately independent of (a single
	// VariableDeclarationList is one VAR_TYPE, so the two never co-occur in
	// practice, but nothing here assumes that).
	private boolean hasUsingDeclarations() {
		for(VariableDef v: getLoopDeclaredVariables()) {
			if(v.getVarType()==VAR_TYPE.USING) {
				return true;
			}
		}
		return false;
	}

	// Cached result of needsPerIterationBinding() - the AST doesn't change
	// after parsing, so this only needs computing once, however many times
	// this loop node itself is evaluated (e.g. inside a function called
	// repeatedly).
	private Boolean needsPerIterationBinding;

	// A fresh-per-iteration binding is only OBSERVABLE if something could
	// capture the context across iterations: a nested closure (function/
	// arrow/method - anywhere in the subtree, however deeply nested) that
	// might reference the loop variable, or an eval() call (which can
	// dynamically create a closure via a string the static AST can't see
	// into, e.g. eval("fns.push(() => i)")). If init/test/increment/body
	// contain NEITHER, a fresh copy and the SAME context mutated in place
	// are indistinguishable to any observer - the whole point of the fresh
	// copy is so a captured reference sees a DIFFERENT value than a later
	// iteration's, and nothing here could hold such a reference - so it's
	// safe to skip the per-iteration allocation entirely and fall through
	// to the same zero-overhead path a `var` loop already uses.
	//
	// initNode is included even though it only ever EVALUATES once (unlike
	// test/inc/body, which run every iteration) - a closure created there
	// (e.g. `let i=0, f=function(){return i}`) still escapes across every
	// later iteration via whatever it's stored into (test262's
	// let-closure-inside-initialization.js: `f` must keep observing
	// iteration 0's `i`, not the shared mutated one, even though `f` itself
	// is never re-created after the first iteration).
	private boolean needsPerIterationBinding() {
		if(needsPerIterationBinding==null) {
			needsPerIterationBinding = hasPerIterationBindings()
					&& (mayCaptureAcrossIterations(initNode) || mayCaptureAcrossIterations(testNode) || mayCaptureAcrossIterations(incNode) || mayCaptureAcrossIterations(bodyNode));
		}
		return needsPerIterationBinding;
	}

	// See ASTVarContainer.needsHeadClosureSnapshot()'s own doc: a closure
	// hoisted directly to THIS node's own container (e.g. from the init/
	// test/increment clauses - a bare-statement/wrapped-block BODY closure
	// hoists to its own block's container instead, never here) is exactly
	// the case that needs a defensive snapshot, for the same reason (and
	// gated on the same condition) the body already needs a fresh
	// per-iteration array copy just above.
	@Override
	public boolean needsHeadClosureSnapshot() {
		return needsPerIterationBinding();
	}

	// Cached result of findDeferredIncClosure() - see that method's own doc.
	// Computed once (pure function of the AST, doesn't change across
	// however many times this loop node is itself evaluated/transpiled).
	private ASTFunction deferredIncClosure;
	private boolean deferredIncClosureComputed;

	// The "let-closure-inside-next-expression" test262 shape:
	// `for(let i=0; i<5; a.push(function(){return i;}), ++i) {}` - a
	// closure directly in the INCREMENT clause, immediately followed
	// (within that SAME clause/round) by a write to the loop's own
	// variable. Per spec, CreatePerIterationEnvironment runs once per
	// round, BEFORE the increment - so the closure and the write that
	// follows it share the SAME environment, and the closure must observe
	// the write. needsHeadClosureSnapshot()'s existing mechanism (see its
	// own doc) takes its defensive array copy eagerly, at the closure's
	// OWN construction site - correct for a closure with nothing writing
	// to the loop variable later in the same clause (e.g. the TEST-clause
	// closure in the sibling `let-closure-inside-condition.js`, already
	// fixed elsewhere), but one write too early for this shape.
	//
	// Deliberately scoped to ONLY incNode (never testNode - deferring
	// there would corrupt the test's own boolean result if the deferred
	// write happened to be the clause's own last term, and no test262
	// shape needs it) and to only DIRECT top-level comma siblings of
	// incNode (an exotic non-comma shape - e.g. the closure buried inside
	// a `&&`/`||` - falls back to the existing construction-time snapshot,
	// unchanged from before this method existed, same as it always was).
	// The "later term contains a write" check is deliberately
	// conservative/over-inclusive (it doesn't verify the write specifically
	// targets one of THIS loop's own variables) - deferring the snapshot
	// later than strictly necessary is always at least as correct as the
	// default eager timing, never less, so a false positive here only
	// costs a harmless extra indirection, never a wrong answer.
	private ASTFunction findDeferredIncClosure() {
		if(!(incNode instanceof ASTExpression expr)) {
			return null;
		}
		ASTNode[] terms = expr.getNodes();
		for(int i=0; i<terms.length-1; i++) {
			ASTFunction fn = findDirectClosure(terms[i]);
			if(fn!=null) {
				for(int j=i+1; j<terms.length; j++) {
					if(containsWrite(terms[j])) {
						return fn;
					}
				}
			}
		}
		return null;
	}

	private ASTFunction getDeferredIncClosure() {
		if(!deferredIncClosureComputed) {
			deferredIncClosureComputed = true;
			deferredIncClosure = needsPerIterationBinding() ? findDeferredIncClosure() : null;
		}
		return deferredIncClosure;
	}

	// Same non-descend-into-nested-function-body shape as
	// mayCaptureAcrossIterations() below, but returns the actual closure
	// node (there's at most one realistic candidate per clause) instead of
	// a boolean.
	private static ASTFunction findDirectClosure(ASTNode node) {
		if(node==null) {
			return null;
		}
		if(node instanceof ASTFunction fn) {
			return fn;
		}
		int count = node.getChildCount();
		for(int i=0; i<count; i++) {
			ASTFunction fn = findDirectClosure(node.getChild(i));
			if(fn!=null) {
				return fn;
			}
		}
		return null;
	}

	// True if `node` contains, anywhere in its own subtree (not descending
	// into a nested closure's own body - a write buried in there doesn't
	// run synchronously as part of THIS clause), an assignment or ++/--.
	private static boolean containsWrite(ASTNode node) {
		if(node==null) {
			return false;
		}
		if(node instanceof ASTAbstractAssign || node instanceof ASTAbstractIncDec) {
			return true;
		}
		if(node instanceof ASTFunction) {
			return false;
		}
		int count = node.getChildCount();
		for(int i=0; i<count; i++) {
			if(containsWrite(node.getChild(i))) {
				return true;
			}
		}
		return false;
	}

	private static boolean mayCaptureAcrossIterations(ASTNode node) {
		if(node==null) {
			return false;
		}
		if(node instanceof ASTFunction) {
			// Any closure-creating construct - function/arrow/generator/
			// async expressions and declarations, object-literal and class
			// methods/getters/setters all ultimately wrap (or are) an
			// ASTFunction node reachable via the generic child walk below.
			return true;
		}
		if(node instanceof ASTCall call && !call.isNullOp()) {
			ASTNode fn = call.getNode();
			if(fn instanceof ASTIdentifier id && "eval".equals(id.getId())) {
				return true;
			}
		}
		int count = node.getChildCount();
		for(int i=0; i<count; i++) {
			if(mayCaptureAcrossIterations(node.getChild(i))) {
				return true;
			}
		}
		return false;
	}

	// CreatePerIterationEnvironment: a fresh context, parented directly to
	// the loop's OWN outer scope (NOT chained through the previous
	// iteration's context, which would leak every past iteration's bindings
	// into an ever-growing scope chain), seeded with each let/const loop
	// variable's CURRENT value copied from `source`.
	private JSInterpretedRuntimeContext createPerIterationContext(JSInterpretedRuntimeContext outer, JSInterpretedRuntimeContext source) {
		InterpretedBlockRuntimeContext iterContext = new InterpretedBlockRuntimeContext(outer);
		for(VariableDef v: getLoopDeclaredVariables()) {
			VAR_TYPE t = v.getVarType();
			if(t==VAR_TYPE.LET || t==VAR_TYPE.CONST) {
				Object value = source.getVariableValue(v.getName(), RuntimeUtil.UNDEFINED);
				iterContext.createVariable(v.getName(), value, t);
			}
		}
		return iterContext;
	}

	// Runs test+body+increment for ONE iteration against `iterContext`
	// (which - for a let/const loop - is a fresh per-iteration context; see
	// evaluate() below). Return value drives the caller's while loop:
	//  - null           : test evaluated false, stop the loop normally.
	//  - Signal._BREAK  : this loop's own break (labeled or not) - stop
	//                     the loop, don't propagate further.
	//  - Signal.NONE    : iteration completed (normally, or via this loop's
	//                     own continue) - keep looping.
	//  - anything else  : RETURN, or a CONTINUE/BREAK for an OUTER labeled
	//                     loop - propagate immediately as ASTFor's own result.
	private Signal runIteration(JSInterpretedRuntimeContext context, JSInterpretedRuntimeContext iterContext, boolean perIteration, JSInterpretedRuntimeContext[] iterContextHolder, JSResult result, JSResult condResult) {
		if(testNode!=null) {
			boolean value = RuntimeUtil.toBoolean(context.getEnvironment(),testNode.evaluateValue(iterContext,condResult));
			if(!value) {
				return null;
			}
		}

		if(bodyNode!=null) {
			Signal s = bodyNode.evaluate(iterContext,result);
			if(s!=Signal.NONE) {
				switch(s.getType()) {
					case RETURN -> {
						return s;
					}
					case CONTINUE -> {
						String l = s.getLabel();
		            	if(!StringUtil.isEmpty(l) && !StringUtil.equals(label,l)) {
		            		// Continue up the next level
		            		return s;
		            	}
		                // This loop's own continue - fall through to the increment.
					}
					case BREAK -> {
						String l = s.getLabel();
		            	if(!StringUtil.isEmpty(l) && !StringUtil.equals(label,l)) {
		            		// Continue up the next level
		            		return s;
		            	}
		                // This loop's own break - result already carries the last
		                // completion value seen from the body (UpdateEmpty semantics).
		                return Signal._BREAK;
					}
				}
			}
		}

		JSInterpretedRuntimeContext nextContext = iterContext;
		if(perIteration) {
			nextContext = createPerIterationContext(context,iterContext);
			iterContextHolder[0] = nextContext;
		}
		if(incNode!=null) {
			incNode.evaluate(nextContext,condResult);
		}
		return Signal.NONE;
	}

	@Override
	public Signal evaluate(JSInterpretedRuntimeContext context, JSResult result) {
		try {
			JSGlobalContext gc = context.getGlobalContext();
			JSInterpretedRuntimeContext forContext = new InterpretedBlockRuntimeContext(context);
			boolean perIteration = needsPerIterationBinding();
			boolean hasUsingDeclarations = hasUsingDeclarations();
			// A let/const/using loop variable is in the Temporal Dead Zone
			// from forContext's creation until initNode runs, same as any
			// other block-entry - pre-populate it here so initNode's own
			// declaration (which now only INITIALIZES an existing binding,
			// see ASTVariableDeclScopedLet/ASTVariableDeclConst/
			// ASTVariableDeclUsing) has a TDZ placeholder to update rather
			// than silently no-op'ing.
			if(hasDeclaredVariables()) {
				for(VariableDef v: getLoopDeclaredVariables()) {
					VAR_TYPE t = v.getVarType();
					if(t==VAR_TYPE.LET || t==VAR_TYPE.CONST || t==VAR_TYPE.USING) {
						forContext.createVariable(v.getName(), RuntimeUtil.TDZ, t);
					}
				}
			}
			return forContext.with( () -> {
				if(!hasUsingDeclarations) {
					return runLoop(context, gc, forContext, perIteration, result);
				}
				// A using/await-using resource declared in the init clause
				// is disposed exactly once, at loop exit (normal completion,
				// break, return, or an exception) - NOT per-iteration, per
				// the spec (only let/const get CreatePerIterationEnvironment)
				// and confirmed against test262's
				// initializer-disposed-at-end-of-forstatement.js.
				Signal s = null;
				Throwable pending = null;
				try {
					s = runLoop(context, gc, forContext, perIteration, result);
				} catch(Throwable t) {
					pending = t;
				}
				Throwable toThrow = DisposeResourcesUtil.dispose(forContext, pending);
				if(toThrow!=null) {
					if(toThrow instanceof RuntimeException re) {
						throw re;
					}
					if(toThrow instanceof Error e) {
						throw e;
					}
					throw new RuntimeException(toThrow);
				}
				return s;
			});
		} catch(Throwable ex) {
			throw fillInStackTrace(ex);
		}
	}

	private Signal runLoop(JSInterpretedRuntimeContext context, JSGlobalContext gc, JSInterpretedRuntimeContext forContext, boolean perIteration, JSResult result) {
		if(initNode!=null) {
			initNode.evaluate(forContext,result);
		}

		// iterContextHolder[0] is null for a plain (non-let/const, e.g.
		// `var` or no declaration at all) loop - test/body/increment
		// then evaluate directly against forContext throughout,
		// unchanged from before, with no per-iteration re-scoping.
		//
		// For a let/const loop, it holds a brand new context - seeded
		// from the previous one's current values - swapped in before
		// the very first iteration and again after every completed
		// iteration (including one that hit `continue`), so each
		// iteration's closures capture their own distinct binding.
		// Each iteration is run inside THAT context's own with()
		// scope (not just passed as an explicit parameter) so that
		// JSContext.get()-based mechanisms (e.g. eval()'s direct-eval
		// detection, which correlates the ASTBaseCall-recorded caller
		// node against JSContext.get()'s current context) see a
		// context consistent with what's being explicitly threaded,
		// rather than the stale outer forContext.
		JSInterpretedRuntimeContext[] iterContextHolder = new JSInterpretedRuntimeContext[1];
		JSInterpretedRuntimeContext iterContext = forContext;
		if(perIteration) {
			iterContext = createPerIterationContext(context,forContext);
			iterContextHolder[0] = iterContext;
		}

		JSResult condResult = new JSResult();
		result.setUndefined();
		while(true) {
			JSInterpretedRuntimeContext currentIterContext = iterContext;
			Signal s = perIteration
				? currentIterContext.with(() -> runIteration(context,currentIterContext,perIteration,iterContextHolder,result,condResult))
				: runIteration(context,currentIterContext,perIteration,iterContextHolder,result,condResult);

			if(s==null) {
				break;
			}
			if(s==Signal._BREAK) {
				break;
			}
			if(s!=Signal.NONE) {
				return s;
			}
			if(perIteration) {
				iterContext = iterContextHolder[0];
			}

			gc.checkInterrupted();
		}

		// result contains the last body evaluation
		return Signal.NONE;
	}

    @Override
	public JSType getReturnedType() {
    	return JSType.UNKNOWN;
	}
    
    @Override
	public void transpileJavaStatement(JSTranspilerGeneratorContext _jsContext, TranspilerJavaBuilder b) {
		JSTranspilerGeneratorContext forContext = new TranspilerGeneratorBlockContext(_jsContext);

		b.println("{");
		b.incIndent();

		// Must run BEFORE transpilerDeclareStatement() below - it declares
		// this container's closure classes (transpilerDeclareFunctionClasses()),
		// which needs to already know whether findDeferredIncClosure()'s
		// closure (if any) is flagged deferred, since that decides whether
		// its snapshot field is emitted final or not. See
		// findDeferredIncClosure()'s own doc.
		ASTFunction deferredIncClosureForDeclare = getDeferredIncClosure();
		if(deferredIncClosureForDeclare!=null) {
			deferredIncClosureForDeclare.setDeferredHeadSnapshotTempVar(forContext.generateUniqueId("dhc_"));
		}

		transpilerDeclareStatement(forContext,b,0);

		// A using/await-using resource declared in the init clause is
		// disposed exactly once, at loop exit (normal completion, break,
		// return, or an exception) - NOT per-iteration (only let/const get
		// CreatePerIterationEnvironment) - mirroring evaluate() above. Wraps
		// the init clause itself too, not just the loop: an EARLIER
		// initializer's resource must still be disposed if a LATER one in the
		// same init clause throws (confirmed via test262's
		// initializer-disposed-if-subsequent-initializer-throws-in-
		// forstatement-head.js), so ASTBlock.transpileWithDisposal()'s
		// disposables list must already be set up (via forContext) BEFORE
		// initNode itself transpiles.
		if(hasUsingDeclarations()) {
			ASTBlock.transpileWithDisposal(_jsContext, forContext, b, () -> transpileForNode(forContext, b));
		} else {
			transpileForNode(forContext, b);
		}

		b.decIndent();
		b.println("}");
    }

	// --- Loop-counter math specialization (JSTranspilerOptions.isSpecializeLoopCounterMath(), off by default) ---
	//
	// Recognizes a classic `for(...; counter CMP bound; counter++/--/+=K/-=K)`
	// shape and shortcuts the loop's own test/update to native int math,
	// guarded by a per-operation RuntimeUtil.isInteger(env,v) check (Tier A)
	// that falls back to EXACTLY today's codegen otherwise - so correctness
	// never depends on anything this recognition step didn't verify:
	//  - the bound is re-evaluated fresh every iteration through its own
	//    normal codegen (cached once per iteration only to avoid evaluating
	//    it twice - see boundCacheVar below), so a bound that's mutated by a
	//    function call inside the body (e.g. `for(let i=0;i<a;i++){f()}`
	//    where `f` reassigns `a`) is still observed correctly - there is no
	//    stale snapshot to go wrong.
	//  - `Integer`-boxed is GaltaJS's own invariant for "safely within int
	//    range" (larger values promote to Long/Double/BigInteger, see
	//    RuntimeUtil.addExact) - so the check doubles as the overflow-safety
	//    proof, for free. isInteger() (not a plain `instanceof Integer`) also
	//    excludes a boxed `new Number(5)`/`Object(5)` - a genuine JS object
	//    that's ALSO `instanceof Integer` at the Java level - which must
	//    still go through the generic path (ToPrimitive, e.g. a
	//    user-overridden valueOf()), not native int math.
	// When NOTHING in the body ever references the counter (Tier B), the
	// counter itself can be a genuine native `int` local with no boxed
	// representation involved in the loop at all - see
	// tierBEligible/analyzeCounterMathSpecialization() below.
	private static final class CounterMathShape {
		final String cmpOperator;
		final String cmpFuncName;
		final ASTNode leftNode;
		final ASTNode rightNode;
		final boolean counterIsLeft;
		final ASTNode updateCounterNode;
		final int step;
		final String counterName;
		CounterMathShape(String cmpOperator, String cmpFuncName, ASTNode leftNode, ASTNode rightNode,
				boolean counterIsLeft, ASTNode updateCounterNode, int step, String counterName) {
			this.cmpOperator = cmpOperator;
			this.cmpFuncName = cmpFuncName;
			this.leftNode = leftNode;
			this.rightNode = rightNode;
			this.counterIsLeft = counterIsLeft;
			this.updateCounterNode = updateCounterNode;
			this.step = step;
			this.counterName = counterName;
		}
		ASTNode boundNode() {
			return counterIsLeft ? rightNode : leftNode;
		}
	}

	// Required precondition, not just an optimization detail: the update
	// codegen below (buildSpecializedUpdate) uses the counter's own
	// transpiled text as BOTH a read expression and an assignment target
	// (`{0} = ...`). That's only valid when the counter resolves to an
	// actual declared variable slot (e.g. `p_0[3]`, a plain array-index
	// expression) - an undeclared/global identifier instead transpiles to a
	// getIdentifierValue(...) method CALL (see ASTIdentifier's fallback for
	// a name that resolves via neither the context chain nor a with-scope),
	// which isn't an assignable Java expression at all. Mirrors the same
	// context-chain walk ASTIdentifier's own getIdentifierReadAccessor uses
	// to resolve a name, just to check existence rather than build an
	// accessor.
	private static boolean isDeclaredVariable(JSTranspilerGeneratorContext ctx, String name) {
		for(JSTranspilerGeneratorContext c=ctx; c!=null; c=c.getParent()) {
			if(c.getOwnVariable(name)!=null) {
				return true;
			}
		}
		return false;
	}

	private CounterMathShape recognizeCounterMathShape(JSTranspilerGeneratorContext forContext) {
		if(testNode==null || incNode==null) {
			return null;
		}
		String cmpOperator;
		String cmpFuncName;
		if(testNode instanceof ASTLt) {
			cmpOperator = "<"; cmpFuncName = "lt";
		} else if(testNode instanceof ASTLe) {
			cmpOperator = "<="; cmpFuncName = "le";
		} else if(testNode instanceof ASTGt) {
			cmpOperator = ">"; cmpFuncName = "gt";
		} else if(testNode instanceof ASTGe) {
			cmpOperator = ">="; cmpFuncName = "ge";
		} else {
			return null;
		}

		ASTNode left = ((ASTBinaryOp)testNode).getLeftNode();
		ASTNode right = ((ASTBinaryOp)testNode).getRightNode();

		String counterName;
		boolean counterIsLeft;
		if(left instanceof ASTIdentifier lid && !lid.isDeclarationSite()) {
			counterName = lid.getId();
			counterIsLeft = true;
		} else if(right instanceof ASTIdentifier rid && !rid.isDeclarationSite()) {
			counterName = rid.getId();
			counterIsLeft = false;
		} else {
			return null;
		}
		if(!isDeclaredVariable(forContext, counterName)) {
			return null;
		}

		ASTNode updateCounterNode;
		int step;
		if(incNode instanceof ASTPreInc || incNode instanceof ASTPostInc) {
			ASTNode target = ((ASTAbstractIncDec)incNode).getNode();
			if(!(target instanceof ASTIdentifier tid) || !counterName.equals(tid.getId())) {
				return null;
			}
			updateCounterNode = target;
			step = 1;
		} else if(incNode instanceof ASTPreDec || incNode instanceof ASTPostDec) {
			ASTNode target = ((ASTAbstractIncDec)incNode).getNode();
			if(!(target instanceof ASTIdentifier tid) || !counterName.equals(tid.getId())) {
				return null;
			}
			updateCounterNode = target;
			step = -1;
		} else if(incNode instanceof ASTAssignAdd assign) {
			if(!(assign.getLeftNode() instanceof ASTIdentifier tid) || !counterName.equals(tid.getId())) {
				return null;
			}
			Integer k = literalPositiveInt(assign.getRightNode());
			if(k==null) {
				return null;
			}
			updateCounterNode = assign.getLeftNode();
			step = k;
		} else if(incNode instanceof ASTAssignSub assign) {
			if(!(assign.getLeftNode() instanceof ASTIdentifier tid) || !counterName.equals(tid.getId())) {
				return null;
			}
			Integer k = literalPositiveInt(assign.getRightNode());
			if(k==null) {
				return null;
			}
			updateCounterNode = assign.getLeftNode();
			step = -k;
		} else {
			return null;
		}

		return new CounterMathShape(cmpOperator, cmpFuncName, left, right, counterIsLeft, updateCounterNode, step, counterName);
	}

	private static Integer literalPositiveInt(ASTNode node) {
		if(node instanceof ASTLiteral lit && lit.getValue() instanceof Integer v && v>0) {
			return v;
		}
		return null;
	}

	// Tier B precondition: nothing anywhere in the body references the
	// counter's name - read or write, at any nesting depth, including inside
	// a nested closure (a closure capturing the counter is itself a textual
	// reference, so this one scan also subsumes what would otherwise be a
	// separate per-iteration-capture check).
	private static boolean counterReferencedAnywhere(ASTNode node, String counterName) {
		if(node==null) {
			return false;
		}
		if(node instanceof ASTIdentifier id && counterName.equals(id.getId())) {
			return true;
		}
		int count = node.getChildCount();
		for(int i=0; i<count; i++) {
			if(counterReferencedAnywhere(node.getChild(i), counterName)) {
				return true;
			}
		}
		return false;
	}

	// Tier B also requires a compile-time-literal init value (unlike Tier A,
	// there's no boxed fallback to degrade to if init turns out not to be a
	// clean integer, so this is checked structurally rather than dynamically).
	private Integer literalIntInitValue(String counterName) {
		if(!(initNode instanceof ASTVariableDecl decl)) {
			return null;
		}
		for(ASTVariableDecl.Entry e: decl.getEntries()) {
			if(e.getVarDecl() instanceof ASTIdentifier id && counterName.equals(id.getId())) {
				return (e.getInitNode() instanceof ASTLiteral lit && lit.getValue() instanceof Integer v) ? v : null;
			}
		}
		return null;
	}

	private void transpileForNode(JSTranspilerGeneratorContext forContext, TranspilerJavaBuilder b) {
		// We do the init node outside of the loop
		// Else, it is complicated to translate to Java.
		if(initNode!=null) {
			initNode.transpileJavaStatement(forContext, b);
		}

		// See findDeferredIncClosure()'s own doc. The temp holder is
		// declared here, OUTSIDE the native for(;;) header (Java doesn't
		// allow a fresh local declaration inside the header's own
		// update-clause expression list) - reassigned fresh every round by
		// the closure's own construction expression below, never itself
		// captured by anything, so it doesn't need to be final. The name
		// itself was already generated and assigned to the closure node in
		// transpileJavaStatement() above, before transpilerDeclareStatement()
		// ran - read back here rather than generated fresh.
		ASTFunction deferredClosure = getDeferredIncClosure();
		String deferredTempVar = deferredClosure!=null ? deferredClosure.getDeferredHeadSnapshotTempVar() : null;
		if(deferredClosure!=null) {
			b.println("{0} {1};", HeadClosureSnapshotHolder.class.getName(), deferredTempVar);
		}

		// A block-scoped let/const declared directly in the loop BODY (not
		// the loop's own head, handled separately by needsPerIterationBinding()
		// above) would otherwise get a brand-new Object[] allocated by
		// ASTBlock's own transpilerDeclareStatement() call EVERY iteration,
		// even when nothing captures it across iterations - the same
		// "fresh binding only matters if something could observe it"
		// argument as needsPerIterationBinding() itself, just applied to the
		// body block's own declarations instead of the loop head's. Safe to
		// hoist the array (declare it once, here, before the loop) exactly
		// when the whole body contains no closure/eval anywhere - the same
		// conservative, whole-subtree check already used above, since a
		// closure capturing an UNRELATED variable elsewhere in the body
		// would make it unsafe to assume too (matches this method's own
		// existing precedent of erring toward "don't hoist" rather than
		// proving which specific variable a closure might reach).
		if(bodyNode instanceof ASTBlock hoistableBlock && hoistableBlock.hasDeclaredVariables() && !mayCaptureAcrossIterations(bodyNode)) {
			hoistableBlock.transpilerDeclareArrayOnly(forContext, b);
		}

		if(StringUtil.isNotEmpty(label)) {
			b.println("{0}:", label);
		}

		// See the "Loop-counter math specialization" block above this method
		// for the full design. Bails out entirely (counterShape stays null)
		// when there's a deferred-increment-closure interaction (rare - see
		// findDeferredIncClosure()'s own doc) rather than composing the two
		// mechanisms, since that shape never coexists with a plain numeric
		// counter loop in practice.
		CounterMathShape counterShape = (deferredClosure==null && forContext.getOptions().isSpecializeLoopCounterMath())
				? recognizeCounterMathShape(forContext)
				: null;
		String nativeCounterVar = null;
		String boundCacheVar = null;
		if(counterShape!=null) {
			boundCacheVar = forContext.generateUniqueId("cb_");
			b.println("Object {0};", boundCacheVar);
			Integer initLiteral = literalIntInitValue(counterShape.counterName);
			if(initLiteral!=null && !counterReferencedAnywhere(bodyNode, counterShape.counterName)) {
				// Tier B: the counter is never read anywhere in the body, so
				// there's nothing left that could re-box and regress - use a
				// genuine native int with no boxed representation involved.
				nativeCounterVar = forContext.generateUniqueId("ci_");
				b.println("int {0} = {1};", nativeCounterVar, initLiteral);
			}
		}

		b.print("for(");
		b.print("; ");
		if(counterShape!=null) {
			b.print(buildSpecializedTest(forContext, counterShape, boundCacheVar, nativeCounterVar));
		} else if(testNode!=null) {
			b.print(JSTranspiler.asBooleanCondition(forContext,testNode));
		}
		b.print("; ");
		if(counterShape!=null) {
			b.print(buildSpecializedUpdate(forContext, counterShape, nativeCounterVar));
		} else if(incNode!=null) {
			// A Java `for(...; ...; UPDATE)`'s update clause must itself be a
			// valid StatementExpression - only STATEMENT_TYPE.STATEMENT nodes
			// (assignments, ++/--, calls, ...) are guaranteed to already be
			// one; both EXPRESSION and LITERAL nodes need wrapping (a bare
			// literal like `false` - e.g. `for(false; ; false) {}`, a real if
			// unusual JS idiom - isn't a valid Java StatementExpression on
			// its own either, unlike a full statement position where it can
			// just be a silently-dropped no-op).
			if(incNode.getStatementType()!=STATEMENT_TYPE.STATEMENT) {
				b.print("statement(");
				b.print(JSTranspiler.asValue(forContext, incNode));
				b.print(")");
			} else {
				b.print(JSTranspiler.asValue(forContext, incNode));
			}
			if(deferredClosure!=null) {
				// A Java for-header's update clause is a comma-separated
				// list of independent StatementExpressions (assignment,
				// ++/--, method call, object creation, ...) - a plain
				// method-call entry appended here, separated by a real Java
				// comma (distinct from the JS-comma `comma(...)` runtime
				// helper the code above already may have used), runs AFTER
				// everything above it, once per round, entirely within the
				// native for header - no restructuring of the loop itself
				// needed. Reads getVariables().getJavaVariable() fresh
				// (rather than reusing a cached name) since - unlike
				// outerVar/iterVar just below, scoped to the BODY only -
				// nothing has redirected it away from the container's own
				// array at this point in the method.
				b.print(", {0}.setHeadClosureSnapshot(java.util.Arrays.copyOf({1},{2}))",
						deferredTempVar, getVariables().getJavaVariable(), getVariables().size());
			}
		}
		b.println(") {");
		b.incIndent();

		// A let/const-declared loop variable needs the SAME per-iteration
		// fresh-binding treatment as the interpreter (see evaluate() above)
		// - here, that means the BODY (and any closure transpiled from
		// within it) must reference a fresh COPY of the variable array made
		// at the top of each Java loop iteration, while the loop header
		// (test/increment, already transpiled to strings above) keeps
		// referencing the single, persistently-mutated outer array.
		// Achieved by temporarily redirecting this container's Java array
		// name - every ASTIdentifier resolving to one of these variables
		// looks it up via getVariables().getJavaVariable() at the moment
		// IT is transpiled, so redirecting it only around the body's own
		// transpileJavaStatement() call is enough to retarget everything
		// nested inside (including generated closures), without needing to
		// touch identifier resolution itself.
		boolean perIteration = needsPerIterationBinding();
		String outerVar = null;
		String iterVar = null;
		if(perIteration) {
			outerVar = getVariables().getJavaVariable();
			iterVar = forContext.generateUniqueId("p_");
			b.println("final Object[] {0} = java.util.Arrays.copyOf({1}, {2});", iterVar, outerVar, getVariables().size());
			getVariables().setJavaVariable(iterVar);
		}

		if(bodyNode instanceof ASTBlock block) {
			block.transpileJavaStatementNoBrace(forContext,b);
		} else {
			b.debugLocation(bodyNode);
			bodyNode.transpileJavaStatement(forContext, b);
		}

		if(perIteration) {
			getVariables().setJavaVariable(outerVar);
			// Copy back whatever the body left the per-iteration array as
			// (including any mid-body mutation of the loop variable itself)
			// so the NEXT iteration's fresh copy - and the increment clause,
			// which runs right after this, still referencing the outer
			// array - both see it.
			// Skipped when the body's own last statement always exits the
			// loop (return/throw/unlabeled break) - this trailer would be
			// genuinely unreachable Java code then (javac rejects it, a real
			// compile error, not just a semantic mismatch). Confirmed via
			// test262 language/module-code/top-level-await/syntax/
			// for-await-expr-{func-expression,new-expr,obj-literal}.js
			// (unlabeled break) and language/statements/for/dstr/
			// {const,let}-ary-ptrn-elem-id-init-throws.js (return).
			if(!endsWithUnconditionalLoopExit(bodyNode)) {
				b.println("System.arraycopy({0}, 0, {1}, 0, {2});", iterVar, outerVar, getVariables().size());
			}
		}

		b.decIndent();
		b.println("}");
    }

	// Builds the test clause's Java text when counterShape is non-null - see
	// the "Loop-counter math specialization" block above transpileForNode
	// for the full design. `boundCacheVar` caches the bound's value so it's
	// evaluated exactly once per iteration (matching original semantics -
	// the bound may have side effects, e.g. a function call that reassigns
	// it) rather than once per branch of the generated ternary.
	private String buildSpecializedTest(JSTranspilerGeneratorContext forContext, CounterMathShape shape, String boundCacheVar, String nativeCounterVar) {
		String boundText = JSTranspiler.asValue(forContext, shape.boundNode());
		String fallback = fallbackComparison(forContext, shape, boundCacheVar, nativeCounterVar);
		String boundInt = "((Integer)"+boundCacheVar+").intValue()";
		// RuntimeUtil.isInteger(env,v) - not a plain `instanceof Integer` -
		// since a boxed `new Number(5)`/`Object(5)` is ALSO a genuine
		// java.lang.Integer instance (see its own doc) yet is a JS OBJECT:
		// comparisons/arithmetic on it must go through ToPrimitive (e.g. a
		// user-overridden valueOf()), not shortcut straight to the boxed int.
		// The assignment-as-argument (`boundCacheVar=boundText`) both caches
		// the bound (evaluated exactly once, matching original semantics -
		// it may have side effects, e.g. a function call that reassigns it)
		// and feeds isInteger() in one expression.
		if(nativeCounterVar!=null) {
			// Tier B: the counter is already a native int - only the bound
			// (re-evaluated fresh here, cached once) needs checking.
			String cmp = shape.counterIsLeft
					? nativeCounterVar+" "+shape.cmpOperator+" "+boundInt
					: boundInt+" "+shape.cmpOperator+" "+nativeCounterVar;
			return StringFormat.format("(isInteger(getEnvironment(), {0}={1})) ? ({2}) : ({3})",
					boundCacheVar, boundText, cmp, fallback);
		}
		// Tier A: both the counter's current (still boxed) value and the
		// bound need checking - `&` (not `&&`) so the bound is ALWAYS
		// evaluated exactly once regardless of the counter's own check,
		// never zero times (which would silently skip its side effects) or
		// twice (which would run them again).
		String counterText = JSTranspiler.asValue(forContext, shape.counterIsLeft ? shape.leftNode : shape.rightNode);
		String counterInt = "((Integer)("+counterText+")).intValue()";
		String cmp = shape.counterIsLeft
				? counterInt+" "+shape.cmpOperator+" "+boundInt
				: boundInt+" "+shape.cmpOperator+" "+counterInt;
		return StringFormat.format("((isInteger(getEnvironment(), {0})) & (isInteger(getEnvironment(), {1}={2}))) ? ({3}) : ({4})",
				counterText, boundCacheVar, boundText, cmp, fallback);
	}

	// The exact-fallback-to-today's-codegen arm: `lt`/`le`/`gt`/`ge` on the
	// original operands (the counter's own current value, boxed - via
	// Integer.valueOf() for Tier B, since its native local never leaves
	// `int` - and the already-cached bound), in their original left/right
	// order, so when this doesn't apply behavior is identical to before this
	// feature existed.
	private String fallbackComparison(JSTranspilerGeneratorContext forContext, CounterMathShape shape, String boundCacheVar, String nativeCounterVar) {
		String counterOperandText = nativeCounterVar!=null
				? "Integer.valueOf("+nativeCounterVar+")"
				: JSTranspiler.asValue(forContext, shape.counterIsLeft ? shape.leftNode : shape.rightNode);
		String leftText = shape.counterIsLeft ? counterOperandText : boundCacheVar;
		String rightText = shape.counterIsLeft ? boundCacheVar : counterOperandText;
		return StringFormat.format("{0}({1},{2})", shape.cmpFuncName, leftText, rightText);
	}

	// Builds the update clause's Java text when counterShape is non-null.
	private String buildSpecializedUpdate(JSTranspilerGeneratorContext forContext, CounterMathShape shape, String nativeCounterVar) {
		if(nativeCounterVar!=null) {
			// Tier B: fail loudly (Math.addExact throws ArithmeticException)
			// rather than silently wrap on the astronomically rare (~2
			// billion iteration) overflow case - there's no boxed
			// representation to gracefully degrade to here, since the
			// counter never leaves native `int` for this tier.
			return StringFormat.format("{0} = Math.addExact({0}, {1})", nativeCounterVar, shape.step);
		}
		// Tier A: RuntimeUtil.addExact (statically imported in generated
		// code) already implements the exact same overflow-to-
		// long/BigInteger/double promotion the generic incNumber() path
		// would, so reusing it directly preserves overflow correctness
		// exactly rather than narrowing it. Uses RuntimeUtil.isInteger(), not
		// a plain `instanceof Integer` - see buildSpecializedTest's own doc
		// comment - a boxed `new Number(5)` counter must still go through
		// incNumber(), not addExact().
		String counterText = JSTranspiler.asValue(forContext, shape.updateCounterNode);
		return StringFormat.format("{0} = (isInteger(getEnvironment(), {0})) ? addExact(getEnvironment(), ((Integer)({0})).intValue(), {1}) : incNumber({0})",
				counterText, shape.step);
	}

	// Conservative "does this node's last reachable top-level statement
	// always exit THIS loop" check - mirrors ASTFunction's own
	// endsWithUnconditionalExit() (which only cares about `return`), but
	// this one also needs `throw` and an UNLABELED `break` (which, at this
	// node's own nesting depth, always targets this immediately-enclosing
	// for loop - a LABELED break/continue is deliberately NOT recognized
	// here: proving it targets an OUTER construct rather than this loop
	// would need real label-scope resolution, and a false positive here
	// would wrongly skip a still-needed arraycopy, not just miss an
	// optimization). Does NOT descend into nested loops/conditionals/
	// switches - only this node's own directly-visible last statement,
	// same conservative scope as ASTFunction's version.
	private static boolean endsWithUnconditionalLoopExit(ASTNode node) {
		node = ASTNode.skipTransparent(node);
		if(node instanceof ASTReturn || node instanceof ASTThrow) {
			return true;
		}
		if(node instanceof ASTBreak b && StringUtil.isEmpty(b.getLabel())) {
			return true;
		}
		if(node instanceof ASTBlock block) {
			ASTNode[] statements = block.getStatements();
			if(statements==null || statements.length==0) {
				return false;
			}
			return endsWithUnconditionalLoopExit(statements[statements.length-1]);
		}
		return false;
	}

    @Override
	public void decompileStatement(JavaBuilder b) {
    	if(StringUtil.isNotEmpty(label)) {
        	b.append("{0}: ", label).nl();
    	}
    	b.append("for(");
    	if(initNode!=null) {
        	b.append(initNode.decompileExpression());
    	}
    	b.append("; ");
    	if(testNode!=null) {
        	b.append(testNode.decompileExpression());
    	}
    	b.append("; ");
    	if(incNode!=null) {
        	b.append(incNode.decompileExpression());
    	}
    	b.append(") {\n");
    	b.incIndent();
    	decompileBlockStatements(b,bodyNode);
    	b.decIndent();
    	b.append("}");
	}
}