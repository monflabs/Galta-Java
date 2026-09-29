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

import org.monflabs.galtajs.node.ASTIdentifier;
import org.monflabs.galtajs.node.ASTNode;
import org.monflabs.galtajs.node.ASTVarContainer;
import org.monflabs.galtajs.node.literal.ASTContainerLiteral;
import org.monflabs.galtajs.node.literal.ASTArrayLiteral;
import org.monflabs.galtajs.node.literal.ASTLiteral;
import org.monflabs.galtajs.node.literal.ASTObjectLiteral;
import org.monflabs.galtajs.node.literal.DefaultRef;
import org.monflabs.galtajs.parser.Token;
import org.monflabs.galtajs.rt.JSResult;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.interpreter.InterpretedUnitRuntimeContext.Signal;
import org.monflabs.galtajs.rt.interpreter.JSInterpretedRuntimeContext;
import org.monflabs.galtajs.rt.interpreter.JSInterpretedRuntimeContext.VAR_TYPE;
import org.monflabs.galtajs.transpiler.JSTranspiler;
import org.monflabs.galtajs.transpiler.TranspilerJavaBuilder;
import org.monflabs.galtajs.transpiler.context.JSTranspilerGeneratorContext;
import org.monflabs.galtajs.transpiler.util.TranspilerUtil;
import org.monflabs.galtajs.types.JSType;
import org.monflabs.util.StringFormat;




/**
 * catch() statement node.
 * Make ASTCatch a block itself...
 */
public class ASTCatch extends ASTVarContainer {

	private ASTNode bindingNode;
	private ASTBlock bodyNode;

	public ASTCatch(Token t, ASTNode bindingNode, ASTBlock bodyNode) {
		super(t);
		this.bindingNode = assignParent(bindingNode);
		this.bodyNode = assignParent(bodyNode);
	}

	// Kept for the simple, common case (transpiler/decompile); returns null
	// when the catch parameter is a destructuring pattern rather than a
	// plain identifier.
	public String getIdentifier() {
		return bindingNode instanceof ASTIdentifier id ? id.getId() : null;
	}

	public ASTNode getBindingNode() {
		return bindingNode;
	}

	public ASTBlock getBodyNode() {
		return bodyNode;
	}

	// Binds the exception value to this catch clause's parameter, whether a
	// plain identifier or a destructuring (array/object) pattern. Every
	// binding created here is marked markCatchParameter() (see
	// VarAccessor.isCatchParameter()'s own doc) - the Annex B.3.5 collision
	// carve-out is about the Catch clause's own Environment Record as a
	// whole, not any one specific name, so a destructuring pattern's OTHER
	// bound names qualify too (test262 only exercises the plain-identifier
	// case, but there's no spec basis to treat them differently).
	public void bindException(JSInterpretedRuntimeContext catchContext, Object exceptionValue, JSResult result) {
		if(bindingNode instanceof ASTIdentifier id) {
			catchContext.createVariable(id.getId(), exceptionValue, VAR_TYPE.LET).markCatchParameter();
		} else if(bindingNode instanceof ASTContainerLiteral lit) {
			lit.assign(catchContext, (k,v) -> catchContext.createVariable(k, v, VAR_TYPE.LET).markCatchParameter(), exceptionValue, result, false);
		} else {
			throw RuntimeUtil.illegalState();
		}
	}

	@Override
	public int getChildCount() {
		return super.getChildCount()+2;
	}
	@Override
	public ASTNode getChild(int index) {
		switch(index) {
			case 0 ->		{ return bindingNode; }
			case 1 ->		{ return bodyNode; }
			default ->		{ return super.getChild(index-2); }
		}
	}
	@Override
	protected void _setChild(int index, ASTNode node) {
		switch(index) {
			case 0 ->	{ this.bindingNode = node; }
			case 1 ->	{ this.bodyNode = (ASTBlock)node; }
			default ->  { super._setChild(index-2,node); }
		}
	}

	@Override
	protected void init(InitContext initContext) {
		if(bindingNode instanceof ASTIdentifier id) {
			bodyNode.addVarDeclaration(id.getId(),VAR_TYPE.PREDECLARED,null);
			checkStrictBindingName(initContext, id.getId(), this);
		} else if(bindingNode instanceof ASTContainerLiteral lit) {
			lit.checkPattern(true, initContext.isGenuinelyStrict());
			// Declared on `this` (ASTCatch's own container), NOT bodyNode -
			// see KnownGaps.md "a closure in a destructured catch parameter's
			// default value doesn't capture the catch binding". `this` is
			// the nearest IContextBlockContainer for a closure nested inside
			// the pattern itself (e.g. a default-value initializer like
			// `catch ([x, _ = function(){return x;}])` - see
			// transpileJavaStatement()'s own comment) - findParentNodeByClass
			// walks ANCESTORS only, and bodyNode is a SIBLING of bindingNode
			// under `this`, never an ancestor of anything inside bindingNode.
			// Declaring the pattern's own bound names directly on bodyNode
			// (the original approach) left such a closure's free-variable
			// lookup unable to ever reach them - it walked straight past
			// ASTCatch's always-empty container to whatever unrelated
			// same-named binding happened to exist further out. Declaring
			// them on `this` instead also matches the actual spec shape (the
			// CatchParameter's own lexical environment is the OUTER one,
			// wrapping the Block's own inner lexical environment) - a
			// reference from within bodyNode's own statements still resolves
			// correctly, since `this` remains an ancestor of everything
			// inside bodyNode too (ASTNode.findVariable/ASTIdentifier's own
			// ancestor walk both keep working unchanged).
			lit.declareVariables(this, VAR_TYPE.PREDECLARED);
			lit.forEachVarName((name) -> {
				checkStrictBindingName(initContext, name, this);
				// See ASTVarContainer.VariableDef.annexBHoistBlocked's field
				// comment - a DESTRUCTURING catch parameter, unlike a plain
				// identifier one, always blocks Annex B's function-hoist.
				// forEachVarName can visit a name that declareVariables()
				// didn't actually register as its own binding (e.g. an
				// elision/hole in a nested array pattern) - guard against that.
				ASTVarContainer.VariableDef vd = getOwnVariable(name);
				if(vd!=null) {
					vd.setAnnexBHoistBlocked(true);
				}
			});
		}
    	super.init(initContext);
	}


	@Override
	public Signal evaluate(JSInterpretedRuntimeContext context, JSResult result) {
		try {
			// A Catch clause's own completion is just its Block's completion
			// (no forcing to undefined here) - result already carries
			// whatever the block produced (or, for an empty block, its own
			// empty-completion default).
			return bodyNode.evaluate(context,result);
		} catch(Throwable ex) {
			throw fillInStackTrace(ex);
		}
	}

    @Override
	public JSType getReturnedType() {
    	return JSType.UNKNOWN;
	}

    @Override
	public void transpileJavaStatement(JSTranspilerGeneratorContext jsContext, TranspilerJavaBuilder b) {
		if(getBodyNode()!=null) {
			// A destructured catch parameter's own bound names are declared
			// directly on `this` (ASTCatch's own container - see init()),
			// not on getBodyNode(). `this` is also the nearest
			// IContextBlockContainer for any closure nested inside the
			// catch parameter's own binding pattern (e.g. a default-value
			// initializer like `catch ([x = function(){...}()])`), which
			// registers itself here via addFunctionDeclaration(). This one
			// transpilerDeclareStatement() call therefore does BOTH jobs
			// together, in the order that matters: it allocates `this`'s own
			// variable array and registers every one of its VariableDefs
			// into jsContext FIRST (ASTVarContainer.transpilerDeclareStatement's
			// variables-then-functions ordering), THEN emits any pattern-
			// nested closures' Java classes - so a closure like
			// `function(){return x;}` in `catch ([x, _ = function(){return
			// x;}])` resolves its free variable `x` to this container's own
			// (already-registered) slot instead of walking past it to an
			// unrelated outer same-named binding. Without this call
			// altogether, those closures' Java classes are never emitted at
			// all, leaving a "cannot find symbol" reference to them below
			// (test262 language/statements/try/dstr/*.js and neighbors).
			transpilerDeclareStatement(jsContext, b, 0);
			String exceptionVarName = ((ASTTry)getParent()).getExceptionVarName();
			// Simplify this by making ASTCatch a ASTBlock itself!
			getBodyNode().transpileJavaStatementNoBrace(jsContext, b, bb -> {
				if(bindingNode instanceof ASTIdentifier id) {
					// Plain-identifier case: still declared on getBodyNode()
					// (see init()) - unchanged (a plain identifier binding
					// has no default value, so no nested-closure risk).
					VariableDef v = getBodyNode().findVariable(id.getId());
					jsContext.createVariable(v);
					bb.println("{0} = JSRuntimeException.exceptionObject({1});", v.getJavaVariableValue(), exceptionVarName);
				} else if(bindingNode instanceof ASTObjectLiteral lit) {
					// Mirrors ASTVariableDecl.transpileJavaStatement()'s identical
					// ASTObjectLiteral branch - see its own comments for the
					// destructParameters()/VarAccessor.destruct() codegen shape.
					// The only difference: the value being destructured is the
					// caught exception object (already computed into
					// exceptionVarName by ASTTry), not a separately-transpiled
					// initializer expression.
					String tmpVarName = jsContext.generateUniqueId("tmp");
					bb.println("Object {0} = JSRuntimeException.exceptionObject({1});", tmpVarName, exceptionVarName);
					bb.println("requireObjectCoercible({0});", tmpVarName);
					lit.destructParameters( (container,defaultValue,path,spread) -> {
						String varName = ((ASTIdentifier)container).getId();
						// Declared on `this` (ASTCatch), not getBodyNode() -
						// see init()'s own comment.
						VariableDef variable = findVariable(varName);
						jsContext.createVariable(variable);
						bb.print("{0} = VarAccessor.destruct({1},{2}", variable.getJavaVariableValue(), JSTranspiler.MAIN_ENVIRONMENT, tmpVarName);
						if(path.length==0) {
							bb.append(",null");
						} else if(path.length==1) {
							bb.append(StringFormat.format(",{0}", ASTLiteral.encodeLiteral(jsContext,path[0])));
						} else {
							bb.print(",new Object[]{");
							for(int j=0; j<path.length; j++) {
								if(j>0) {
									bb.print(",");
								}
								bb.print("{0}", JSTranspiler.asValue(jsContext,path[j]));
							}
							bb.print("}");
						}
						if(spread!=null) {
							bb.print(",{0}", TranspilerUtil.encodeSpreadValue(jsContext,spread));
						} else {
							bb.print(",null");
						}
						if(defaultValue!=null) {
							if(path.length>1) {
								bb.print(",{0}", DefaultRef.transpileChain(jsContext,defaultValue));
							} else {
								bb.print(",()->{0}", JSTranspiler.asValue(jsContext,defaultValue.node()));
							}
						}
						bb.println(");");
					}, RuntimeUtil.EMPTY_PARAMS, jsContext, bb, null);
				} else if(bindingNode instanceof ASTArrayLiteral arr) {
					// Mirrors ASTVariableDecl.transpileJavaStatement()'s identical
					// ASTArrayLiteral branch - see its own comments.
					String tmpVarName = jsContext.generateUniqueId("tmp");
					bb.println("Object {0} = JSRuntimeException.exceptionObject({1});", tmpVarName, exceptionVarName);
					arr.transpileJavaIteratorDestructure(jsContext, bb, tmpVarName, /*isAssignmentContext*/ false, (target,valueExpr) -> {
						String varName = ((ASTIdentifier)target).getId();
						// Declared on `this` (ASTCatch), not getBodyNode() -
						// see init()'s own comment.
						VariableDef variable = findVariable(varName);
						jsContext.createVariable(variable);
						bb.println("{0} = {1};", variable.getJavaVariableValue(), valueExpr);
					});
				}
			} );
		}
    }
}
