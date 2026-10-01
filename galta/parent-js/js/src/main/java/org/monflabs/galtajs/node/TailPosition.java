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

import org.monflabs.galtajs.node.binaryop.ASTLogicalOp;
import org.monflabs.galtajs.node.control.ASTBlock;
import org.monflabs.galtajs.node.control.ASTCase;
import org.monflabs.galtajs.node.control.ASTCatch;
import org.monflabs.galtajs.node.control.ASTDoWhile;
import org.monflabs.galtajs.node.control.ASTFor;
import org.monflabs.galtajs.node.control.ASTFunction;
import org.monflabs.galtajs.node.control.ASTIf;
import org.monflabs.galtajs.node.control.ASTReturn;
import org.monflabs.galtajs.node.control.ASTSwitch;
import org.monflabs.galtajs.node.control.ASTSwitchCaseBlock;
import org.monflabs.galtajs.node.control.ASTTry;
import org.monflabs.galtajs.node.control.ASTWhile;
import org.monflabs.galtajs.node.ternaryop.ASTTernaryTest;
import org.monflabs.galtajs.node.unaryop.ASTParen;
import org.monflabs.galtajs.node.variable.ASTVariableDecl;
import org.monflabs.galtajs.rt.interpreter.JSInterpretedRuntimeContext.VAR_TYPE;

/**
 * Static semantics IsInTailPosition (ECMA-262 15.10.1): whether a call is
 * the last thing its function does, so that it can be performed after the
 * function's own frame is gone (see TailCall). Only calls in the body of a
 * strict, non-async, non-generator function qualify, reached from the
 * function body through statements and expressions that return their
 * operand's value as is. Deliberately conservative: a return inside a try
 * block, a for-in/for-of loop or a block with using declarations (which run
 * code after the call) and class constructors are never tail positions here.
 */
public final class TailPosition {

	private TailPosition() {
	}

	// strictEnvironment: the environment runs every script as strict code
	// (JSConfiguration's strictMode extension)
	public static boolean isTailCall(ASTNode call, boolean strictEnvironment) {
		// Expression: up to the return statement
		ASTNode child = call;
		INode p = call.getParent();
		while(!(p instanceof ASTReturn)) {
			if(p instanceof ASTParen
					|| (p instanceof ASTTernaryTest t && child!=t.getOp1Node())
					|| (p instanceof ASTLogicalOp op && child==op.getRightNode())
					|| (p instanceof ASTExpression e && e.getChildCount()>0 && child==e.getChild(e.getChildCount()-1))
					|| isTransparent(p)) {
				child = (ASTNode)p;
				p = p.getParent();
				continue;
			}
			return false;
		}
		// Statements: up to the function body
		child = (ASTNode)p;
		p = p.getParent();
		while(p!=null) {
			if(p instanceof ASTFunction fn) {
				return (fn.isGenuinelyStrictMode() || strictEnvironment) && !fn.isAsync() && !fn.isGenerator() && !fn.isClassConstructor();
			}
			if(p instanceof ASTTry t) {
				// The catch block without a finally, or the finally block
				if(!(child==t.getFinallyNode() || (child==t.getCatchNode() && t.getFinallyNode()==null))) {
					return false;
				}
			} else if(p instanceof ASTBlock b) {
				if(hasUsingDeclaration(b)) {
					return false;
				}
			} else if(!(p instanceof ASTCatch || p instanceof ASTIf || p instanceof ASTWhile || p instanceof ASTDoWhile
					|| p instanceof ASTFor || p instanceof ASTSwitch || p instanceof ASTSwitchCaseBlock || p instanceof ASTCase
					|| p instanceof ASTStatementList || isTransparent(p))) {
				return false;
			}
			child = p instanceof ASTNode n ? n : null;
			p = p.getParent();
		}
		return false;
	}

	private static boolean isTransparent(INode node) {
		return node instanceof ASTNode n && ASTNode.skipTransparent(n)!=n;
	}

	private static boolean hasUsingDeclaration(ASTBlock block) {
		ASTNode[] statements = block.getStatements();
		if(statements!=null) {
			for(ASTNode s: statements) {
				if(ASTNode.skipTransparent(s) instanceof ASTVariableDecl d && d.getVarType()==VAR_TYPE.USING) {
					return true;
				}
			}
		}
		return false;
	}
}
