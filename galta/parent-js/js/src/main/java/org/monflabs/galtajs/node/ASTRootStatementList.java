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

import java.util.List;

import org.monflabs.galtajs.node.control.IContextRootContainer;
import org.monflabs.galtajs.node.literal.ASTLiteral;
import org.monflabs.galtajs.parser.Token;


/**
 * Base class for main statement container (program, function, ...)
 */
public abstract class ASTRootStatementList extends ASTStatementList implements IContextRootContainer {

	private boolean forceStrictMode;
	
	public ASTRootStatementList(Token t, List<ASTNode> statements) {
		super(t);
		if(statements!=null) {
			// Scan the whole Directive Prologue (the leading run of plain
			// string-literal expression statements), not just the first
			// statement - "use strict" activates strict mode wherever it
			// appears in that run, not only when it comes first.
			int i = 0;
			while(i<statements.size()) {
				// In debug mode every statement is wrapped in a (transparent) debug hook
				ASTNode n = skipTransparent(statements.get(i));
				if(n instanceof ASTLiteral lit && lit.getValue() instanceof String) {
					if(lit.isLiteralDirectiveText("use strict")) {
						statements.remove(i);
						forceStrictMode = true;
					} else {
						i++;
					}
				} else {
					break;
				}
			}
			setStatements(statements.toArray(new ASTNode[statements.size()]));
		}
	}
	
	public final boolean isForceStrictMode() {
		return forceStrictMode;
	}
}