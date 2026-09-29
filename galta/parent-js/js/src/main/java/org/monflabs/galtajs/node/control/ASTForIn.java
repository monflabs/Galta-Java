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
import java.util.List;

import org.monflabs.galtajs.JSParseException;
import org.monflabs.galtajs.node.ASTIdentifier;
import org.monflabs.galtajs.node.ASTNode;
import org.monflabs.galtajs.node.ASTVarContainer;
import org.monflabs.galtajs.node.call.ASTCall;
import org.monflabs.galtajs.node.literal.ASTContainerLiteral;
import org.monflabs.galtajs.node.variable.ASTVariableDecl;
import org.monflabs.galtajs.parser.Token;
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
import org.monflabs.util.iterators.Iterators;




/**
 * for in() abstract statement node.
 */
public class ASTForIn extends ASTFor_ {
	
	private ASTNode varDecl;
	private ASTNode collectionNode;
	private ASTNode bodyNode;

	public ASTForIn(Token t, ASTNode varDecl, ASTNode collectionNode, ASTNode bodyNode) {
		super(t);
		this.varDecl = assignParent(varDecl);
		this.collectionNode = assignParent(collectionNode);
		this.bodyNode = assignParent(wrapBareBodyIfNeeded(t, bodyNode));
	}

	// See ASTForOf's identical helper for the full rationale: a
	// bare-statement body has no block container of its own for a closure
	// inside it to hoist to at parse time, so (unwrapped) it hoists to THIS
	// node's own container instead - outside the per-iteration redirect
	// window in transpileJavaStatement() below. Wrapping it in a synthetic
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
			case 0 ->	{ return varDecl; }
			case 1 ->	{ return collectionNode; }
			case 2 ->	{ return bodyNode; }
			default ->	{ return super.getChild(index-3); }
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
		
		// For now - we should find a better way to validate the assigned nodes???
		checkAssignmentTarget(varDecl, AssignmentUse.FOR_IN_OF, initContext.isGenuinelyStrict());
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

	// CreatePerIterationEnvironment: true when this loop's OWN head binding
	// is `let`/`const` - each iteration (and any closure created within it)
	// must observe a FRESH copy, not the one shared/mutated binding a
	// `var`-declared loop variable correctly gets. Mirrors ASTFor/ASTForOf's
	// identically-named/-purposed check.
	private boolean hasPerIterationBindings() {
		if(!hasDeclaredVariables()) {
			return false;
		}
		for(VariableDef v: getVariables()) {
			VAR_TYPE t = v.getVarType();
			if(t==VAR_TYPE.LET || t==VAR_TYPE.CONST) {
				return true;
			}
		}
		return false;
	}

	private Boolean needsPerIterationBinding;

	// A fresh-per-iteration binding is only OBSERVABLE if something could
	// capture it across iterations - see ASTFor's identical check (and its
	// own doc) for the full rationale; duplicated here (and in ASTForOf)
	// rather than shared, to keep this fix self-contained.
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

	@SuppressWarnings("incomplete-switch")
	@Override
	public Signal evaluate(JSInterpretedRuntimeContext context, JSResult result) {
		try {
			// Per spec 13.7.5.12, the collection expression evaluates in a
			// lexical environment that already has this loop's own let/const
			// bound name(s) - in TDZ - so a self-referencing collection
			// must throw, not resolve to an outer same-named binding.
			JSInterpretedRuntimeContext headContext = context;
			if(hasDeclaredVariables()) {
				for(VariableDef v: getVariables()) {
					VAR_TYPE t = v.getVarType();
					if(t==VAR_TYPE.LET || t==VAR_TYPE.CONST) {
						if(headContext==context) {
							headContext = new InterpretedBlockRuntimeContext(context);
						}
						headContext.createVariable(v.getName(), RuntimeUtil.TDZ, t);
					}
				}
			}
			// Annex B.3.4 "for ( var ForBinding = Initializer in Expression )":
			// per spec 13.7.5.12 ForIn/OfHeadEvaluation, when the (var-only)
			// ForBinding carries an Initializer, it's evaluated and assigned to
			// the binding ONCE, here, BEFORE the collection expression - this
			// runs regardless of whether the collection turns out to have any
			// enumerable keys at all (a plain `for (var a in obj)` with no
			// initializer never reaches this branch, since ASTVariableDecl's
			// single Entry then has a null initNode).
			if(varDecl instanceof ASTVariableDecl decl) {
				List<ASTVariableDecl.Entry> declEntries = decl.getEntries();
				if(declEntries.size()==1) {
					ASTNode initNode = declEntries.get(0).getInitNode();
					if(initNode!=null) {
						Object initValue = initNode.evaluateValue(headContext,result);
						decl.evaluateAssign(headContext,initValue,null,result,null);
					}
				}
			}
			Object col = collectionNode.evaluateValue(headContext,result);
			Iterator<String> it = RuntimeUtil.keyIterator(context.getEnvironment(),col);

			// The spec says that we first get the list and then we iterate
			// -> if a property is ADDED while looping, the addition is
			// ignored (EnumerateObjectProperties never has to observe it).
			// A DELETION must still be respected though (13.7.5.13 ForIn/
			// OfBodyEvaluation step 6.d: "If a property that has not yet
			// been visited during the enumeration is deleted, then it will
			// not be visited" combined with 6.g.iii's own re-check) - each
			// collected name is re-verified as a still-enumerable own-or-
			// inherited property (env.isStillEnumerableProperty(), same
			// shadowing-aware walk as RuntimeUtil.keyIterator() above, just
			// applied per-key) right before use, and skipped if it no
			// longer qualifies.
			List<String> names = Iterators.collect(it);

			JSGlobalContext gc = context.getGlobalContext();
			result.setUndefined();
			JSResult tempResult = new JSResult();
			// A fresh context (plus a fresh TDZ-seeded slot) is only
			// observable per-iteration if something could actually capture
			// it across iterations - see needsPerIterationBinding()'s own
			// doc (and ASTForOf's identical fix) for the full rationale.
			// for-in has no using/await-using head binding at all (spec
			// only allows those in for-of), so unlike ASTForOf this needs
			// no extra exclusion beyond needsPerIterationBinding() itself.
			boolean reuseContext = !needsPerIterationBinding();
			JSInterpretedRuntimeContext sharedContext = null;
			if(reuseContext) {
				sharedContext = new InterpretedBlockRuntimeContext(context);
				if(hasDeclaredVariables()) {
					for(VariableDef v: getVariables()) {
						VAR_TYPE t = v.getVarType();
						if(t==VAR_TYPE.LET || t==VAR_TYPE.CONST) {
							sharedContext.createVariable(v.getName(), RuntimeUtil.TDZ, t);
						}
					}
				}
			}
loop:		for(Object value: names) {
				if(!RuntimeUtil.isStillEnumerableProperty(context.getEnvironment(),col,(String)value)) {
					continue;
				}
				JSInterpretedRuntimeContext forContext;
				if(reuseContext) {
					// Re-assigning into the SAME already-initialized slot
					// every iteration, rather than a fresh TDZ one, is safe
					// specifically BECAUSE nothing can observe the
					// difference - see ASTForOf's identical fix for the
					// full rationale (including why this doesn't trip a
					// const-reassignment error).
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
							if(t==VAR_TYPE.LET || t==VAR_TYPE.CONST) {
								forContext.createVariable(v.getName(), RuntimeUtil.TDZ, t);
							}
						}
					}
				}
				Signal s = forContext.with( () -> {
					// A bare destructuring pattern (no var/let/const - e.g. `for ([a,b] in obj)`)
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
				if(s!=Signal.NONE) {
					switch(s.getType()) {
						case RETURN -> {
							return s;
						}
						case CONTINUE -> {
							String l = s.getLabel(); 
			            	if(!StringUtil.isEmpty(l)) {
			            		if(!StringUtil.equals(getLabel(),l)) {
			            			// Continue up the next level
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
			            			return s;
			            		}
			            	}
			                // Break the main loop - result already carries the last
			                // completion value seen from the body (UpdateEmpty semantics).
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

    @Override
	public JSType getReturnedType() {
    	return JSType.UNKNOWN;
	}
    
    @Override
	public void transpileJavaStatement(JSTranspilerGeneratorContext _jsContext, TranspilerJavaBuilder b) {
		JSTranspilerGeneratorContext forContext = new TranspilerGeneratorBlockContext(_jsContext); 

		b.println("{");
		b.incIndent();

		// NOTE: deferring the functions half of transpilerDeclareStatement()
		// into the per-iteration redirect window below (so a closure
		// hoisted to THIS node's own container would also observe
		// `iterVar`) was attempted and reverted - its instantiation site
		// (`new F_xxx(...)`) isn't necessarily textually nested inside that
		// same generated Java block, so moving the CLASS DECLARATION in
		// there broke Java scoping for some call sites ("cannot find
		// symbol: class F_xxx", a genuine compile error). See ASTForOf's
		// identical revert for the full account (5 files broken vs 1 fixed
		// there). A closure that's part of a BARE-STATEMENT body no longer
		// reaches this container's own declare-pass at all in the common
		// case, though - see wrapBareBodyIfNeeded(), called from the
		// constructor: such a body is now wrapped in its own synthetic
		// ASTBlock when it contains a closure, taking the `bodyNode
		// instanceof ASTBlock` branch below instead - the SAME
		// already-correct mechanism a genuine `{ ... }` body uses.
		transpilerDeclareStatement(forContext,b,0);

		// Annex B.3.4 "for ( var ForBinding = Initializer in Expression )":
		// mirrors evaluate()'s own identical block (see its doc comment) -
		// when the (var-only) ForBinding carries an Initializer, it must be
		// evaluated and assigned ONCE, here, BEFORE the collection expression -
		// this transpiled path had no equivalent at all (test262 annexB/
		// language/statements/for-in/nonstrict-initializer.js: the
		// initializer's own side effects/value were silently dropped). A
		// plain `for (var a in obj)` with no initializer never reaches this
		// (initNode is null - ASTVariableDecl's single Entry only carries one
		// when the source actually wrote `= ...`).
		if(varDecl instanceof ASTVariableDecl decl) {
			List<ASTVariableDecl.Entry> declEntries = decl.getEntries();
			if(declEntries.size()==1) {
				ASTNode initNode = declEntries.get(0).getInitNode();
				if(initNode!=null) {
					transpileForAssignmentStatement(forContext,b,varDecl,JSTranspiler.asValue(forContext,initNode));
				}
			}
		}

		// Evaluated once, into a named variable, rather than inlined
		// straight into the keyIterator(...) call below - it's needed
		// again on every iteration for the per-key deletion re-check just
		// below (mirrors the interpreted evaluate()'s own `col` local,
		// which is evaluated once and reused the same way). Declared
		// BEFORE the loop label (rather than between it and the `for`) so
		// the label keeps directly labeling the loop statement itself, as
		// `continue <label>`/`break <label>` require.
		String colVar = forContext.generateUniqueId("_col");
		b.println("Object {0} = {1};", colVar, JSTranspiler.asValue(forContext, collectionNode));

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

		if(StringUtil.isNotEmpty(getLabel())) {
			b.println("{0}:", getLabel());
		}

		String itName = "_it"+_jsContext.getDepth();
		b.println("for( Iterator<?> {0}=keyIterator({1}); {0}.hasNext(); ) {", itName, colVar);
		b.incIndent();

		// EnumerateObjectProperties (13.7.5.13 ForIn/OfBodyEvaluation step
		// 6.d/6.g.iii): keyIterator() above snapshots the enumerable key
		// list once, eagerly, at loop start - a key deleted by the loop's
		// OWN body (e.g. by a nested for-in over the same object, as in
		// test262's for-in/S12.6.4_A7_T2.js) before its own turn comes up
		// must NOT be visited. The interpreted evaluate() already performs
		// this exact per-key re-check (RuntimeUtil.isStillEnumerableProperty)
		// right before each key's use; this mirrors it for the transpiled
		// path, which previously used the raw iterator value unchecked.
		String keyVar = forContext.generateUniqueId("_key");
		b.println("String {0} = (String){1}.next();", keyVar, itName);
		b.println("if(!isStillEnumerableProperty(env,{0},{1})) continue;", colVar, keyVar);

		// A let/const-declared loop variable needs the SAME per-iteration
		// fresh-binding treatment as the interpreter - see
		// needsPerIterationBinding()'s own doc and ASTFor/ASTForOf's
		// identical mechanism, which this mirrors.
		boolean perIteration = needsPerIterationBinding();
		String outerVar = null;
		String iterVar = null;
		if(perIteration) {
			outerVar = getVariables().getJavaVariable();
			iterVar = forContext.generateUniqueId("p_");
			b.println("final Object[] {0} = java.util.Arrays.copyOf({1}, {2});", iterVar, outerVar, getVariables().size());
			getVariables().setJavaVariable(iterVar);
		}

		transpileForAssignmentStatement(forContext,b,varDecl,keyVar);

		if(bodyNode instanceof ASTBlock block) {
			block.transpileJavaStatementNoBrace(forContext,b);
		} else {
			b.debugLocation(bodyNode);
			bodyNode.transpileJavaStatement(forContext, b);
		}

		if(perIteration) {
			getVariables().setJavaVariable(outerVar);
			// Copied back (like ASTFor/ASTForOf) for whatever still bakes
			// in `outerVar` rather than `iterVar` - a bare-statement body
			// with NO closure (wrapBareBodyIfNeeded() only wraps bodies
			// that actually contain one), or varDecl/collectionNode
			// capturing on their own - so it doesn't read a permanently-
			// TDZ'd/stale outer slot when called after the loop completes.
			// See ASTForOf's identical fix for the full rationale.
			if(!endsWithUnconditionalLoopExit(bodyNode)) {
				b.println("System.arraycopy({0}, 0, {1}, 0, {2});", iterVar, outerVar, getVariables().size());
			}
		}

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
    	b.append("for(");
       	b.append(varDecl.decompileExpression());
    	b.append(" in ");
       	b.append(collectionNode.decompileExpression());
    	b.append(") {\n");
    	b.incIndent();
    	decompileBlockStatements(b,bodyNode);
    	b.decIndent();
    	b.append("}");
	}
}
