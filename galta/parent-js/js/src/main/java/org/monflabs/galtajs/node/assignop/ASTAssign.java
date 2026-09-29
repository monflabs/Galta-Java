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
package org.monflabs.galtajs.node.assignop;

import org.monflabs.galtajs.JSParseException;
import org.monflabs.galtajs.node.ASTArrayMember;
import org.monflabs.galtajs.node.ASTIdentifier;
import org.monflabs.galtajs.node.ASTMember;
import org.monflabs.galtajs.node.ASTNode;
import org.monflabs.galtajs.node.NoopNode;
import org.monflabs.galtajs.node.call.ASTBaseCall;
import org.monflabs.galtajs.node.clazz.ASTClassDecl;
import org.monflabs.galtajs.node.control.ASTFunction;
import org.monflabs.galtajs.node.control.IContextBlockContainer;
import org.monflabs.galtajs.node.control.IVarDeclarator;
import org.monflabs.galtajs.node.literal.ASTContainerLiteral;
import org.monflabs.galtajs.parser.Token;
import org.monflabs.galtajs.rt.JSResult;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.interpreter.InterpretedUnitRuntimeContext.Signal;
import org.monflabs.galtajs.rt.interpreter.JSInterpretedRuntimeContext;
import org.monflabs.galtajs.rt.interpreter.JSInterpretedRuntimeContext.VAR_TYPE;
import org.monflabs.galtajs.rt.transpiler.VarAccessor;
import org.monflabs.galtajs.transpiler.context.JSTranspilerGeneratorContext;
import org.monflabs.galtajs.types.JSType;
import org.monflabs.util.StringUtil;


/**
 * Assignment + node.
 */
public class ASTAssign extends ASTAbstractAssign implements IVarDeclarator {

	// IsIdentifierRef of LeftHandSideExpression (spec 13.15.2 step 1c) is a
	// SYNTACTIC check on the actual grammar production used - a parenthesized
	// identifier ("(fn) = function(){}") parses through
	// CoverParenthesizedExpressionAndArrowParameterList, NOT the
	// IdentifierReference production directly, so it does NOT qualify for
	// NamedEvaluation even though assignment-TARGET resolution itself
	// transparently treats "(fn)" the same as "fn" (hence the constructor
	// below still unwraps leftNode for THAT purpose). Captured here, before
	// unwrapping, specifically for the naming check in init().
	private final boolean leftWasParenthesized;

	public boolean isLeftParenthesized() {
		return leftWasParenthesized;
	}

	public ASTAssign(Token t, ASTNode leftNode, ASTNode rightNode) {
		super(t,skipTransparent(leftNode),rightNode);
		this.leftWasParenthesized = leftNode instanceof NoopNode;
	}

	@Override
	public void init(InitContext initContext) {
		// If the right node is a function, then set a name
		// If the function or class being defined is anonymous, but it is being assigned to a variable or object property, then its .name is inferred from that context.
		// IsAnonymousFunctionDefinition/HasName propagate transparently through
		// a ParenthesizedExpression per spec (only a multi-expression comma
		// sequence disqualifies it, not parens alone) - skipTransparent()
		// unwraps ASTParen (and any other NoopNode wrapper) so "x = (function(){})"
		// is named just like the unparenthesized "x = function(){}" already was.
		ASTNode rightNode = skipTransparent(this.rightNode);
		if(rightNode instanceof ASTFunction fd && StringUtil.isEmpty(fd.getFunctionName())) {
			ASTNode leftNode = getLeftNode();
			// !leftWasParenthesized: see this field's own doc comment -
			// "(fn) = function(){}" must NOT name the function "fn" (test262
			// language/expressions/assignment/fn-name-lhs-cover.js).
			if(!leftWasParenthesized && leftNode instanceof ASTIdentifier id) {
				fd.setFunctionName(id.getId());
			// Below is not part of the spec and lead to issue when calling an external function with the same name
			//} else if(leftNode instanceof ASTMember mb) {
			//	fd.setFunctionName(mb.getMemberName());
			}
		} else if(rightNode instanceof ASTClassDecl fd && StringUtil.isEmpty(fd.getClassName())) {
			ASTNode leftNode = getLeftNode();
			if(!leftWasParenthesized && leftNode instanceof ASTIdentifier id) {
				fd.setClassName(id.getId());
			} else if(!leftWasParenthesized && leftNode instanceof ASTMember mb) {
				fd.setClassName(mb.getMemberName());
			}
		}

		super.init(initContext);
	}

	@Override
    public void declareVariables(IContextBlockContainer varContainer, VAR_TYPE varType) {
		ASTNode varNode = getLeftNode();
		if(varNode instanceof ASTIdentifier id) {
			String varName = id.getId();
			varContainer.addVarDeclaration(varName, varType, null);
		} else {
			throw new JSParseException(null,this,"Illegal State exception");
		}
	}

	@Override
	public Signal evaluate(JSInterpretedRuntimeContext context, JSResult result) {
		try {
			ASTNode leftNode = getLeftNode();
			if(leftNode instanceof ASTArrayMember arrayMember && arrayMember.canResolveReference()) {
				// ECMAScript 13.15.2: resolve LHS reference before evaluating RHS
				ASTArrayMember.ResolvedReference ref = arrayMember.resolveReference(context, result);
				Object rightValue = getRightNode().evaluateValue(context, result);
				if(ref!=null) {
					arrayMember.assignToResolved(context, ref, rightValue, null, result, null);
				}
			} else if(leftNode instanceof ASTBaseCall) {
				// Annex B.1.2: the CallExpression LHS must be evaluated (for its
				// side effects) and rejected as a runtime ReferenceError BEFORE
				// the RHS is ever evaluated - test262 asserts the right-hand
				// side's call is never made (see ASTBaseCall.evaluateAssign()).
				leftNode.evaluateAssign(context, null, null, result, null);
			} else if(leftNode instanceof ASTIdentifier ident && ident.getScopeHops()<0) {
				// ECMAScript 13.15.2 step 1a: resolve the identifier reference
				// BEFORE the RHS is evaluated (test262 S11.13.1_A5/A6) - see
				// ASTIdentifier.evaluateAssign()'s preResolved-parameter comment
				// for the full rationale. Only attempted when scopeHops<0 (the
				// optimizer hasn't proven this identifier's scope is free of
				// with/eval); a non-null find here is a real, already-existing
				// binding, so it's safe to lock in early. A null result here
				// (not yet resolvable) is passed through unchanged, falling back
				// to ASTIdentifier's original post-RHS resolution/auto-create
				// logic exactly as before - no behavior change for that case.
				VarAccessor preResolved = context.getVariableEntry(ident.getId());
				// Strict mode: a reference that is unresolvable before the RHS
				// runs stays unresolvable, so PutValue throws even when the RHS
				// itself creates the global (test262 language/identifier-
				// resolution/assign-to-global-undefined.js).
				boolean unresolvable = preResolved==null && context.isStrictMode() && !ident.isResolvable(context);
				Object rightValue = getRightNode().evaluateValue(context, result);
				if(unresolvable) {
					throw RuntimeUtil.referenceError("{0} is not defined", ident.getId());
				}
				ident.evaluateAssign(context, rightValue, null, result, null, preResolved);
			} else {
				Object rightValue = getRightNode().evaluateValue(context,result);
				if(leftNode instanceof ASTContainerLiteral lit) {
					lit.assign(
						context,
						(k,v) -> {
							RuntimeUtil.assignIdentifierOrCreateGlobal(context, k, v);
						},
						rightValue, result, true);
					// A destructuring assignment expression evaluates to the
					// right-hand side value, not whatever lit.assign() last left in
					// `result` internally (e.g. from evaluating a default value).
					result.setValue(rightValue);
				} else {
					leftNode.evaluateAssign(context, rightValue, null, result, null);
				}
			}
			return Signal.NONE;
		} catch(Throwable ex) {
			throw fillInStackTrace(ex);
		}
	}

    @Override
	public JSType getReturnedType() {
    	return rightNode.getReturnedType();
	}

    @Override
	public String transpileJavaExpression(JSTranspilerGeneratorContext jsContext) {
    	String rightValue = getRightNode().transpileJavaExpression(jsContext);
    	return getLeftNode().transpileJavaAssignment(jsContext, ASSIGN_TYPE.EQUALS, rightValue, getRightNode().isSequence(), false);
    }
    
    @Override
	protected String decompileOperator() {
    	return " = ";
    }
}