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

import org.monflabs.galtajs.node.control.StatementList;
import org.monflabs.galtajs.parser.Token;


/**
 * Base class for main statement container (program, function, ...)
 */
public abstract class ASTStatementList extends ASTVarContainer implements StatementList {

	private ASTNode[] statements;
	private int hoistedCount;

	public ASTStatementList(Token t) {
		super(t);
		this.statements = EMPTY_NODES;
	}

	// `export default function fn(){}` (and generator/async forms) is,
	// per spec, a genuine HoistableDeclaration - same early-evaluation
	// treatment as an ordinary top-level `function` decl - but it parses
	// as an `ASTExport` node WRAPPING an `ASTFunctionDecl` as its
	// `defaultExport` child, not as a HoistableNode itself (unlike a bare
	// `function fn(){}`). `export default class C{}`/`export default
	// <expr>` are NOT spec-hoisted, so this deliberately checks the
	// WRAPPED node's own named-function-declaration status (via
	// ASTExport.isHoistableDefaultExport()) rather than treating every
	// ASTExport as hoistable.
	private static boolean isHoistable(ASTNode actualNode) {
		if(actualNode instanceof HoistableNode) {
			return true;
		}
		// Non-default `export function fn(){}`/generator form - see
		// ASTExport.isHoistableNamedExport()'s own doc comment; same
		// wrapped-node reasoning as isHoistableDefaultExport() just below.
		return actualNode instanceof org.monflabs.galtajs.node.control.ASTExport exp
				&& (exp.isHoistableDefaultExport() || exp.isHoistableNamedExport());
	}

	protected ASTNode[] hoistNodes(ASTNode[] nodes) {
		ASTNode[] hoistedNodes = null;
		hoistedCount = 0;
		int len = nodes.length;
		for(int i=0;i<len; i++) {
			ASTNode actualNode = skipTransparent(nodes[i]);
			if(isHoistable(actualNode)) {
				if(hoistedNodes==null) {
					hoistedNodes = new ASTNode[len];
				}
				hoistedNodes[hoistedCount++] = nodes[i];
			}
		}
		if(hoistedNodes==null) {
			return nodes;
		}
		int idx = hoistedCount;
		for(int i=0;i<len; i++) {
			ASTNode actualNode = skipTransparent(nodes[i]);
			if(!isHoistable(actualNode)) {
				hoistedNodes[idx++] = nodes[i];
			}
		}
		return hoistedNodes;
	}

	@Override
	protected void init(InitContext initContext) {
		statements = hoistNodes(statements);
		super.init(initContext);
	}

	@Override
	public int getChildCount() {
		return super.getChildCount()+statements.length;
	}
	@Override
	public ASTNode getChild(int index) {
		if(index<statements.length) {
			return statements[index];
		}
		return super.getChild(index-statements.length);
	}
	@Override
	protected void _setChild(int index, ASTNode node) {
		if(index<statements.length) {
			statements[index] = node;
			return;
		}
		super._setChild(index-statements.length, node);
	}
	@Override
    public ASTNode[] getStatements() {
    	return statements;
    }
	@Override
    public void setStatements(ASTNode[] statements) {
		this.statements = assignParent(statements);
    }
}
