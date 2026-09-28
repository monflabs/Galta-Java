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

import java.util.Iterator;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.JSParseException;
import org.monflabs.galtajs.node.ASTIdentifier;
import org.monflabs.galtajs.node.ASTNode;
import org.monflabs.galtajs.node.ASTProgram;
import org.monflabs.galtajs.node.ASTVarContainer;
import org.monflabs.galtajs.node.call.ASTCall;
import org.monflabs.galtajs.node.literal.ASTContainerLiteral;
import org.monflabs.galtajs.parser.Token;
import org.monflabs.galtajs.rt.DisposeResourcesUtil;
import org.monflabs.galtajs.rt.JSGlobalContext;
import org.monflabs.galtajs.rt.JSResult;
import org.monflabs.galtajs.rt.RuntimeUtil;
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
import org.monflabs.util.StringFormat;
import org.monflabs.util.StringUtil;




/**
 * for of() abstract statement node.
 */
public class ASTForOf extends ASTFor_ {

	private ASTNode varDecl;
	private ASTNode collectionNode;
	private ASTNode bodyNode;

	// True for `for await (... of ...)` (13.7.5's [+Await] alternative) -
	// false for plain `for (... of ...)`. Reuses this same class (rather than
	// a separate ASTForAwaitOf) since the two forms share their entire head
	// grammar shape (ForDeclaration/LeftHandSideExpression + "of" +
	// AssignmentExpression) and almost all runtime behavior (per-iteration
	// bindings, using-disposal, IteratorClose, labeled break/continue) - the
	// only difference is HOW the iterator is obtained/stepped (see
	// RuntimeUtil.valueIteratorAsync()) and gating on an enclosing async
	// context (see init() below).
	private boolean isAwait;

	// Set statically in init(), from the IMMEDIATELY-enclosing
	// ASTFunctionDecl only - same rationale as ASTAwait's own identically-
	// named/shaped field: only true when isAwait AND that function is itself
	// an async GENERATOR (`async function*`), in which case every implicit
	// await this loop performs (stepping the iterator) must go through the
	// yielder-based RuntimeUtil.awaitInGenerator_() instead of the plain
	// blocking RuntimeUtil.await_() - a raw blocking await here would
	// deadlock exactly like a hand-written `await` expression would (see
	// ASTAwait's own doc for the full rationale).
	private boolean insideAsyncGeneratorBody;

	public ASTForOf(Token t, ASTNode varDecl, ASTNode collectionNode, ASTNode bodyNode) {
		this(t, varDecl, collectionNode, bodyNode, false);
	}

	public ASTForOf(Token t, ASTNode varDecl, ASTNode collectionNode, ASTNode bodyNode, boolean isAwait) {
		super(t);
		this.varDecl = assignParent(varDecl);
		this.collectionNode = assignParent(collectionNode);
		this.bodyNode = assignParent(wrapBareBodyIfNeeded(t, bodyNode));
		this.isAwait = isAwait;
	}

	public boolean isAwait() {
		return isAwait;
	}

	// A bare-statement body (no `{}` of its own) has no block container for
	// a closure inside it to hoist to at parse time (see
	// needsPerIterationBinding()'s own doc, and the long comment at the top
	// of transpileJavaStatement() describing the two reverted attempts at
	// this) - it hoists to THIS node's own container instead, which sits
	// outside the per-iteration redirect window below and so never observes
	// it. Wrapping such a body in a synthetic single-statement ASTBlock -
	// done HERE, at construction time, before init() walks the parent chain
	// to decide where a closure registers - gives it its own block
	// container to hoist into instead, routing it through the SAME
	// already-correct, already-tested per-iteration mechanism a real
	// `{ ... }` body already uses (the `bodyNode instanceof ASTBlock` branch
	// below), rather than inventing a new one. Scoped to only bodies that
	// actually contain a closure/eval (mayContainClosure()) so the
	// overwhelmingly common bare-body loop with nothing to capture keeps
	// its original, simpler codegen shape unchanged.
	private static ASTNode wrapBareBodyIfNeeded(Token t, ASTNode bodyNode) {
		if(bodyNode!=null && !(bodyNode instanceof ASTBlock) && mayContainClosure(bodyNode)) {
			return new ASTBlock(t, java.util.Collections.singletonList(bodyNode));
		}
		return bodyNode;
	}

	// Same shape as mayCaptureAcrossIterations() below - duplicated (not
	// reused) since this one runs at CONSTRUCTION time (before this node's
	// own getVariables() exists), while mayCaptureAcrossIterations() is a
	// distinct, later check (needsPerIterationBinding(), gated additionally
	// on the head binding actually being let/const/using).
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

	public ASTNode getVarDecl() {
		return varDecl;
	}

	public ASTNode getCollectionNode() {
		return collectionNode;
	}

	public ASTNode getBodyNode() {
		return bodyNode;
	}

	@Override
	public int getChildCount() {
		return super.getChildCount()+3;
	}
	@Override
	public ASTNode getChild(int index) {
		switch(index) {
			case 0 ->		{ return varDecl; }
			case 1 ->		{ return collectionNode; }
			case 2 ->		{ return bodyNode; }
			default ->		{ return super.getChild(index-3); }
		}
	}
	@Override
	protected void _setChild(int index, ASTNode node) {
		switch(index) {
			case 0 ->	{ this.varDecl = node; }
			case 1 ->	{ this.collectionNode  = node; }
			case 2 ->	{ this.bodyNode  = node; }
			default ->  { super._setChild(index-3,node); }
		}
	}
	
	@Override
	protected void init(InitContext initContext) {
		super.init(initContext);

		if(isAwait) {
			// Mirrors ASTAwait.init() exactly: a top-level `for await` marks
			// the enclosing PROGRAM as needing async execution (GaltaJS
			// allows top-level await everywhere by design - see
			// awaitReserved's own doc in JSParser.jj); nested inside a
			// function, the parser's awaitReserved gating already
			// guarantees that function is async (a non-async function body
			// sets awaitReserved=false, which excludes the "for" "await"
			// grammar alternative entirely - see IterationStatement()), so
			// there's nothing to enforce here beyond noting whether it's
			// also a GENERATOR (for the await-mechanism choice below).
			IContextRootContainer n = findParentNodeByClass(IContextRootContainer.class);
			if(n instanceof ASTProgram p) {
				p.setAsyncExecution(true);
			} else if(n instanceof ASTFunctionDecl f) {
				insideAsyncGeneratorBody = f.isGenerator();
			}
		}

		// For now - we should find a better way to validate the assigned nodes???
		if(skipTransparent(varDecl) instanceof ASTIdentifier id) {
			VariableDef v = findParentNodeByClass(ASTVarContainer.class).findVariable(id.getId());
			if(v==null) {
				if(initContext.getEnvironment().mustDeclareAllVariables()) {
					throw new JSParseException(null, this, "Variable {0} does not exist", id.getId());
				}
				//public boolean addVarDeclaration(String varName, VAR_TYPE varType, JSType jsType, ASTFunctionDecl function, Function<JSRuntimeContext,Object> initializer);
				//v = findParentNodeByClass(IContextRootContainer.class).addVarDeclaration(id.getId(),VAR_TYPE.AUTO,null);
			}
		}
	}

	// True when the loop head declares its variable with `using`/`await
	// using` - such a resource is disposed at the END OF EACH ITERATION
	// (ForBodyEvaluation's per-iteration DisposeResources), unlike ASTFor's
	// own init-clause using (a single declaration evaluated once, disposed
	// once at loop exit - see its hasUsingDeclarations() comment).
	private boolean hasUsingDeclarations() {
		if(!hasDeclaredVariables()) {
			return false;
		}
		for(VariableDef v: getVariables()) {
			if(v.getVarType()==VAR_TYPE.USING) {
				return true;
			}
		}
		return false;
	}

	// CreatePerIterationEnvironment: true when this loop's OWN head binding
	// is `let`/`const`/`using`/`await using` - each iteration (and any
	// closure created within it) must observe a FRESH copy, not the one
	// shared/mutated binding a `var`-declared loop variable correctly gets.
	// USING is included here (unlike ASTFor's own C-style-for check, which
	// deliberately excludes it - see that class's hasUsingDeclarations()
	// doc, a for-of loop's own using binding IS per-iteration, disposed at
	// the end of EACH iteration, unlike ASTFor's single loop-exit-only
	// disposal) - test262 head-using-fresh-binding-per-iteration.js.
	private boolean hasPerIterationBindings() {
		if(!hasDeclaredVariables()) {
			return false;
		}
		for(VariableDef v: getVariables()) {
			VAR_TYPE t = v.getVarType();
			if(t==VAR_TYPE.LET || t==VAR_TYPE.CONST || t==VAR_TYPE.USING) {
				return true;
			}
		}
		return false;
	}

	private Boolean needsPerIterationBinding;

	// A fresh-per-iteration binding is only OBSERVABLE if something could
	// capture it across iterations - see ASTFor's identical check (and its
	// own doc) for the full rationale; duplicated here rather than shared,
	// to keep this fix self-contained. varDecl is included (even though a
	// destructuring default there could reference an EARLIER-in-the-same-
	// pattern binding, not a captured-across-iterations one) purely for
	// symmetry with ASTFor's own initNode inclusion - a closure inside a
	// default-value expression (`for (let [a = () => a] of pairs)`) is the
	// scenario that matters.
	private boolean needsPerIterationBinding() {
		if(needsPerIterationBinding==null) {
			needsPerIterationBinding = hasPerIterationBindings()
					&& (mayCaptureAcrossIterations(varDecl) || mayCaptureAcrossIterations(collectionNode) || mayCaptureAcrossIterations(bodyNode));
		}
		return needsPerIterationBinding;
	}

	// See ASTVarContainer.needsHeadClosureSnapshot()'s own doc: a closure
	// hoisted directly to THIS node's own container (from the collection
	// expression, or a ForDeclaration's own default-value initializer - a
	// bare-statement/wrapped-block BODY closure hoists to its own block's
	// container instead, never here) is exactly the case that needs a
	// defensive snapshot, for the same reason (and gated on the same
	// condition) the body already needs a fresh per-iteration array copy.
	@Override
	public boolean needsHeadClosureSnapshot() {
		return needsPerIterationBinding();
	}

	private static boolean mayCaptureAcrossIterations(ASTNode node) {
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
			if(mayCaptureAcrossIterations(node.getChild(i))) {
				return true;
			}
		}
		return false;
	}

	// Conservative "does this node's last reachable top-level statement
	// always exit THIS loop" check - duplicated from ASTFor's identical
	// helper (see its own doc for the full rationale) rather than shared,
	// to keep this fix self-contained.
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

	@SuppressWarnings({ "incomplete-switch" })
	@Override
	public Signal evaluate(JSInterpretedRuntimeContext context, JSResult result) {
		try {
			Iterator<?> it = null;
			// Per spec 13.7.5.12, the collection expression evaluates in a
			// lexical environment that already has this loop's own let/const/
			// using bound name(s) - in TDZ - so a self-referencing collection
			// (`let x=1; for (let x of [x]) {}`, or the using equivalent)
			// must throw, not resolve to an outer same-named binding.
			JSInterpretedRuntimeContext headContext = context;
			if(hasDeclaredVariables()) {
				for(VariableDef v: getVariables()) {
					VAR_TYPE t = v.getVarType();
					if(t==VAR_TYPE.LET || t==VAR_TYPE.CONST || t==VAR_TYPE.USING) {
						if(headContext==context) {
							headContext = new InterpretedBlockRuntimeContext(context);
						}
						headContext.createVariable(v.getName(), RuntimeUtil.TDZ, t);
					}
				}
			}
			Object col = collectionNode.evaluateValue(headContext,result);
			if(col!=null) {
				// This is only in (for...of)
				it = isAwait
						? RuntimeUtil.valueIteratorAsync(context,col,insideAsyncGeneratorBody)
						: RuntimeUtil.valueIterator(context.getEnvironment(),col);
			} else {
				throw RuntimeUtil.typeError("Null collection");
			}
			
			JSGlobalContext gc = context.getGlobalContext();
			result.setUndefined();
			JSResult tempResult = new JSResult();
			// A fresh context (plus a fresh TDZ-seeded slot) is only
			// observable per-iteration if something could actually capture
			// it across iterations - see needsPerIterationBinding()'s own
			// doc for the full rationale, already relied on by
			// transpileJavaStatement()'s identical "perIteration" codegen
			// below. `using`/`await using` is excluded from this shortcut
			// even though hasPerIterationBindings() alone would allow it:
			// its disposal timing (once per iteration, unconditionally -
			// see hasUsingDeclarations() below) was not re-verified safe
			// against reusing the very same context object run over run, so
			// it keeps the original, already-correct fresh-per-iteration
			// path unconditionally instead.
			boolean reuseContext = !needsPerIterationBinding() && !hasUsingDeclarations();
			JSInterpretedRuntimeContext sharedContext = null;
			if(reuseContext) {
				sharedContext = new InterpretedBlockRuntimeContext(context);
				if(hasDeclaredVariables()) {
					for(VariableDef v: getVariables()) {
						VAR_TYPE t = v.getVarType();
						if(t==VAR_TYPE.LET || t==VAR_TYPE.CONST || t==VAR_TYPE.USING) {
							sharedContext.createVariable(v.getName(), RuntimeUtil.TDZ, t);
						}
					}
				}
			}
loop:		while(it.hasNext()) {
				Object value = it.next();

				JSInterpretedRuntimeContext forContext;
				if(reuseContext) {
					// Re-assigning into the SAME already-initialized slot
					// every iteration, rather than a fresh TDZ one, is safe
					// specifically BECAUSE nothing can observe the
					// difference (that's needsPerIterationBinding()'s own
					// condition) - including a const loop variable, whose
					// normal reassignment-throws behavior only applies to a
					// plain assignment expression, never to a declaration's
					// own (re-)initialization, which is what this is on
					// every iteration.
					forContext = sharedContext;
				} else {
					forContext = new InterpretedBlockRuntimeContext(context);
					// A let/const loop variable is in TDZ from this fresh
					// per-iteration context's creation until the assignment
					// below runs - pre-populate it, same as ASTFor/ASTBlock,
					// so that assignment (which now only INITIALIZES an
					// existing binding) has something to update.
					if(hasDeclaredVariables()) {
						for(VariableDef v: getVariables()) {
							VAR_TYPE t = v.getVarType();
							if(t==VAR_TYPE.LET || t==VAR_TYPE.CONST || t==VAR_TYPE.USING) {
								forContext.createVariable(v.getName(), RuntimeUtil.TDZ, t);
							}
						}
					}
				}
				Signal s;
				try {
					s = forContext.with( () -> {
						// A bare destructuring pattern (no var/let/const - e.g. `for ([a,b] of it)`)
						// parses as a raw ASTContainerLiteral, which - unlike ASTVariableDecl -
						// doesn't implement the generic evaluateAssign() dispatch; assign through
						// it directly, same as ASTAssign does for `[a,b] = value`.
						if(varDecl instanceof ASTContainerLiteral lit) {
							lit.assign(forContext, (k,v) -> RuntimeUtil.assignIdentifierOrCreateGlobal(forContext, k, v), value, tempResult, true);
						} else {
							varDecl.evaluateAssign(forContext, value, null, tempResult, null);
						}
						if(bodyNode!=null) {
							return bodyNode.evaluate(forContext,result);
						}
						return Signal.NONE;
					});
				} catch(Throwable ex) {
					// IteratorClose (7.4.8): the loop is being abandoned because of an
					// exception (binding the loop variable or the body) - best-effort
					// close, swallowing any secondary error so the original exception
					// is what actually propagates.
					closeIteratorQuietly(context.getEnvironment(), it);
					if(hasUsingDeclarations()) {
						// A using/await-using loop variable is disposed at the END
						// OF EACH ITERATION (unlike ASTFor's init-clause using,
						// disposed once at loop exit) - forContext is itself
						// per-iteration here, so this is exactly analogous to
						// ASTBlock's own per-invocation disposal.
						Throwable toThrow = DisposeResourcesUtil.dispose(forContext, ex);
						if(toThrow instanceof RuntimeException re) {
							throw re;
						}
						if(toThrow instanceof Error e) {
							throw e;
						}
						throw new RuntimeException(toThrow);
					}
					throw ex;
				}
				if(hasUsingDeclarations()) {
					Throwable toThrow = DisposeResourcesUtil.dispose(forContext, null);
					if(toThrow!=null) {
						closeIteratorQuietly(context.getEnvironment(), it);
						if(toThrow instanceof RuntimeException re) {
							throw re;
						}
						if(toThrow instanceof Error e) {
							throw e;
						}
						throw new RuntimeException(toThrow);
					}
				}
				if(s!=Signal.NONE) {
					switch(s.getType()) {
						case RETURN -> {
							closeIterator(context.getEnvironment(), it);
							return s;
						}
						case CONTINUE -> {
							String l = s.getLabel();
			            	if(!StringUtil.isEmpty(l)) {
			            		if(!StringUtil.equals(getLabel(),l)) {
			            			// Continue up the next level - also abandons this loop
			            			closeIterator(context.getEnvironment(), it);
			            			return s;
			            		}
			            	}
			                // Continue the main loop
			                // continue...
						}
						case BREAK -> {
							String l = s.getLabel();
			            	if(!StringUtil.isEmpty(l)) {
			            		if(!StringUtil.equals(getLabel(),l)) {
			            			// Continue up the next level
			            			closeIterator(context.getEnvironment(), it);
			            			return s;
			            		}
			            	}
			                // Break the main loop - result already carries the last
			                // completion value seen from the body (UpdateEmpty semantics).
			                closeIterator(context.getEnvironment(), it);
			                break loop;
						}
					}
				}

				gc.checkInterrupted();
			}

			// result contains the last body evaluation
			return Signal.NONE;
		} catch(Throwable ex) {
			throw fillInStackTrace(ex);
		}
	}

	// IteratorClose (7.4.8): best-effort call to the for-of iterator's
	// return() method when abandoning it before exhausting it (break/
	// continue-to-an-outer-label/return/an exception).
	private static void closeIterator(JSEnvironment env, Iterator<?> it) {
		RuntimeUtil.iteratorClose(env, it);
	}
	private static void closeIteratorQuietly(JSEnvironment env, Iterator<?> it) {
		try {
			closeIterator(env, it);
		} catch(Throwable ignore) {
			// The original exception (from the body/binding) is what must propagate.
		}
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

		// NOTE: a closure hoisted to THIS node's own container still can't
		// have its declare-pass moved inside the per-iteration redirect
		// window below: its instantiation site (`new F_xxx(...)`) isn't
		// necessarily textually nested inside that same generated Java
		// block (e.g. a closure reachable from the collection expression,
		// transpiled further above, or from elsewhere structurally
		// disconnected from the per-iteration try block) - moving the
		// CLASS DECLARATION in there broke Java scoping for those call
		// sites ("cannot find symbol: class F_xxx", a genuine compile
		// error) for 5 files, while only fixing 1 (reverted attempt, see
		// KnownGaps.md). A closure that's part of a BARE-STATEMENT body no
		// longer reaches this container's own declare-pass at all in the
		// common case, though - see wrapBareBodyIfNeeded(), called from the
		// constructor: such a body is now wrapped in its own synthetic
		// ASTBlock when it contains a closure, so it hoists into (and
		// declares via) that block's own container instead, taking the
		// `bodyNode instanceof ASTBlock` branch below - the SAME already-
		// correct mechanism a genuine `{ ... }` body uses.
		transpilerDeclareStatement(forContext,b,0);

		String itName = "_it"+_jsContext.getDepth();
		// IteratorClose (7.4.8): the interpreter's evaluate() above closes the
		// iterator whenever the loop is abandoned before exhausting it - on an
		// exception from binding/body (quiet close so the original exception
		// still wins) or a normal abrupt completion (break / a labeled
		// continue targeting an outer loop / return - non-quiet, so a close
		// failure correctly overrides it, per spec) - but NOT when the abrupt
		// completion is the iterator's OWN hasNext()/next() call throwing
		// (test262 iterator-next-error.js/iterator-next-result-value-attr-
		// error.js: no close attempt at all in that case, since the iterator
		// that just threw isn't merely being abandoned early). Uses plain
		// valueIterator() (not the stricter, spec-only-iterables
		// IteratorRecord/getIterator ASTArrayLiteral's destructuring codegen
		// uses) since a for-of target can also be a raw Java Iterator/
		// Iterable via GaltaJS's Java interop, which only valueIterator()
		// handles.
		//   doneVar: set ONLY when hasNext() reports naturally exhausted (no
		//     exception) - skips the close entirely (nothing to abandon).
		//   closedVar: set once a close has already happened (body/bind
		//     exception) OR is definitely not wanted (step exception) - either
		//     way the outer finally must not attempt a second one.
		String doneVar = forContext.generateUniqueId("itDone");
		String closedVar = forContext.generateUniqueId("itClosed");
		String stepExVar = forContext.generateUniqueId("itStepEx");
		String bodyExVar = forContext.generateUniqueId("itBodyEx");
		String valVar = forContext.generateUniqueId("v");
		if(isAwait) {
			// Mirrors evaluate()'s isAwait branch above - valueIteratorAsync
			// is statically imported the same way valueIterator/await_ etc.
			// already are in every generated unit (see JSTranspiledUnit).
			b.println("Iterator<Object> {0}=valueIteratorAsync({1},{2},{3});", itName, JSTranspiler.MAIN_CONTEXT, JSTranspiler.asValue(forContext, collectionNode), insideAsyncGeneratorBody);
		} else {
			b.println("Iterator<Object> {0}=valueIterator({1});", itName, JSTranspiler.asValue(forContext, collectionNode));
		}
		b.println("boolean {0}=false, {1}=false;", doneVar, closedVar);

		// See ASTFor's identical hoisting logic (and its own doc) for the
		// full rationale: a block-scoped let/const declared in the BODY
		// (not this loop's own head, handled separately by perIteration
		// below) would otherwise get a fresh Object[] allocated by
		// ASTBlock's transpilerDeclareStatement() every iteration even when
		// nothing captures it across iterations. Safe to hoist (declare the
		// array once, here, before the loop) exactly when the whole body
		// contains no closure/eval anywhere.
		if(bodyNode instanceof ASTBlock hoistableBlock && hoistableBlock.hasDeclaredVariables() && !mayCaptureAcrossIterations(bodyNode)) {
			hoistableBlock.transpilerDeclareArrayOnly(forContext, b);
		}

		b.println("try {");
		b.incIndent();

		if(StringUtil.isNotEmpty(getLabel())) {
			b.println("{0}:", getLabel());
		}
		b.println("for(;;) {");
		b.incIndent();
		b.println("Object {0};", valVar);
		b.println("try {");
		b.incIndent();
		b.println("if(!{0}.hasNext()) {", itName);
		b.incIndent();
		b.println("{0}=true;", doneVar);
		b.println("break;");
		b.decIndent();
		b.println("}");
		b.println("{0}={1}.next();", valVar, itName);
		b.decIndent();
		b.println("} catch(Throwable {0}) {", stepExVar);
		b.incIndent();
		b.println("{0}=true;", closedVar);
		b.println("throw {0};", stepExVar);
		b.decIndent();
		b.println("}");

		b.println("try {");
		b.incIndent();

		// A let/const-declared loop variable needs the SAME per-iteration
		// fresh-binding treatment as the interpreter (see
		// needsPerIterationBinding()'s own doc, and ASTFor's identical
		// mechanism for the C-style for-loop, which this mirrors): the
		// binding assignment and the body (and any closure transpiled from
		// within it) reference a fresh copy of the variable array made at
		// the top of each Java loop iteration, instead of the single array
		// this container's own transpilerDeclareStatement() allocated once,
		// above the whole `for(;;)`. Copied back to the outer array
		// afterward (like ASTFor) - NOT because anything outside this loop
		// reads it again for a plain block-bodied loop, but for whatever
		// still bakes in `outerVar` rather than `iterVar`: a BARE-STATEMENT
		// body containing NO closure (wrapBareBodyIfNeeded() only wraps
		// bodies that actually contain one - see its own doc - so this
		// container's own transpilerDeclareStatement() call at the very
		// top, entirely outside this redirect window, is still what such a
		// body's own identifiers resolve through), or `varDecl`/
		// `collectionNode` capturing across iterations on their own
		// (needsPerIterationBinding() can be true for reasons other than
		// the body). Without the copy-back, a closure reading `outerVar`
		// after the loop completes would see it stuck at its initial TDZ
		// sentinel forever (test262 scope-body-lex-boundary.js) instead of
		// merely observing the last iteration's value.
		boolean perIteration = needsPerIterationBinding();
		String outerVar = null;
		String iterVar = null;
		if(perIteration) {
			outerVar = getVariables().getJavaVariable();
			iterVar = forContext.generateUniqueId("p_");
			b.println("final Object[] {0} = java.util.Arrays.copyOf({1}, {2});", iterVar, outerVar, getVariables().size());
			getVariables().setJavaVariable(iterVar);
		}

		// A using/await-using loop-head binding is disposed at the END OF
		// EACH ITERATION (unlike ASTFor's C-style init-clause using, disposed
		// once at loop exit - see its own hasUsingDeclarations() doc) -
		// mirrors evaluate() above, which calls DisposeResourcesUtil.dispose()
		// unconditionally once per iteration (on the exception path AND right
		// after a normal/break/continue/return completion). Reuses
		// ASTBlock.transpileWithDisposal()'s existing shape - a fresh
		// `List<DisposableResource>` declared HERE, textually inside the
		// `for(;;)`, so a new Java ArrayList is created every iteration at
		// runtime - wrapping the assignment (which is what actually calls
		// RuntimeUtil.registerDisposableResource() against this list, see
		// ASTVariableDeclUsing.transpileJavaAssignment()) and the body in a
		// try/finally that disposes on every exit path, matching Java's real
		// break/continue/return control flow (unlike the interpreter's
		// Signal-based one - see transpileWithDisposal's own doc for why a
		// finally is required here instead of just running code after the
		// try). Any resulting disposal exception propagates out to the
		// existing bodyExVar catch below exactly like a body exception would.
		if(hasUsingDeclarations()) {
			ASTBlock.transpileWithDisposal(_jsContext, forContext, b, () -> {
				transpileForAssignmentStatement(forContext,b,varDecl,valVar);

				if(bodyNode instanceof ASTBlock block) {
					block.transpileJavaStatementNoBrace(forContext,b);
				} else {
					b.debugLocation(bodyNode);
					bodyNode.transpileJavaStatement(forContext, b);
				}
			});
		} else {
			transpileForAssignmentStatement(forContext,b,varDecl,valVar);

			if(bodyNode instanceof ASTBlock block) {
				block.transpileJavaStatementNoBrace(forContext,b);
			} else {
				b.debugLocation(bodyNode);
				bodyNode.transpileJavaStatement(forContext, b);
			}
		}

		if(perIteration) {
			getVariables().setJavaVariable(outerVar);
			if(!endsWithUnconditionalLoopExit(bodyNode)) {
				b.println("System.arraycopy({0}, 0, {1}, 0, {2});", iterVar, outerVar, getVariables().size());
			}
		}

		b.decIndent();
		b.println("} catch(Throwable {0}) {", bodyExVar);
		b.incIndent();
		b.println("{0}=true;", closedVar);
		b.println("iteratorCloseQuietly({0});", itName);
		b.println("throw {0};", bodyExVar);
		b.decIndent();
		b.println("}");

		b.decIndent();
		b.println("}");

		b.decIndent();
		b.println("} finally {");
		b.incIndent();
		b.println("if(!{0} && !{1}) {", doneVar, closedVar);
		b.incIndent();
		b.println("iteratorClose({0});", itName);
		b.decIndent();
		b.println("}");
		b.decIndent();
		b.println("}");

		b.decIndent();
		b.println("}");
    }

    
    @Override
	public void decompileStatement(JavaBuilder b) {
    	if(StringUtil.isNotEmpty(getLabel())) {
        	b.append("{0}: ", getLabel()).nl();
    	}
    	b.append("for");
    	if(isAwait) {
    		b.append(" await");
    	}
    	b.append("(");
       	b.append(varDecl.decompileExpression());
    	b.append(" of ");
       	b.append(collectionNode.decompileExpression());
    	b.append(") {\n");
    	b.incIndent();
    	decompileBlockStatements(b,bodyNode);
    	b.decIndent();
    	b.append("}");
	}
 }