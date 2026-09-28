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

import org.monflabs.galtajs.JSParseException;
import org.monflabs.galtajs.node.ASTIdentifier;
import org.monflabs.galtajs.node.ASTNode;
import org.monflabs.galtajs.parser.Token;
import org.monflabs.galtajs.rt.JSResult;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.interpreter.InterpretedUnitRuntimeContext.Signal;
import org.monflabs.galtajs.rt.interpreter.InterpretedWithRuntimeContext;
import org.monflabs.galtajs.rt.interpreter.JSInterpretedRuntimeContext;
import org.monflabs.galtajs.transpiler.JSTranspiler;
import org.monflabs.galtajs.transpiler.TranspilerJavaBuilder;
import org.monflabs.galtajs.transpiler.context.JSTranspilerGeneratorContext;
import org.monflabs.galtajs.transpiler.context.TranspilerGeneratorWithContext;
import org.monflabs.galtajs.types.JSType;
import org.monflabs.galtajs.util.JavaBuilder;




/**
 * with() statement node.
 */
public class ASTWith extends ASTNode implements IWithContextNode {

	private ASTNode withNode;
	private ASTNode bodyNode;
 
	public ASTWith(Token t, ASTNode testNode, ASTNode bodyNode) {
		super(t);
		this.withNode = assignParent(testNode);
		this.bodyNode = assignParent(wrapBareBodyIfNeeded(t, bodyNode));
	}

	// Same fix, same root cause, as ASTForOf.wrapBareBodyIfNeeded() (see its
	// own doc) - duplicated rather than shared, matching that precedent's own
	// choice. A bare (non-block) `with` body has no ASTVarContainer of its
	// own, so a closure nested in it (ASTFunction.init()'s
	// findParentNodeByClassUnchecked(IContextBlockContainer.class) walk)
	// registers with whatever REAL container encloses the `with` statement
	// itself (the nearest function/program) instead - that outer container
	// declares ALL its own nested function classes once, at the very top of
	// its generated Java method, textually BEFORE this `with`'s own
	// generated `final Object with_N = ...;` local even exists, so the
	// closure's class body can never reference with_N (KnownGaps.md "a
	// closure created via a with-target's own comma-sequence side effects
	// doesn't capture the with-object"). Wrapping the body in a synthetic
	// ASTBlock gives it its own container: that block's own function-class
	// declarations are emitted inside its own generated `{ ... }`, which
	// ASTWith.transpileJavaStatement() below only opens AFTER with_N is
	// already declared - ordinary Java local-class capture then makes
	// with_N visible, so this needs no runtime-context redesign, only the
	// same container-boundary fix for-of already proved out. A closure in
	// the with-TARGET expression itself (evaluated in oldEnv, before with_N
	// is even assigned) is untouched - it correctly must NOT see the
	// with-object, and this only ever wraps bodyNode.
	private static ASTNode wrapBareBodyIfNeeded(Token t, ASTNode bodyNode) {
		if(bodyNode!=null && !(bodyNode instanceof ASTBlock) && mayContainClosure(bodyNode)) {
			return new ASTBlock(t, java.util.Collections.singletonList(bodyNode));
		}
		return bodyNode;
	}

	// Duplicated from ASTForOf.mayContainClosure() (see its own doc) rather
	// than shared - same precedent, same reasoning: this runs at
	// CONSTRUCTION time, before this node has a parent or any of the other
	// state a shared instance-method version would need.
	private static boolean mayContainClosure(ASTNode node) {
		if(node==null) {
			return false;
		}
		if(node instanceof ASTFunction) {
			return true;
		}
		if(node instanceof org.monflabs.galtajs.node.call.ASTCall call && !call.isNullOp()) {
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

	public ASTNode getBodyNode() {
		return bodyNode;
	}

	@Override
	public int getChildCount() {
		return super.getChildCount()+2;
	}
	@Override
	public ASTNode getChild(int index) {
		switch(index) {
			case 0 ->	{ return withNode; }
			case 1 ->	{ return bodyNode; }
			default ->	{ return super.getChild(index-2); }
		}
	}
	@Override
	protected void _setChild(int index, ASTNode node) {
		switch(index) {
			case 0 ->	{ this.withNode = node; }
			case 1 ->	{ this.bodyNode  = node; }
			default ->  { super._setChild(index-2,node); }
		}
	}
	
	@Override
	protected void init(InitContext initContext) {
		if(initContext.isStrictMode()) {
			throw new JSParseException(null,this,"With is not available in strict mode");
		}
		// A `with(...)` body could resolve `arguments` off the with-object; more
		// importantly, it could shadow it dynamically. Force the enclosing
		// non-arrow function to materialize its own arguments object so a
		// with-body's lookup can find it via the normal scope chain.
		for(ASTNode n=getParent(); n!=null; n=n.getParent()) {
			if(n instanceof ASTFunction f && !f.isArrow()) {
				f.setUseArguments(true);
				break;
			}
		}
		super.init(initContext);
	}

	@Override	
	public Signal evaluate(JSInterpretedRuntimeContext context, JSResult result) {
		try {
			// Evaluate the with-target into its own JSResult (matching ASTIf's
			// test expression and ASTWhile's condition) so it can't pollute the
			// completion value, then reset `result` to undefined as the
			// baseline (matching ASTWhile) before the body runs - if the body
			// completes abruptly with no value of its own (e.g. a bare `break`
			// out of an enclosing loop), that baseline is what survives as
			// this with-statement's own completion, per spec's UpdateEmpty.
			JSResult withResult = new JSResult();
			Object _with = withNode.evaluateValue(context,withResult);
			if(RuntimeUtil.isNullOrUndefined(_with)) {
				throw RuntimeUtil.typeError("with() argument is null or undefined");
			}
			result.setUndefined();
			InterpretedWithRuntimeContext withContext = new InterpretedWithRuntimeContext(context,_with);
			return withContext.with( () -> {
				return bodyNode.evaluate(withContext,result);
			});
		} catch(Exception ex) {
			throw fillInStackTrace(ex);
		}
	}

    @Override
	public JSType getReturnedType() {
    	return JSType.UNKNOWN;
	}
    
    @Override
	public void transpileJavaStatement(JSTranspilerGeneratorContext jsContext, TranspilerJavaBuilder b) {
    	TranspilerGeneratorWithContext withContext = new TranspilerGeneratorWithContext(jsContext);
		b.println("{");
		b.incIndent();
    	b.println("final Object {0} = {1};", withContext.getWithJavaName(), JSTranspiler.asValue(jsContext,withNode) );
		bodyNode.transpileJavaStatement(withContext, b);
		b.decIndent();
		b.println("}");
    }

    
    @Override
	public void decompileStatement(JavaBuilder b) {
    	b.append("with(");
       	b.append(withNode.decompileExpression());
    	b.append(") {\n");
    	
    	b.incIndent();
    	decompileBlockStatements(b, bodyNode );
    	b.decIndent();

    	b.append("}");
	}
}