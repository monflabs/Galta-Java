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
package org.monflabs.json.jsonpath;

/**
 * Base class for an expression node.
 * 
 * @author priand
 */
public final class ExprUnaryOp extends ExprNode {
	
	public enum OP {
		NOT,
	};
	
	private OP op;
	private ExprNode node;
	
	public ExprUnaryOp(OP op, ExprNode node) {
		this.op = op;
		this.node = node;
	}
	
	@Override
	public String toString() {
		StringBuilder b = new StringBuilder();
		switch(op) {
			case NOT ->	b.append("!");
		}
		addNode(b,node);
		return b.toString();
	}
	private static void addNode(StringBuilder b, ExprNode n) {
		if(n instanceof ExprLiteral) {
			b.append(n.toString());
		} else if(n instanceof ExprPathOp || n instanceof ExprFunction) {
			b.append(n.toString());
		} else {
			b.append("(");
			b.append(n.toString());
			b.append(")");
		}
	}
	
	@Override
	public boolean execute(Object root, Object current) {
		switch(op) {
			case NOT: {
				return node!=null ? !node.execute(root, current) : true;
			}
		}
		throw new IllegalStateException();
	}
}
